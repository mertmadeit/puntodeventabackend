package com.example.puntodeventabackend.controller;

import com.example.puntodeventabackend.service.PosApiService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sales")
public class SalesController {

    private final PosApiService posApiService;

    public SalesController(PosApiService posApiService) {
        this.posApiService = posApiService;
    }

    @GetMapping
    public List<Map<String, Object>> getSales() {
        return posApiService.getSales();
    }
    @GetMapping("/today-summary")
    public Map<String, Object> getSalesTodaySummary() {
        return posApiService.getSalesTodaySummary();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createSale(@RequestBody Map<String, Object> payload) {
        return posApiService.createSale(payload);
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancelSale(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return posApiService.cancelSale(id, payload);
    }
}
