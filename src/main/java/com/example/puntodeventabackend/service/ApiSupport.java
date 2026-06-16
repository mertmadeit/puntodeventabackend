package com.example.puntodeventabackend.service;

import com.example.puntodeventabackend.repository.JdbcSupportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ApiSupport {

    private ApiSupport() {
    }

    static Long insertAndReturnId(JdbcSupportRepository repository, String sql, JdbcSupportRepository.StatementBinder binder) {
        try {
            return repository.insertAndReturnId(sql, binder);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo obtener el id generado");
        }
    }

    static String toUtcIsoString(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toInstant(ZoneOffset.UTC).toString();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toString();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().toString();
        }
        return String.valueOf(value);
    }

    static List<Map<String, Object>> castListOfMap(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) map;
                out.add(casted);
            }
        }
        return out;
    }

    static BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd.setScale(2, RoundingMode.HALF_UP);
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        String raw = String.valueOf(value).trim();
        if (raw.isBlank()) return null;
        try {
            return new BigDecimal(raw).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "monto invalido");
        }
    }

    static BigDecimal requirePositiveAmount(Object value, String message) {
        BigDecimal amount = toBigDecimal(value);
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return amount;
    }

    static BigDecimal requireNonNegativeAmount(Object value, String message) {
        BigDecimal amount = toBigDecimal(value);
        if (amount == null || amount.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return amount;
    }

    static BigDecimal nvlMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    static Long requireLong(Object value, String blankMessage, String invalidMessage) {
        String raw = String.valueOf(value == null ? "" : value).trim();
        if (raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, blankMessage);
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalidMessage);
        }
    }

    static Integer requirePositiveInteger(Object value, String message) {
        try {
            Integer number = Integer.valueOf(String.valueOf(value));
            if (number <= 0) {
                throw new NumberFormatException();
            }
            return number;
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    static String optionalString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    static boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
