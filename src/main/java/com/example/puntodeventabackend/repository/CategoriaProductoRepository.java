package com.example.puntodeventabackend.repository;

import com.example.puntodeventabackend.model.CategoriaProductoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaProductoRepository extends JpaRepository<CategoriaProductoEntity, Long> {
}
