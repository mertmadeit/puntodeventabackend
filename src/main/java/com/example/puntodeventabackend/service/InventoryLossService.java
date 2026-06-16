package com.example.puntodeventabackend.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
public class InventoryLossService {

    private final JdbcTemplate jdbcTemplate;

    public InventoryLossService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> getMermas() {
        return jdbcTemplate.query(
                """
                SELECT m.id, m.cantidad, m.motivo, m.fecha_hora, p.nombre as producto, u.nombre_completo as usuario
                FROM mermas m
                JOIN productos p ON p.id = m.producto_id
                JOIN usuarios u ON u.id = m.usuario_id
                ORDER BY m.fecha_hora DESC
                """,
                (rs, rowNum) -> Map.of(
                        "id", rs.getLong("id"),
                        "cantidad", rs.getInt("cantidad"),
                        "motivo", rs.getString("motivo"),
                        "fecha_hora", ApiSupport.toUtcIsoString(rs.getObject("fecha_hora")),
                        "producto", rs.getString("producto"),
                        "usuario", rs.getString("usuario")
                )
        );
    }

    @Transactional
    public Map<String, Object> createMerma(Map<String, Object> payload) {
        List<Map<String, Object>> items = ApiSupport.castListOfMap(payload.get("items"));
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items requeridos");
        }

        String motivo = ApiSupport.optionalString(payload.getOrDefault("motivo", "Caducidad"));
        Long userId = getDefaultUserId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int inserted = 0;

        for (Map<String, Object> item : items) {
            Long productId = ApiSupport.requireLong(item.get("productId"), "productId requerido", "productId invalido");
            Integer quantity = ApiSupport.requirePositiveInteger(item.get("quantity"), "Cantidad invalida");
            ensureProductExists(productId);

            // La base de datos descuenta el stock mediante el trigger TRG_Mermas_AfterInsert.
            jdbcTemplate.update(
                    "INSERT INTO mermas (producto_id, cantidad, motivo, fecha_hora, usuario_id) VALUES (?, ?, ?, ?, ?)",
                    productId, quantity, motivo.isBlank() ? "Caducidad" : motivo, Timestamp.valueOf(now), userId
            );
            inserted++;
        }

        return Map.of("message", "Mermas registradas con exito", "count", inserted);
    }

    private Long getDefaultUserId() {
        List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM usuarios ORDER BY id LIMIT 1", Long.class);
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay usuarios");
        }
        return ids.get(0);
    }

    private void ensureProductExists(Long productId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM productos WHERE id = ?", Integer.class, productId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Producto no encontrado");
        }
    }
}
