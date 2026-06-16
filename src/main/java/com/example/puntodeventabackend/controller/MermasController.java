package com.example.puntodeventabackend.controller;

import com.example.puntodeventabackend.service.InventoryLossService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mermas")
public class MermasController {

    private final InventoryLossService inventoryLossService;

    public MermasController(InventoryLossService inventoryLossService) {
        this.inventoryLossService = inventoryLossService;
    }

    @GetMapping
    public List<Map<String, Object>> getMermas() {
        return inventoryLossService.getMermas();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createMerma(@RequestBody Map<String, Object> payload) {
        return inventoryLossService.createMerma(payload);
    }
}
