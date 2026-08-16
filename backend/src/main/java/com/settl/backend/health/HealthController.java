package com.settl.backend.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "System health check endpoint")
public class HealthController {

    @GetMapping
    @Operation(summary = "Check service health", description = "Returns service operational status, name, and current timestamp")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "settl-backend",
                "timestamp", Instant.now().toString(),
                "version", "1.0.0"
        ));
    }
}
