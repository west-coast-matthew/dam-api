package com.twofourseven.dam.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request and response body for /categories. {@code catalogId} is required on create and cannot
 * change. A null {@code parentId} makes a top-level category. {@code slug} is derived from the
 * name when not supplied.
 */
public record CategoryInstanceDto(
        UUID id,
        UUID catalogId,
        UUID parentId,
        @NotBlank(message = "Category name is required")
        @Size(max = 255) String name,
        @Size(max = 255) String slug) {
}
