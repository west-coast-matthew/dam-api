package com.twofourseven.dam.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request and response body for /lightboxes. {@code catalogId} and {@code userId} are required on
 * create and cannot change. {@code shared} defaults to false. {@code itemCount} is read-only.
 */
public record LightboxInstanceDto(
        UUID id,
        UUID catalogId,
        UUID userId,
        @NotBlank(message = "Lightbox title is required")
        @Size(max = 255) String title,
        String description,
        Boolean shared,
        Integer itemCount) {
}
