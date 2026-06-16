package com.example.puntodeventabackend.controller;

import com.example.puntodeventabackend.service.PurchasesService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PurchasesController {

    private final PurchasesService purchasesService;

    public PurchasesController(PurchasesService purchasesService) {
        this.purchasesService = purchasesService;
    }

    @GetMapping("/proveedores")
    public List<Map<String, Object>> getProveedores() {
        return purchasesService.getProveedores();
    }

    @PostMapping("/proveedores")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createProveedor(@RequestBody Map<String, Object> payload) {
        return purchasesService.createProveedor(payload);
    }

    @PutMapping("/proveedores/{id}")
    public Map<String, Object> updateProveedor(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return purchasesService.updateProveedor(id, payload);
    }

    @DeleteMapping("/proveedores/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProveedor(@PathVariable Long id) {
        purchasesService.deleteProveedor(id);
    }

    @GetMapping("/compras")
    public List<Map<String, Object>> getCompras() {
        return purchasesService.getCompras();
    }

    @PostMapping("/compras")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createCompra(@RequestBody Map<String, Object> payload) {
        return purchasesService.createCompra(payload);
    }
}
