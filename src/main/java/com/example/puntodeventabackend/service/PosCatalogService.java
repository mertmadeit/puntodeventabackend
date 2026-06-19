package com.example.puntodeventabackend.service;

import com.example.puntodeventabackend.repository.JdbcSupportRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Types;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
/**
 * Maneja el catalogo base del POS: usuarios, categorias y productos.
 *
 * Este servicio absorbe el CRUD que no pertenece al flujo de ventas
 * para que `PosApiService` quede mas enfocado en operaciones de negocio.
 */
public class PosCatalogService {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcSupportRepository jdbcSupportRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public PosCatalogService(JdbcTemplate jdbcTemplate, JdbcSupportRepository jdbcSupportRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbcSupportRepository = jdbcSupportRepository;
    }

    // Usuarios
    public List<Map<String, Object>> getUsers() {
        return jdbcTemplate.query(
                """
                SELECT id, username, email, role, nombre_completo, estado, image_url
                FROM usuarios
                ORDER BY id
                """,
                (rs, rowNum) -> Map.of(
                        "id", rs.getLong("id"),
                        "name", rs.getString("nombre_completo"),
                        "email", formatUserEmail(rs.getString("email"), rs.getString("username")),
                        "role", rs.getString("role"),
                        "status", formatUserStatus(rs.getString("estado")),
                        "imageUrl", Objects.toString(rs.getString("image_url"), "")
                )
        );
    }

    // Alta de usuario con password inicial y auditoria.
    public Map<String, Object> createUser(Map<String, Object> payload) {
        String name = payloadString(payload, "name");
        String email = payloadString(payload, "email");
        String username = usernameFromEmail(email);
        if (name.isBlank() || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name y email requeridos");
        }
        String password = payloadString(payload, "password");
        if (password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password requerido");
        }
        if (password.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password debe tener al menos 6 caracteres");
        }

        String role = normalizeUserRole(payloadStringOrDefault(payload, "role", "vendedor"));
        String status = normalizeUserStatus(payloadStringOrDefault(payload, "status", "activo"));
        String imageUrl = payloadString(payload, "imageUrl");

        Long id;
        try {
            id = ApiSupport.insertAndReturnId(
                    jdbcSupportRepository,
                    """
                    INSERT INTO usuarios(username, email, password, role, nombre_completo, estado, image_url)
                    VALUES(?,?,?,?,?,?,?)
                    """,
                    statement -> {
                        statement.setString(1, username);
                        statement.setString(2, email);
                        statement.setString(3, passwordEncoder.encode(password));
                        statement.setString(4, role);
                        statement.setString(5, name);
                        statement.setString(6, status);
                        statement.setString(7, imageUrl);
                    }
            );
        } catch (DataIntegrityViolationException ex) {
            throw userConflict();
        }

        ApiSupport.recordAudit(
                jdbcTemplate,
                "EDICION",
                "Creacion de usuario " + name + " (" + email + ")."
        );

        return getUserById(id);
    }

