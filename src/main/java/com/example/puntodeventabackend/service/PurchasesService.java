package com.example.puntodeventabackend.service;

import com.example.puntodeventabackend.repository.JdbcSupportRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PurchasesService {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcSupportRepository jdbcSupportRepository;

    public PurchasesService(JdbcTemplate jdbcTemplate, JdbcSupportRepository jdbcSupportRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbcSupportRepository = jdbcSupportRepository;
    }

    public List<Map<String, Object>> getProveedores() {
        return jdbcTemplate.query(
                "SELECT id, nombre, contacto, telefono, rfc, activo FROM proveedores ORDER BY nombre",
                (rs, rowNum) -> Map.of(
                        "id", rs.getLong("id"),
                        "nombre", rs.getString("nombre"),
                        "contacto", ApiSupport.optionalString(rs.getString("contacto")),
                        "telefono", ApiSupport.optionalString(rs.getString("telefono")),
                        "rfc", ApiSupport.optionalString(rs.getString("rfc")),
                        "activo", rs.getBoolean("activo")
                )
        );
    }

    public Map<String, Object> createProveedor(Map<String, Object> payload) {
        String nombre = ApiSupport.optionalString(payload.get("nombre"));
        if (nombre.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nombre requerido");
        }
        String contacto = ApiSupport.optionalString(payload.get("contacto"));
        String telefono = ApiSupport.optionalString(payload.get("telefono"));
        String rfc = ApiSupport.optionalString(payload.get("rfc"));

        Long id = ApiSupport.insertAndReturnId(
                jdbcSupportRepository,
                "INSERT INTO proveedores (nombre, contacto, telefono, rfc) VALUES (?, ?, ?, ?)",
                statement -> {
                    statement.setString(1, nombre);
                    statement.setString(2, contacto);
                    statement.setString(3, telefono);
                    statement.setString(4, rfc);
                }
        );
        return Map.of("id", id, "nombre", nombre, "contacto", contacto, "telefono", telefono, "rfc", rfc, "activo", true);
    }

    public Map<String, Object> updateProveedor(Long id, Map<String, Object> payload) {
        List<Map<String, Object>> current = jdbcTemplate.queryForList("SELECT * FROM proveedores WHERE id = ?", id);
        if (current.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proveedor no encontrado");
        }

        Map<String, Object> curr = current.get(0);
        String nombre = payload.containsKey("nombre") ? ApiSupport.optionalString(payload.get("nombre")) : String.valueOf(curr.get("nombre"));
        if (nombre.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nombre requerido");
        }
        String contacto = payload.containsKey("contacto") ? ApiSupport.optionalString(payload.get("contacto")) : ApiSupport.optionalString(curr.get("contacto"));
        String telefono = payload.containsKey("telefono") ? ApiSupport.optionalString(payload.get("telefono")) : ApiSupport.optionalString(curr.get("telefono"));
        String rfc = payload.containsKey("rfc") ? ApiSupport.optionalString(payload.get("rfc")) : ApiSupport.optionalString(curr.get("rfc"));
        Boolean activo = payload.containsKey("activo") ? ApiSupport.toBoolean(payload.get("activo")) : ApiSupport.toBoolean(curr.get("activo"));

        jdbcTemplate.update(
                "UPDATE proveedores SET nombre=?, contacto=?, telefono=?, rfc=?, activo=? WHERE id=?",
                nombre, contacto, telefono, rfc, activo, id
        );
        return Map.of("id", id, "nombre", nombre, "contacto", contacto, "telefono", telefono, "rfc", rfc, "activo", activo);
    }

    public void deleteProveedor(Long id) {
        try {
            int affected = jdbcTemplate.update("DELETE FROM proveedores WHERE id = ?", id);
            if (affected == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Proveedor no encontrado");
            }
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No se puede eliminar el proveedor porque tiene historial (compras). Considera desactivarlo en su lugar."
            );
        }
    }

    public List<Map<String, Object>> getCompras() {
        return jdbcTemplate.query(
                """
                SELECT c.id, c.fecha_hora, p.nombre as proveedor, u.nombre_completo as usuario, c.total, c.estado
                FROM compras c
                JOIN proveedores p ON p.id = c.proveedor_id
                JOIN usuarios u ON u.id = c.usuario_id
                ORDER BY c.fecha_hora DESC
                """,
                (rs, rowNum) -> Map.of(
                        "id", rs.getLong("id"),
                        "fecha_hora", ApiSupport.toUtcIsoString(rs.getObject("fecha_hora")),
                        "proveedor", rs.getString("proveedor"),
                        "usuario", rs.getString("usuario"),
                        "total", rs.getBigDecimal("total"),
                        "estado", rs.getString("estado")
                )
        );
    }

    @Transactional
    public Map<String, Object> createCompra(Map<String, Object> payload) {
        Long proveedorId = ApiSupport.requireLong(payload.get("proveedorId"), "proveedorId requerido", "proveedorId invalido");
        ensureProveedorExists(proveedorId);

        List<Map<String, Object>> items = ApiSupport.castListOfMap(payload.get("items"));
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items requeridos");
        }

        Long userId = getDefaultUserId();
        BigDecimal total = BigDecimal.ZERO;
        List<Map<String, Object>> resolvedItems = new ArrayList<>();

        for (Map<String, Object> item : items) {
            Long productId = ApiSupport.requireLong(item.get("productId"), "productId requerido", "productId invalido");
            Integer quantity = ApiSupport.requirePositiveInteger(item.get("quantity"), "Cantidad invalida");
            BigDecimal unitCost = ApiSupport.requirePositiveAmount(item.get("costo"), "Costo invalido");
            String productName = getProductName(productId);

            BigDecimal subtotal = unitCost.multiply(BigDecimal.valueOf(quantity));
            total = total.add(subtotal);
            resolvedItems.add(Map.of(
                    "productId", productId,
                    "name", productName,
                    "quantity", quantity,
                    "costo", unitCost,
                    "subtotal", subtotal
            ));
        }

        BigDecimal finalTotal = total;
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Long compraId = ApiSupport.insertAndReturnId(
                jdbcSupportRepository,
                "INSERT INTO compras (proveedor_id, usuario_id, fecha_hora, total, estado) VALUES (?, ?, ?, ?, 'Completado')",
                statement -> {
                    statement.setLong(1, proveedorId);
                    statement.setLong(2, userId);
                    statement.setTimestamp(3, Timestamp.valueOf(now));
                    statement.setBigDecimal(4, finalTotal);
                }
        );

        // El detalle queda en la misma transaccion que el encabezado para evitar compras incompletas.
        for (Map<String, Object> item : resolvedItems) {
            jdbcTemplate.update(
                    """
                    INSERT INTO compra_detalles (compra_id, producto_id, producto_nombre, cantidad, costo_unitario, subtotal)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    compraId, item.get("productId"), item.get("name"), item.get("quantity"), item.get("costo"), item.get("subtotal")
            );
        }

        return Map.of("id", compraId, "total", finalTotal, "estado", "Completado");
    }

    private void ensureProveedorExists(Long proveedorId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM proveedores WHERE id = ?", Integer.class, proveedorId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proveedor no encontrado");
        }
    }

    private Long getDefaultUserId() {
        List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM usuarios ORDER BY id LIMIT 1", Long.class);
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No hay usuarios");
        }
        return ids.get(0);
    }

    private String getProductName(Long productId) {
        List<String> names = jdbcTemplate.queryForList("SELECT nombre FROM productos WHERE id = ?", String.class, productId);
        if (names.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Producto no encontrado");
        }
        return names.get(0);
    }
}
