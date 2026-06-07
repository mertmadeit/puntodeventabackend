package com.example.puntodeventabackend.controller;

import com.example.puntodeventabackend.service.PosApiService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tesoreria")
public class TreasuryController {

    private final PosApiService posApiService;

    public TreasuryController(PosApiService posApiService) {
        this.posApiService = posApiService;
    }

    @GetMapping("/resumen")
    public Map<String, Object> getTesoreriaResumen() {
        return posApiService.getTesoreriaResumen();
    }

    @GetMapping("/movimientos")
    public List<Map<String, Object>> getTesoreriaMovimientos() {
        return posApiService.getTesoreriaMovimientos();
    }

    @PostMapping("/movimientos")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createTesoreriaMovimiento(@RequestBody Map<String, Object> payload) {
        return posApiService.createTesoreriaMovimiento(payload);
    }

    @GetMapping("/cortes")
    public List<Map<String, Object>> getTesoreriaCortes() {
        return posApiService.getTesoreriaCortes();
    }

    @PostMapping("/cortes")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createTesoreriaCorte(@RequestBody Map<String, Object> payload) {
        return posApiService.createTesoreriaCorte(payload);
    }

    @GetMapping("/turnos")
    public List<Map<String, Object>> getTesoreriaTurnos() {
        return posApiService.getTesoreriaTurnos();
    }
}
