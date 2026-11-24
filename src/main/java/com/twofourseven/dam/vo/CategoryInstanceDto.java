package com.twofourseven.dam.vo;

import com.twofourseven.dam.entities.core.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request and response body for /categories. {@code catalogId} is required on create and cannot
 * change. A null {@code parentId} makes a top-level category. {@code slug} is derived from the
 * name when not supplied. {@code position} is the 0-based order among siblings; when null a new
 * category goes last and an update keeps its place. {@code defaultCategory} and {@code assetCount}
 * are read-only: the first is true for the catalog's default category, which is created with the
 * catalog, can't be deleted and must stay top-level.
 */
public record CategoryInstanceDto(
        UUID id,
        UUID catalogId,
        UUID parentId,
        @NotBlank(message = "Category name is required")
        @Size(max = Category.NAME_MAX_LENGTH) String name,
        @Size(max = 255) String slug,
        @Size(max = Category.DESCRIPTION_MAX_LENGTH) String description,
        @PositiveOrZero Integer position,
        Boolean defaultCategory,
        Integer assetCount) {

    /** Request body; {@code defaultCategory} and {@code assetCount} are set by the server. */
    public static CategoryInstanceDto request(UUID catalogId, UUID parentId, String name, String slug) {
        return new CategoryInstanceDto(null, catalogId, parentId, name, slug, null, null, null, null);
    }
}
