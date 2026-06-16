package com.example.puntodeventabackend.security;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthTokenService {

    private static final long TTL_SECONDS = 60 * 60 * 8; // 8h

    // Sesiones efimeras guardadas en memoria; se pierden al reiniciar el backend.
    private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();

    // Emite un token nuevo y guarda la sesion con fecha de expiracion.
    public String issueToken(Long userId, String username, String role) {
        String token = UUID.randomUUID().toString().replace("-", "") + "." + UUID.randomUUID();
        SessionData data = new SessionData(userId, username, role, Instant.now().plusSeconds(TTL_SECONDS));
        sessions.put(token, data);
        return token;
    }

    // Revisa si el token sigue vigente y devuelve la sesion asociada.
    public SessionData validate(String token) {
        if (token == null || token.isBlank()) return null;
        SessionData data = sessions.get(token);
        if (data == null) return null;
        if (Instant.now().isAfter(data.expiresAt())) {
            sessions.remove(token);
            return null;
        }
        return data;
    }

    // Elimina el token cuando el usuario cierra sesion.
    public void revoke(String token) {
        if (token == null || token.isBlank()) return;
        sessions.remove(token);
    }

    public record SessionData(Long userId, String username, String role, Instant expiresAt) {
    }
}