    // Actualiza datos de usuario y anota solo los campos que cambiaron.
    public Map<String, Object> updateUser(Long id, Map<String, Object> payload) {
        List<Map<String, Object>> current = jdbcTemplate.queryForList(
                "SELECT id, username, email, role, nombre_completo, estado, image_url FROM usuarios WHERE id = ?",
                id
        );
        if (current.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }

        Map<String, Object> curr = current.get(0);
        String name = payloadString(payload, "name");
        String email = payloadString(payload, "email");
        String username = usernameFromEmail(email);
        if (name.isBlank() || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name y email requeridos");
        }

        String role = normalizeUserRole(payloadStringOrDefault(payload, "role", "vendedor"));
        String status = normalizeUserStatus(payloadStringOrDefault(payload, "status", "activo"));
        String imageUrl = payloadString(payload, "imageUrl");
        String password = payloadString(payload, "password");
        boolean updatePassword = !password.isBlank();
        if (updatePassword && password.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password debe tener al menos 6 caracteres");
        }

        int affected;
        try {
            if (updatePassword) {
                affected = jdbcTemplate.update(
                        """
                        UPDATE usuarios
                        SET username = ?, email = ?, password = ?, role = ?, nombre_completo = ?, estado = ?, image_url = ?
                        WHERE id = ?
                        """,
                        username, email, passwordEncoder.encode(password), role, name, status, imageUrl, id
                );
            } else {
                affected = jdbcTemplate.update(
                        """
                        UPDATE usuarios
                        SET username = ?, email = ?, role = ?, nombre_completo = ?, estado = ?, image_url = ?
                        WHERE id = ?
                        """,
                        username, email, role, name, status, imageUrl, id
                );
            }
        } catch (DataIntegrityViolationException ex) {
            throw userConflict();
        }
        if (affected == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }

        StringBuilder changes = new StringBuilder();
        if (!Objects.equals(String.valueOf(curr.get("nombre_completo")), name)) {
            changes.append("nombre, ");
        }
        if (!Objects.equals(formatUserEmail(Objects.toString(curr.get("email"), ""), String.valueOf(curr.get("username"))), email)) {
            changes.append("correo, ");
        }
        if (!Objects.equals(String.valueOf(curr.get("role")), role)) {
            changes.append("rol, ");
        }
        if (!Objects.equals(formatUserStatus(String.valueOf(curr.get("estado"))), "inactivo".equals(status) ? "Inactivo" : "Activo")) {
            changes.append("estado, ");
        }
        if (!Objects.equals(Objects.toString(curr.get("image_url"), ""), imageUrl)) {
            changes.append("imagen, ");
        }
        if (updatePassword) {
            changes.append("contrasena, ");
        }

        if (!changes.isEmpty()) {
            String detail = "Edicion de usuario " + name + ". Cambios: " + changes.substring(0, changes.length() - 2) + ".";
            ApiSupport.recordAudit(jdbcTemplate, "EDICION", detail);
        }

        return getUserById(id);
    }

    // Elimina el usuario si no existe una restriccion de base.
    public void deleteUser(Long id) {
        List<Map<String, Object>> current = jdbcTemplate.queryForList(
                "SELECT nombre_completo, username, email FROM usuarios WHERE id = ?",
                id
        );
        if (current.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }

        int affected = jdbcTemplate.update("DELETE FROM usuarios WHERE id = ?", id);
        if (affected == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }

        Map<String, Object> curr = current.get(0);
        ApiSupport.recordAudit(
                jdbcTemplate,
                "EDICION",
                "Eliminacion de usuario " + curr.get("nombre_completo") + " (" + formatUserEmail(Objects.toString(curr.get("email"), ""), String.valueOf(curr.get("username"))) + ")."
        );
    }

    // Categorias
    public List<Map<String, Object>> getCategories() {
        return jdbcTemplate.query(
                "SELECT id, nombre, slug FROM categorias_producto ORDER BY id",
                (rs, rowNum) -> Map.of(
                        "id", rs.getLong("id"),
                        "name", rs.getString("nombre"),
                        "slug", rs.getString("slug")
                )
        );
    }

    // Crea categoria y genera slug si no viene uno.
    public Map<String, Object> createCategory(Map<String, Object> payload) {
        String name = String.valueOf(payload.getOrDefault("name", "")).trim();
        if (name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name requerido");
        }
        String slug = String.valueOf(payload.getOrDefault("slug", slugify(name))).trim();
        Long id = ApiSupport.insertAndReturnId(
                jdbcSupportRepository,
                "INSERT INTO categorias_producto(nombre, slug) VALUES(?,?)",
                statement -> {
                    statement.setString(1, name);
                    statement.setString(2, slug);
                }
        );
        ApiSupport.recordAudit(jdbcTemplate, "EDICION", "Creacion de categoria " + name + ".");
        return Map.of("id", id, "name", name, "slug", slug);
    }

