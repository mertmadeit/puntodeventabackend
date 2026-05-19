package com.example.puntodeventabackend.repository;

import com.example.puntodeventabackend.model.ProductoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoRepository extends JpaRepository<ProductoEntity, Long> {
}
