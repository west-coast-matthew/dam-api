package com.twofourseven.dam.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** A workspace and its catalogs, as returned by the service tier. */
public record WorkspaceVO(
        UUID id,
        String name,
        String description,
        LocalDateTime createdAt,
        List<CatalogVO> catalogs) {
}
