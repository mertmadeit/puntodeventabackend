package com.example.puntodeventabackend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuarios")
public class UsuarioEntity {

    @Id
    private Long id;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "nombre_completo", nullable = false)
    private String nombreCompleto;

    @Column(name = "estado", nullable = false)
    private String estado;

    @Column(name = "image_url", columnDefinition = "LONGTEXT")
    private String imageUrl;

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getRole() {
        return role;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public String getEstado() {
        return estado;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}
