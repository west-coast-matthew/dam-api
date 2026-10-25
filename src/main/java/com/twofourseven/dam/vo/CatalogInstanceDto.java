package com.twofourseven.dam.vo;

import com.twofourseven.dam.entities.core.Catalog;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request and response body for /catalogs. {@code workspaceId} is required on create and cannot
 * change afterwards. {@code id} and {@code createdAt} are set by the server.
 */
public record CatalogInstanceDto(
        UUID id,
        UUID workspaceId,
        @NotBlank(message = "Catalog name is required")
        @Size(max = Catalog.NAME_MAX_LENGTH) String name,
        @Size(max = Catalog.DESCRIPTION_MAX_LENGTH) String description,
        LocalDateTime createdAt) {
}
