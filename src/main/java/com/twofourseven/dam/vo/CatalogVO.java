package com.twofourseven.dam.vo;

import java.time.LocalDateTime;
import java.util.UUID;

/** A catalog, as returned by the service tier. */
public record CatalogVO(
        UUID id,
        UUID workspaceId,
        String name,
        String description,
        LocalDateTime createdAt) {
}
