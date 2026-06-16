package com.example.puntodeventabackend.service;

import com.example.puntodeventabackend.repository.JdbcSupportRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utilidades compartidas para los servicios del POS.
 *
 * Aqui viven validaciones, conversiones de tipos y el helper que inserta
 * registros de auditoria sin repetir el mismo codigo en cada servicio.
 */
final class ApiSupport {

    private ApiSupport() {
    }

    // Ejecuta un INSERT y regresa la llave generada por la base de datos.
    static Long insertAndReturnId(JdbcSupportRepository repository, String sql, JdbcSupportRepository.StatementBinder binder) {
        try {
            return repository.insertAndReturnId(sql, binder);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo obtener el id generado");
        }
    }

    // Normaliza fechas de la base a ISO-8601 en UTC para la API.
    static String toUtcIsoString(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toInstant(ZoneOffset.UTC).toString();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toString();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().toString();
        }
        return String.valueOf(value);
    }

    // Convierte payloads tipo lista-JSON a List<Map<String, Object>>.
    static List<Map<String, Object>> castListOfMap(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) map;
                out.add(casted);
            }
        }
        return out;
    }

    // Convierte valores numericos a BigDecimal con 2 decimales.
    static BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd.setScale(2, RoundingMode.HALF_UP);
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        String raw = String.valueOf(value).trim();
        if (raw.isBlank()) return null;
        try {
            return new BigDecimal(raw).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "monto invalido");
        }
    }

    // Valida montos mayores a cero.
    static BigDecimal requirePositiveAmount(Object value, String message) {
        BigDecimal amount = toBigDecimal(value);
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return amount;
    }

    // Valida montos cero o mayores, por ejemplo para cortes.
    static BigDecimal requireNonNegativeAmount(Object value, String message) {
        BigDecimal amount = toBigDecimal(value);
        if (amount == null || amount.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return amount;
    }

    // Evita null en operaciones monetarias.
    static BigDecimal nvlMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    // Lee un Long desde texto o numero, con mensaje claro si falla.
    static Long requireLong(Object value, String blankMessage, String invalidMessage) {
        String raw = String.valueOf(value == null ? "" : value).trim();
        if (raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, blankMessage);
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalidMessage);
        }
    }

    // Valida enteros positivos para cantidades.
    static Integer requirePositiveInteger(Object value, String message) {
        try {
            Integer number = Integer.valueOf(String.valueOf(value));
            if (number <= 0) {
                throw new NumberFormatException();
            }
            return number;
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    // Normaliza strings opcionales para evitar nulls y espacios sobrantes.
    static String optionalString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    // Acepta booleanos, numeros o texto para campos flexibles del frontend.
    static boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    // Registra auditoria usando el usuario que viene en el contexto de Spring Security.
    static void recordAudit(JdbcTemplate jdbcTemplate, String evento, String detalle) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication == null ? "" : String.valueOf(authentication.getName()).trim();
        Long userId = null;
        String userName = "Sistema";

        if (!username.isBlank()) {
            List<Map<String, Object>> users = jdbcTemplate.queryForList(
                    "SELECT id, nombre_completo FROM usuarios WHERE username = ? LIMIT 1",
                    username
            );
            if (!users.isEmpty()) {
                Map<String, Object> user = users.get(0);
                userId = ((Number) user.get("id")).longValue();
                userName = String.valueOf(user.get("nombre_completo"));
            }
        }

        recordAudit(jdbcTemplate, userId, userName, evento, detalle);
    }

    // Inserta el registro final en auditoria_registros.
    static void recordAudit(JdbcTemplate jdbcTemplate, Long userId, String userName, String evento, String detalle) {
        jdbcTemplate.update(
                "INSERT INTO auditoria_registros(fecha_hora, usuario_id, usuario_nombre, evento, detalle) VALUES(?,?,?,?,?)",
                Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)),
                userId,
                userName == null || userName.isBlank() ? "Sistema" : userName.trim(),
                evento,
                detalle
        );
    }
}
