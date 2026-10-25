package com.twofourseven.dam.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request and response body for /categories. {@code catalogId} is required on create and cannot
 * change. A null {@code parentId} makes a top-level category. {@code slug} is derived from the
 * name when not supplied. {@code defaultCategory} is read-only: true for the catalog's default
 * category, which is created with the catalog, can't be deleted and must stay top-level.
 */
public record CategoryInstanceDto(
        UUID id,
        UUID catalogId,
        UUID parentId,
        @NotBlank(message = "Category name is required")
        @Size(max = 255) String name,
        @Size(max = 255) String slug,
        Boolean defaultCategory) {

    /** Request body; {@code defaultCategory} is set by the server. */
    public static CategoryInstanceDto request(UUID catalogId, UUID parentId, String name, String slug) {
        return new CategoryInstanceDto(null, catalogId, parentId, name, slug, null);
    }
}
