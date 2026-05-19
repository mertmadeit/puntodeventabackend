package com.example.puntodeventabackend.repository;

import com.example.puntodeventabackend.model.UsuarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<UsuarioEntity, Long> {
}
