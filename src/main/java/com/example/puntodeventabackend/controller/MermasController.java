package com.example.puntodeventabackend.controller;

import com.example.puntodeventabackend.service.PosApiService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mermas")
public class MermasController {

    private final PosApiService posApiService;

    public MermasController(PosApiService posApiService) {
        this.posApiService = posApiService;
    }

    @GetMapping
    public List<Map<String, Object>> getMermas() {
        return posApiService.getMermas();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createMerma(@RequestBody Map<String, Object> payload) {
        return posApiService.createMerma(payload);
    }
}
