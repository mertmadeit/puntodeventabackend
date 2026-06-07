package com.example.puntodeventabackend.controller;

import com.example.puntodeventabackend.service.PosApiService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PurchasesController {

    private final PosApiService posApiService;

    public PurchasesController(PosApiService posApiService) {
        this.posApiService = posApiService;
    }

    @GetMapping("/proveedores")
    public List<Map<String, Object>> getProveedores() {
        return posApiService.getProveedores();
    }

    @PostMapping("/proveedores")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createProveedor(@RequestBody Map<String, Object> payload) {
        return posApiService.createProveedor(payload);
    }

    @PutMapping("/proveedores/{id}")
    public Map<String, Object> updateProveedor(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return posApiService.updateProveedor(id, payload);
    }

    @DeleteMapping("/proveedores/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProveedor(@PathVariable Long id) {
        posApiService.deleteProveedor(id);
    }

    @GetMapping("/compras")
    public List<Map<String, Object>> getCompras() {
        return posApiService.getCompras();
    }

    @PostMapping("/compras")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createCompra(@RequestBody Map<String, Object> payload) {
        return posApiService.createCompra(payload);
    }
}