    // Edita categoria y registra auditoria solo si hubo cambios.
    public Map<String, Object> updateCategory(Long id, Map<String, Object> payload) {
        List<Map<String, Object>> current = jdbcTemplate.queryForList(
                "SELECT id, nombre, slug FROM categorias_producto WHERE id = ?",
                id
        );
        if (current.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria no encontrada");
        }
        String name = payload.containsKey("name") ? String.valueOf(payload.get("name")) : String.valueOf(current.get(0).get("nombre"));
        String slug = payload.containsKey("slug") ? String.valueOf(payload.get("slug")) : String.valueOf(current.get(0).get("slug"));
        jdbcTemplate.update("UPDATE categorias_producto SET nombre = ?, slug = ? WHERE id = ?", name, slug, id);
        if (!Objects.equals(String.valueOf(current.get(0).get("nombre")), name) || !Objects.equals(String.valueOf(current.get(0).get("slug")), slug)) {
            ApiSupport.recordAudit(jdbcTemplate, "EDICION", "Edicion de categoria " + name + ".");
        }
        return Map.of("id", id, "name", name, "slug", slug);
    }

    // Borra categoria; la base puede rechazarla si tiene productos asociados.
    public void deleteCategory(Long id) {
        List<Map<String, Object>> current = jdbcTemplate.queryForList(
                "SELECT nombre, slug FROM categorias_producto WHERE id = ?",
                id
        );
        if (current.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria no encontrada");
        }

        int affected = jdbcTemplate.update("DELETE FROM categorias_producto WHERE id = ?", id);
        if (affected == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria no encontrada");
        }
        ApiSupport.recordAudit(jdbcTemplate, "EDICION", "Eliminacion de categoria " + current.get(0).get("nombre") + ".");
    }

    // Productos e inventario
    public List<Map<String, Object>> getProducts() {
        return fetchProducts(false);
    }

