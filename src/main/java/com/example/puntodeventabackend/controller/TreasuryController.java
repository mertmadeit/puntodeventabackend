package com.example.puntodeventabackend.controller;

import com.example.puntodeventabackend.service.TreasuryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tesoreria")
// Resumen de caja, movimientos, cortes y turnos abiertos.
public class TreasuryController {

    private final TreasuryService treasuryService;

    public TreasuryController(TreasuryService treasuryService) {
        this.treasuryService = treasuryService;
    }

    @GetMapping("/resumen")
    public Map<String, Object> getTesoreriaResumen() {
        return treasuryService.getResumen();
    }

    @GetMapping("/movimientos")
    public List<Map<String, Object>> getTesoreriaMovimientos() {
        return treasuryService.getMovimientos();
    }

    @PostMapping("/movimientos")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createTesoreriaMovimiento(@RequestBody Map<String, Object> payload) {
        return treasuryService.createMovimiento(payload);
    }

    @GetMapping("/cortes")
    public List<Map<String, Object>> getTesoreriaCortes() {
        return treasuryService.getCortes();
    }

    @PostMapping("/cortes")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createTesoreriaCorte(@RequestBody Map<String, Object> payload) {
        return treasuryService.createCorte(payload);
    }

    @GetMapping("/turnos")
    public List<Map<String, Object>> getTesoreriaTurnos() {
        return treasuryService.getTurnos();
    }

    @PostMapping("/turnos")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createTesoreriaTurno(@RequestBody Map<String, Object> payload, Authentication authentication) {
        return treasuryService.createTurno(payload, authentication);
    }
}
