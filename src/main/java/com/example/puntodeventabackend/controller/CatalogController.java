package com.example.puntodeventabackend.controller;

import com.example.puntodeventabackend.service.PosApiService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final PosApiService posApiService;

    public CatalogController(PosApiService posApiService) {
        this.posApiService = posApiService;
    }

    @GetMapping("/categories")
    public List<Map<String, Object>> getCategories() {
        return posApiService.getCategories();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createCategory(@RequestBody Map<String, Object> payload) {
        return posApiService.createCategory(payload);
    }

    @PutMapping("/categories/{id}")
    public Map<String, Object> updateCategory(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return posApiService.updateCategory(id, payload);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id) {
        posApiService.deleteCategory(id);
    }

    @GetMapping("/products")
    public List<Map<String, Object>> getProducts() {
        return posApiService.getProducts();
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createProduct(@RequestBody Map<String, Object> payload) {
        return posApiService.createProduct(payload);
    }

    @PutMapping("/products/{id}")
    public Map<String, Object> updateProduct(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return posApiService.updateProduct(id, payload);
    }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable Long id) {
        posApiService.deleteProduct(id);
    }

    // Also alias /inventory to product creation for consistency if needed, but products is standard
    @PostMapping("/inventory")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createInventoryProduct(@RequestBody Map<String, Object> payload) {
        return posApiService.createProduct(payload);
    }

    @GetMapping("/products/alerts")
    public List<Map<String, Object>> getProductAlerts() {
        return posApiService.getProductAlerts();
    }

    @GetMapping("/inventory")
    public List<Map<String, Object>> getInventory() {
        return posApiService.getInventory();
    }
}