    // Devuelve alertas enriquecidas con proveedor y proyeccion de agotamiento.
    public List<Map<String, Object>> getProductAlerts() {
        String sql = """
                SELECT p.id, p.nombre, c.nombre categoria, p.codigo_barras, p.stock, p.stock_minimo,
                       p.precio, p.unidad, p.imagen_url, p.proveedor_id, pr.nombre proveedor,
                       referencia.fecha_referencia,
                       COALESCE(SUM(CASE WHEN v.id IS NOT NULL THEN vd.cantidad ELSE 0 END), 0) unidades_vendidas_30d
                FROM productos p
                JOIN categorias_producto c ON c.id = p.categoria_id
                LEFT JOIN proveedores pr ON pr.id = p.proveedor_id
                CROSS JOIN (
                    SELECT COALESCE(DATE(MAX(fecha_hora)), UTC_DATE()) fecha_referencia
                    FROM ventas
                ) referencia
                LEFT JOIN venta_detalles vd ON vd.producto_id = p.id
                LEFT JOIN ventas v ON v.id = vd.venta_id
                    AND v.estado = 'Pagado'
                    AND DATE(v.fecha_hora) BETWEEN DATE_SUB(referencia.fecha_referencia, INTERVAL 29 DAY)
                                               AND referencia.fecha_referencia
                WHERE p.activo = 1
                  AND p.stock <= p.stock_minimo
                GROUP BY p.id, p.nombre, c.nombre, p.codigo_barras, p.stock, p.stock_minimo,
                         p.precio, p.unidad, p.imagen_url, p.proveedor_id, pr.nombre,
                         referencia.fecha_referencia
                ORDER BY (p.stock <= 0) DESC, (p.stock_minimo - p.stock) DESC, p.nombre
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            int stock = rs.getInt("stock");
            int unitsSold30Days = rs.getInt("unidades_vendidas_30d");
            LocalDate projectionBaseDate = rs.getDate("fecha_referencia").toLocalDate();
            BigDecimal averageDailySales = BigDecimal.valueOf(unitsSold30Days)
                    .divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);

            Integer estimatedDaysRemaining = null;
            if (stock <= 0) {
                estimatedDaysRemaining = 0;
            } else if (unitsSold30Days > 0) {
                estimatedDaysRemaining = (int) Math.ceil((stock * 30.0) / unitsSold30Days);
            }

            Map<String, Object> product = new LinkedHashMap<>();
            product.put("id", rs.getLong("id"));
            product.put("name", rs.getString("nombre"));
            product.put("category", rs.getString("categoria"));
            product.put("barcode", Objects.toString(rs.getString("codigo_barras"), ""));
            product.put("stock", stock);
            product.put("minStock", rs.getInt("stock_minimo"));
            product.put("price", rs.getBigDecimal("precio"));
            product.put("unit", rs.getString("unidad"));
            product.put("imageUrl", Objects.toString(rs.getString("imagen_url"), ""));
            product.put("providerId", rs.getLong("proveedor_id"));
            product.put("providerName", Objects.toString(rs.getString("proveedor"), "Sin proveedor"));
            product.put("unitsSold30Days", unitsSold30Days);
            product.put("averageDailySales", averageDailySales);
            product.put("projectionBaseDate", projectionBaseDate.toString());

            if (estimatedDaysRemaining != null) {
                product.put("estimatedDaysRemaining", estimatedDaysRemaining);
                product.put(
                        "estimatedStockoutDate",
                        projectionBaseDate.plusDays(estimatedDaysRemaining).toString()
                );
            }

            return product;
        });
    }

    // Inventario completo para la tabla principal.
    public List<Map<String, Object>> getInventory() {
        return fetchProducts(false);
    }

    // Inserta producto nuevo y deja que la DB calcule la llave primaria.
    public Map<String, Object> createProduct(Map<String, Object> payload) {
        String nombre = String.valueOf(payload.getOrDefault("nombre", "")).trim();
        if (nombre.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nombre requerido");

        String codigoBarras = String.valueOf(payload.getOrDefault("codigo_barras", ""));
        Long categoriaId = payload.containsKey("categoria_id") ? ((Number) payload.get("categoria_id")).longValue() : null;
        Long proveedorId = ApiSupport.requireLong(payload.get("proveedor_id"), "proveedor requerido", "proveedor invalido");
        ensureProveedorExists(proveedorId);
        BigDecimal precio = new BigDecimal(String.valueOf(payload.getOrDefault("precio", "0")));
        Integer stock = payload.containsKey("stock") ? ((Number) payload.get("stock")).intValue() : 0;
        Integer stockMinimo = payload.containsKey("stock_minimo") ? ((Number) payload.get("stock_minimo")).intValue() : 0;
        String unidad = String.valueOf(payload.getOrDefault("unidad", "pz"));

        Long id = ApiSupport.insertAndReturnId(
                jdbcSupportRepository,
                "INSERT INTO productos(codigo_barras, nombre, categoria_id, proveedor_id, precio, stock, stock_minimo, unidad) VALUES(?,?,?,?,?,?,?,?)",
                stmt -> {
                    stmt.setString(1, codigoBarras);
                    stmt.setString(2, nombre);
                    if (categoriaId != null) stmt.setLong(3, categoriaId);
                    else stmt.setNull(3, Types.BIGINT);
                    stmt.setLong(4, proveedorId);
                    stmt.setBigDecimal(5, precio);
                    stmt.setInt(6, stock);
                    stmt.setInt(7, stockMinimo);
                    stmt.setString(8, unidad);
                }
        );
        ApiSupport.recordAudit(jdbcTemplate, "EDICION", "Creacion de producto " + nombre + " con precio " + precio + " y stock " + stock + ".");
        return Map.of("id", id, "nombre", nombre, "stock", stock);
    }

    // Actualiza producto y compara contra el estado anterior para auditar cambios.
    public Map<String, Object> updateProduct(Long id, Map<String, Object> payload) {
        List<Map<String, Object>> current = jdbcTemplate.queryForList("SELECT * FROM productos WHERE id = ?", id);
        if (current.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado");

        Map<String, Object> curr = current.get(0);
        String nombre = payload.containsKey("nombre") ? String.valueOf(payload.get("nombre")) : String.valueOf(curr.get("nombre"));
        String codigoBarras = payload.containsKey("codigo_barras") ? String.valueOf(payload.get("codigo_barras")) : (curr.get("codigo_barras") != null ? String.valueOf(curr.get("codigo_barras")) : "");
        Long categoriaId = payload.containsKey("categoria_id") ? ((Number) payload.get("categoria_id")).longValue() : (curr.get("categoria_id") != null ? ((Number) curr.get("categoria_id")).longValue() : null);
        Long proveedorId = payload.containsKey("proveedor_id")
                ? ApiSupport.requireLong(payload.get("proveedor_id"), "proveedor requerido", "proveedor invalido")
                : (curr.get("proveedor_id") != null ? ((Number) curr.get("proveedor_id")).longValue() : null);
        if (proveedorId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "proveedor requerido");
        }
        ensureProveedorExists(proveedorId);
        BigDecimal precio = payload.containsKey("precio") ? new BigDecimal(String.valueOf(payload.get("precio"))) : (BigDecimal) curr.get("precio");
        Integer stock = payload.containsKey("stock") ? ((Number) payload.get("stock")).intValue() : (Integer) curr.get("stock");
        Integer stockMinimo = payload.containsKey("stock_minimo") ? ((Number) payload.get("stock_minimo")).intValue() : (Integer) curr.get("stock_minimo");
        String unidad = payload.containsKey("unidad") ? String.valueOf(payload.get("unidad")) : String.valueOf(curr.get("unidad"));

        jdbcTemplate.update(
                "UPDATE productos SET codigo_barras=?, nombre=?, categoria_id=?, proveedor_id=?, precio=?, stock=?, stock_minimo=?, unidad=? WHERE id=?",
                codigoBarras, nombre, categoriaId, proveedorId, precio, stock, stockMinimo, unidad, id
        );

        StringBuilder changes = new StringBuilder();
        if (!Objects.equals(String.valueOf(curr.get("nombre")), nombre)) {
            changes.append("nombre, ");
        }
        if (!Objects.equals(Objects.toString(curr.get("codigo_barras"), ""), codigoBarras)) {
            changes.append("codigo de barras, ");
        }
        if (!Objects.equals(curr.get("categoria_id"), categoriaId)) {
            changes.append("categoria, ");
        }
        if (!Objects.equals(curr.get("proveedor_id"), proveedorId)) {
            changes.append("proveedor, ");
        }
        if (!Objects.equals(curr.get("stock"), stock)) {
            changes.append("stock, ");
        }
        if (!Objects.equals(curr.get("stock_minimo"), stockMinimo)) {
            changes.append("stock minimo, ");
        }
        if (!Objects.equals(String.valueOf(curr.get("unidad")), unidad)) {
            changes.append("unidad, ");
        }

        if (!changes.isEmpty()) {
            String detail = "Edicion de producto " + nombre + ". Cambios: " + changes.substring(0, changes.length() - 2) + ".";
            ApiSupport.recordAudit(jdbcTemplate, "EDICION", detail);
        }

        return Map.of("id", id, "nombre", nombre);
    }

    // Borra el producto solo si no existen dependencias historicas.
    public void deleteProduct(Long id) {
        List<Map<String, Object>> current = jdbcTemplate.queryForList(
                "SELECT nombre, codigo_barras FROM productos WHERE id = ?",
                id
        );
        if (current.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado");
        }

        try {
            int affected = jdbcTemplate.update("DELETE FROM productos WHERE id = ?", id);
            if (affected == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado");
            ApiSupport.recordAudit(jdbcTemplate, "EDICION", "Eliminacion de producto " + current.get(0).get("nombre") + ".");
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede eliminar el producto porque tiene historial (ventas, compras, etc.). Considera desactivarlo en su lugar.");
        }
    }

    // Resuelve un usuario ya creado para regresar la version que consume el frontend.
    private Map<String, Object> getUserById(Long id) {
        return jdbcTemplate.queryForObject(
                """
                SELECT id, username, email, role, nombre_completo, estado, image_url
                FROM usuarios
                WHERE id = ?
                """,
                (rs, rowNum) -> Map.of(
                        "id", rs.getLong("id"),
                        "name", rs.getString("nombre_completo"),
                        "email", formatUserEmail(rs.getString("email"), rs.getString("username")),
                        "role", rs.getString("role"),
                        "status", formatUserStatus(rs.getString("estado")),
                        "imageUrl", Objects.toString(rs.getString("image_url"), "")
                ),
                id
        );
    }

    // Query base de productos; `onlyAlerts` filtra los que ya cruzaron el minimo.
    private List<Map<String, Object>> fetchProducts(boolean onlyAlerts) {
        String sql = """
                SELECT p.id, p.nombre, c.nombre categoria, p.codigo_barras, p.stock, p.stock_minimo,
                       p.precio, p.unidad, p.imagen_url, p.proveedor_id, pr.nombre proveedor
                FROM productos p
                JOIN categorias_producto c ON c.id = p.categoria_id
                LEFT JOIN proveedores pr ON pr.id = p.proveedor_id
                WHERE p.activo = 1
                """ + (onlyAlerts ? " AND p.stock <= p.stock_minimo " : "") + " ORDER BY p.id";
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> Map.ofEntries(
                        Map.entry("id", rs.getLong("id")),
                        Map.entry("name", rs.getString("nombre")),
                        Map.entry("category", rs.getString("categoria")),
                        Map.entry("barcode", Objects.toString(rs.getString("codigo_barras"), "")),
                        Map.entry("stock", rs.getInt("stock")),
                        Map.entry("minStock", rs.getInt("stock_minimo")),
                        Map.entry("price", rs.getBigDecimal("precio")),
                        Map.entry("unit", rs.getString("unidad")),
                        Map.entry("imageUrl", Objects.toString(rs.getString("imagen_url"), "")),
                        Map.entry("providerId", rs.getLong("proveedor_id")),
                        Map.entry("providerName", Objects.toString(rs.getString("proveedor"), ""))
                )
        );
    }

    private void ensureProveedorExists(Long proveedorId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proveedores WHERE id = ? AND activo = 1",
                Integer.class,
                proveedorId
        );
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proveedor no encontrado o inactivo");
        }
    }

    // Lee strings del payload sin convertir null en texto literal.
    private static String payloadString(Map<String, Object> payload, String key) {
        return Objects.toString(payload.get(key), "").trim();
    }

    private static String payloadStringOrDefault(Map<String, Object> payload, String key, String fallback) {
        String value = payloadString(payload, key);
        return value.isBlank() ? fallback : value;
    }

    private static ResponseStatusException userConflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un usuario con ese correo");
    }

    // Normaliza el nombre que el frontend usa para correo en pantalla.
    private static String formatUserEmail(String email, String username) {
        if (email != null && !email.isBlank()) return email;
        if (username == null || username.isBlank()) return "";
        if (username.contains("@")) return username;
        return username + "@pdv.local";
    }

    // El frontend puede mandar email completo o nombre simple; aqui se normaliza.
    private static String usernameFromEmail(String email) {
        String value = email == null ? "" : email.trim();
        int atIndex = value.indexOf("@");
        if (atIndex > 0) {
            return value.substring(0, atIndex);
        }
        return value;
    }

    // Convierte roles del formulario al formato que guarda la base.
    private static String normalizeUserRole(String role) {
        String value = role == null ? "" : role.trim().toLowerCase();
        return switch (value) {
            case "admin" -> "admin";
            case "supervisor" -> "supervisor";
            case "ventas", "caja", "vendedor" -> "vendedor";
            default -> "vendedor";
        };
    }

    // Convierte estado visible a valor interno de base.
    private static String normalizeUserStatus(String status) {
        String value = status == null ? "" : status.trim().toLowerCase();
        return switch (value) {
            case "inactivo", "inactive" -> "inactivo";
            default -> "activo";
        };
    }

    // Presenta el estado con mayuscula inicial para la UI.
    private static String formatUserStatus(String status) {
        return "inactivo".equalsIgnoreCase(status) ? "Inactivo" : "Activo";
    }

    // Genera slugs simples a partir del nombre de una categoria.
    private static String slugify(String input) {
        return input.toLowerCase().trim().replaceAll("[^a-z0-9\\s-]", "").replaceAll("\\s+", "-");
    }
}
