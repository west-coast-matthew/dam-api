package com.twofourseven.dam.vo;

import com.twofourseven.dam.entities.core.AssetStatus;
import com.twofourseven.dam.entities.core.MediaType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Denormalized asset view used as the /assets payload.
 *
 * <p>Writable fields: {@code catalogId} (create only), {@code versioningEnabled},
 * {@code customMetadata}, {@code categoryIds} and {@code tags}. On update a null field leaves the
 * current value unchanged. Every other field is derived by the server from the uploaded file.
 *
 * @param currentVersion 1 for the original upload, incremented on each versioned file replacement
 * @param tags           tag names; unknown names are created in the asset's catalog
 */
public record AssetInstanceDto(
        UUID id,
        UUID catalogId,
        String catalogName,
        UUID workspaceId,
        String originalFilename,
        MediaType mediaType,
        String mimeType,
        Long fileSize,
        AssetStatus status,
        Boolean versioningEnabled,
        Integer currentVersion,
        Map<String, Object> customMetadata,
        List<UUID> categoryIds,
        List<String> tags,
        LocalDateTime createdAt) {

    /** Request body with only the writable fields set. */
    public static AssetInstanceDto request(UUID catalogId, Boolean versioningEnabled,
                                           Map<String, Object> customMetadata,
                                           List<UUID> categoryIds, List<String> tags) {
        return new AssetInstanceDto(null, catalogId, null, null, null, null, null, null, null,
                versioningEnabled, null, customMetadata, categoryIds, tags, null);
    }
}
