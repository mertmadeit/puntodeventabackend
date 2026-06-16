package com.example.puntodeventabackend.controller;

import com.example.puntodeventabackend.service.PosApiService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
// Entrada de autenticacion: login, usuario actual y cierre de sesion.
public class AuthController {

    private final PosApiService posApiService;

    public AuthController(PosApiService posApiService) {
        this.posApiService = posApiService;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> payload) {
        return posApiService.login(payload);
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        return posApiService.me(authentication);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        posApiService.logout(authHeader);
    }
}
