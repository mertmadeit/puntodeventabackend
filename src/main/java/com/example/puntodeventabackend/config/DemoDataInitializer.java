package com.example.puntodeventabackend.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DemoDataInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public DemoDataInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensurePasswordColumn();
        upsertUser(1L, "admin", "admin", "admin", "Mert Made It");
        upsertUser(2L, "juan", "admin", "vendedor", "Juan Perez");
        upsertUser(3L, "ana", "admin", "vendedor", "Ana Diaz");
        upsertUser(4L, "carlos", "admin", "supervisor", "Carlos Perez");
        upsertUser(5L, "laura", "admin", "supervisor", "Laura Gomez");
    }

    private void ensurePasswordColumn() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'usuarios'
                  AND column_name = 'password'
                """,
                Integer.class
        );

        if (count != null && count == 0) {
            jdbcTemplate.execute("ALTER TABLE usuarios ADD COLUMN password VARCHAR(255) NULL");
        }
    }

    private void upsertUser(Long id, String username, String rawPassword, String role, String fullName) {
        jdbcTemplate.update(
                """
                INSERT INTO usuarios (id, username, password, role, nombre_completo, estado)
                VALUES (?, ?, ?, ?, ?, 'activo')
                ON DUPLICATE KEY UPDATE
                  role = VALUES(role),
                  nombre_completo = VALUES(nombre_completo),
                  estado = 'activo',
                  password = CASE
                    WHEN password IS NULL OR password = '' THEN VALUES(password)
                    ELSE password
                  END
                """,
                id,
                username,
                passwordEncoder.encode(rawPassword),
                role,
                fullName
        );
    }
}
