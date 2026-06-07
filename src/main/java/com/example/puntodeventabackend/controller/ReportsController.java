package com.example.puntodeventabackend.controller;

import com.example.puntodeventabackend.service.PosApiService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReportsController {

    private final PosApiService posApiService;

    public ReportsController(PosApiService posApiService) {
        this.posApiService = posApiService;
    }

    @GetMapping("/reportes/ventas")
    public List<Map<String, Object>> getReportesVentas() {
        return posApiService.getReportesVentas();
    }

    @PostMapping("/reportes/ventas")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createReporteVenta(@RequestBody Map<String, Object> payload) {
        return posApiService.createReporteVenta(payload);
    }

    @GetMapping("/audit")
    public List<Map<String, Object>> getAudit() {
        return posApiService.getAudit();
    }
}
