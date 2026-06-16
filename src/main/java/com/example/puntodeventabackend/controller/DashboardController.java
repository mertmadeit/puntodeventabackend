package com.example.puntodeventabackend.controller;

import com.example.puntodeventabackend.service.PosApiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
// Datos resumidos y series para las graficas del panel.
public class DashboardController {

    private final PosApiService posApiService;

    public DashboardController(PosApiService posApiService) {
        this.posApiService = posApiService;
    }

    @GetMapping("/resumen")
    public Map<String, Object> getDashboardResumen() {
        return posApiService.getDashboardResumen();
    }

    @GetMapping("/series")
    public List<Map<String, Object>> getDashboardSeries() {
        return posApiService.getDashboardSeries();
    }
}
