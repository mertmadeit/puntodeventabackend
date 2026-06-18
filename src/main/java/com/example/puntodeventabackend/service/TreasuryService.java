package com.example.puntodeventabackend.service;

import com.example.puntodeventabackend.repository.JdbcSupportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
/**
 * Consolida la operacion de tesoreria: resumen, movimientos, turnos y cortes.
 *
 * Aqui conviven consultas resumidas y operaciones que dependen de un turno abierto.
 */
public class TreasuryService {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcSupportRepository jdbcSupportRepository;

    public TreasuryService(JdbcTemplate jdbcTemplate, JdbcSupportRepository jdbcSupportRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbcSupportRepository = jdbcSupportRepository;
    }

    // Devuelve el estado general de caja y ventas por metodo de pago.
    public Map<String, Object> getResumen() {
        BigDecimal fondoCaja = ApiSupport.nvlMoney(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(monto_inicial),0) FROM caja_turnos WHERE estado='abierto'",
                BigDecimal.class
        ));
        BigDecimal ventasEfectivo = ApiSupport.nvlMoney(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total),0) FROM ventas WHERE estado='Pagado' AND metodo_pago='Efectivo'",
                BigDecimal.class
        ));
        BigDecimal ventasTarjeta = ApiSupport.nvlMoney(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total),0) FROM ventas WHERE estado='Pagado' AND metodo_pago='Tarjeta'",
                BigDecimal.class
        ));
        BigDecimal transferencias = ApiSupport.nvlMoney(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(total),0) FROM ventas WHERE estado='Pagado' AND metodo_pago='Transferencia'",
                BigDecimal.class
        ));
        return Map.of(
                "fondoCaja", fondoCaja,
                "ventasEfectivo", ventasEfectivo,
                "ventasTarjeta", ventasTarjeta,
                "transferencias", transferencias
        );
    }

    // Trae los movimientos de caja en orden descendente.
    public List<Map<String, Object>> getMovimientos() {
        return jdbcTemplate.query(
                """
                SELECT id, fecha_hora, tipo, categoria, concepto, proveedor_nombre, monto
                FROM caja_movimientos
                ORDER BY fecha_hora DESC
                """,
                (rs, rowNum) -> Map.of(
                        "id", rs.getLong("id"),
                        "timestamp", ApiSupport.toUtcIsoString(rs.getObject("fecha_hora")),
                        "tipo", rs.getString("tipo"),
                        "categoria", rs.getString("categoria"),
                        "concepto", rs.getString("concepto"),
                        "proveedorNombre", Objects.toString(rs.getString("proveedor_nombre"), ""),
                        "monto", rs.getBigDecimal("monto")
                )
        );
    }

    // Registra una entrada o retiro de caja asociado al turno abierto.
    @Transactional
    public Map<String, Object> createMovimiento(Map<String, Object> payload) {
        Long turnoId = getOpenTurnoId();
        String tipo = normalizeMovimientoTipo(payload.getOrDefault("tipo", "entrada"));
        String categoria = String.valueOf(payload.getOrDefault("categoria", "operativo")).trim();
        String concepto = String.valueOf(payload.getOrDefault("concepto", "Sin concepto")).trim();
        String proveedorNombre = String.valueOf(payload.getOrDefault("proveedorNombre", "")).trim();
        BigDecimal monto = ApiSupport.requirePositiveAmount(payload.getOrDefault("monto", 0), "monto debe ser mayor a cero");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        Long id = ApiSupport.insertAndReturnId(
                jdbcSupportRepository,
                """
                INSERT INTO caja_movimientos(turno_id, fecha_hora, tipo, categoria, concepto, proveedor_nombre, monto)
                VALUES(?,?,?,?,?,?,?)
                """,
                statement -> {
                    statement.setLong(1, turnoId);
                    statement.setTimestamp(2, Timestamp.valueOf(now));
                    statement.setString(3, tipo);
                    statement.setString(4, categoria.isBlank() ? "operativo" : categoria);
                    statement.setString(5, concepto.isBlank() ? "Sin concepto" : concepto);
                    statement.setString(6, proveedorNombre.isBlank() ? null : proveedorNombre);
                    statement.setBigDecimal(7, monto);
                }
        );
        ApiSupport.recordAudit(
                jdbcTemplate,
                "EDICION",
                "Movimiento de caja " + tipo + " por " + monto + " con concepto " + concepto + "."
        );
        return Map.of(
                "id", id,
                "timestamp", now.toInstant(ZoneOffset.UTC).toString(),
                "tipo", tipo,
                "categoria", categoria.isBlank() ? "operativo" : categoria,
                "concepto", concepto.isBlank() ? "Sin concepto" : concepto,
                "proveedorNombre", proveedorNombre,
                "monto", monto
        );
    }

    // Lista cortes ya calculados con datos del turno original.
    public List<Map<String, Object>> getCortes() {
        return jdbcTemplate.query(
                """
                SELECT c.id, c.fecha_hora, c.turno_id, u.nombre_completo, t.hora_apertura, t.monto_inicial,
                       c.esperado, c.contado, c.diferencia
                FROM caja_cortes c
                JOIN caja_turnos t ON t.id = c.turno_id
                JOIN usuarios u ON u.id = t.usuario_id
                ORDER BY c.fecha_hora DESC
                """,
                (rs, rowNum) -> Map.of(
                        "id", rs.getLong("id"),
                        "timestamp", ApiSupport.toUtcIsoString(rs.getObject("fecha_hora")),
                        "turnoId", String.valueOf(rs.getLong("turno_id")),
                        "cajero", rs.getString("nombre_completo"),
                        "horaApertura", ApiSupport.toUtcIsoString(rs.getObject("hora_apertura")),
                        "montoInicial", rs.getBigDecimal("monto_inicial"),
                        "esperado", rs.getBigDecimal("esperado"),
                        "contado", rs.getBigDecimal("contado"),
                        "diferencia", rs.getBigDecimal("diferencia")
                )
        );
    }

    // Genera el corte usando el SP de la base y registra auditoria.
    @Transactional
    public Map<String, Object> createCorte(Map<String, Object> payload) {
        Long turnoId = ApiSupport.requireLong(payload.get("turnoId"), "turnoId requerido", "turnoId invalido");
        BigDecimal contado = ApiSupport.requireNonNegativeAmount(payload.getOrDefault("contado", 0), "monto invalido");
        List<Map<String, Object>> turnos = jdbcTemplate.queryForList(
                """
                SELECT t.hora_apertura, t.monto_inicial, u.nombre_completo
                FROM caja_turnos t
                JOIN usuarios u ON u.id = t.usuario_id
                WHERE t.id = ? AND t.estado = 'abierto'
                """,
                turnoId
        );
        if (turnos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Turno abierto no encontrado");
        }

        List<Map<String, Object>> spResult = jdbcTemplate.queryForList("CALL SP_CorteCaja(?)", turnoId);
        if (spResult.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo calcular el corte de caja.");
        }

        BigDecimal esperado = ApiSupport.toBigDecimal(spResult.get(0).get("total_esperado_caja"));
        BigDecimal diferencia = contado.subtract(esperado).setScale(2, RoundingMode.HALF_UP);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Map<String, Object> turno = turnos.get(0);

        Long id = ApiSupport.insertAndReturnId(
                jdbcSupportRepository,
                "INSERT INTO caja_cortes(turno_id, fecha_hora, esperado, contado, diferencia) VALUES(?,?,?,?,?)",
                statement -> {
                    statement.setLong(1, turnoId);
                    statement.setTimestamp(2, Timestamp.valueOf(now));
                    statement.setBigDecimal(3, esperado);
                    statement.setBigDecimal(4, contado);
                    statement.setBigDecimal(5, diferencia);
                }
        );

        jdbcTemplate.update(
                "UPDATE caja_turnos SET hora_cierre = ?, estado = 'cerrado' WHERE id = ?",
                Timestamp.valueOf(now),
                turnoId
        );

        ApiSupport.recordAudit(
                jdbcTemplate,
                "EDICION",
                "Corte de caja generado para el turno " + turnoId + " con contado " + contado + " y diferencia " + diferencia + "."
        );

        return Map.of(
                "id", id,
                "timestamp", now.toInstant(ZoneOffset.UTC).toString(),
                "turnoId", String.valueOf(turnoId),
                "cajero", turno.get("nombre_completo"),
                "horaApertura", ApiSupport.toUtcIsoString(turno.get("hora_apertura")),
                "montoInicial", turno.get("monto_inicial"),
                "esperado", esperado,
                "contado", contado,
                "diferencia", diferencia
        );
    }

    // Abre un turno real para enlazar las ventas de caja con el corte posterior.
    @Transactional
    public Map<String, Object> createTurno(Map<String, Object> payload, Authentication authentication) {
        Long userId = getAuthenticatedUserId(authentication);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }

        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                """
                SELECT t.id, t.hora_apertura, t.monto_inicial, u.nombre_completo
                FROM caja_turnos t
                JOIN usuarios u ON u.id = t.usuario_id
                WHERE t.usuario_id = ? AND t.estado = 'abierto'
                ORDER BY t.hora_apertura DESC
                LIMIT 1
                """,
                userId
        );
        if (!existing.isEmpty()) {
            Map<String, Object> turno = existing.get(0);
            return Map.of(
                    "id", String.valueOf(turno.get("id")),
                    "cajero", turno.get("nombre_completo"),
                    "horaApertura", ApiSupport.toUtcIsoString(turno.get("hora_apertura")),
                    "montoInicial", turno.get("monto_inicial"),
                    "ventasEfectivo", BigDecimal.ZERO,
                    "movimientosNeto", BigDecimal.ZERO
            );
        }

        BigDecimal montoInicial = ApiSupport.requireNonNegativeAmount(
                payload.getOrDefault("montoInicial", 0),
                "monto inicial invalido"
        );
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String turnoCodigo = "turno-" + userId + "-" + System.currentTimeMillis();

        Long id = ApiSupport.insertAndReturnId(
                jdbcSupportRepository,
                "INSERT INTO caja_turnos(turno_codigo, usuario_id, hora_apertura, monto_inicial, estado) VALUES(?,?,?,?, 'abierto')",
                statement -> {
                    statement.setString(1, turnoCodigo);
                    statement.setLong(2, userId);
                    statement.setTimestamp(3, Timestamp.valueOf(now));
                    statement.setBigDecimal(4, montoInicial);
                }
        );

        Map<String, Object> user = jdbcTemplate.queryForMap(
                "SELECT nombre_completo FROM usuarios WHERE id = ?",
                userId
        );

        ApiSupport.recordAudit(
                jdbcTemplate,
                userId,
                String.valueOf(user.get("nombre_completo")),
                "EDICION",
                "Apertura de caja con monto inicial " + montoInicial + "."
        );

        return Map.of(
                "id", String.valueOf(id),
                "cajero", user.get("nombre_completo"),
                "horaApertura", now.toInstant(ZoneOffset.UTC).toString(),
                "montoInicial", montoInicial,
                "ventasEfectivo", BigDecimal.ZERO,
                "movimientosNeto", BigDecimal.ZERO
        );
    }

    // Lista turnos abiertos con ventas y movimientos acumulados.
    public List<Map<String, Object>> getTurnos() {
        return jdbcTemplate.query(
                """
                SELECT t.id, u.nombre_completo, t.hora_apertura, t.monto_inicial,
                       COALESCE((
                         SELECT SUM(v.total) FROM ventas v
                         WHERE v.usuario_id=t.usuario_id AND v.estado='Pagado' AND v.metodo_pago='Efectivo'
                           AND v.fecha_hora >= t.hora_apertura
                           AND (t.hora_cierre IS NULL OR v.fecha_hora <= t.hora_cierre)
                       ),0) ventas_efectivo,
                       COALESCE((
                         SELECT SUM(CASE WHEN m.tipo='entrada' THEN m.monto ELSE -m.monto END)
                         FROM caja_movimientos m
                         WHERE m.turno_id=t.id
                       ),0) movimientos_neto
                FROM caja_turnos t
                JOIN usuarios u ON u.id=t.usuario_id
                WHERE t.estado = 'abierto'
                ORDER BY t.hora_apertura DESC
                """,
                (rs, rowNum) -> Map.of(
                        "id", String.valueOf(rs.getLong("id")),
                        "cajero", rs.getString("nombre_completo"),
                        "horaApertura", ApiSupport.toUtcIsoString(rs.getObject("hora_apertura")),
                        "montoInicial", rs.getBigDecimal("monto_inicial"),
                        "ventasEfectivo", rs.getBigDecimal("ventas_efectivo"),
                        "movimientosNeto", rs.getBigDecimal("movimientos_neto")
                )
        );
    }

    // Busca el unico turno abierto disponible para la caja.
    private Long getOpenTurnoId() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM caja_turnos WHERE estado='abierto' ORDER BY hora_apertura LIMIT 1",
                Long.class
        );
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay turno abierto");
        }
        return ids.get(0);
    }

    private Long getAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) return null;
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM usuarios WHERE username = ? LIMIT 1",
                Long.class,
                authentication.getName()
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    // Normaliza el tipo de movimiento a los valores aceptados por la base.
    private static String normalizeMovimientoTipo(Object value) {
        String tipo = String.valueOf(value).trim().toLowerCase();
        return switch (tipo) {
            case "entrada", "retiro" -> tipo;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tipo invalido");
        };
    }
}
