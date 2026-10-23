package com.twofourseven.dam.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Database connection settings (dam.db.*). Used to build spring.datasource.*
 * in application.properties. Startup fails with a clear message if any are missing.
 */
@Validated
@ConfigurationProperties(prefix = "dam.db")
public record DatabaseProperties(
        @NotBlank String server,
        @Min(1) @Max(65535) int port,
        @NotBlank String name,
        @NotBlank String username,
        @NotBlank String password) {

    @Override
    public String toString() {
        // Never log the password
        return "DatabaseProperties[server=%s, port=%d, name=%s, username=%s]".formatted(server, port, name, username);
    }
}
