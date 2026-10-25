package com.twofourseven.dam.vo;

import java.time.LocalDateTime;
import java.util.UUID;

/** A previous file of an asset, kept when the file was replaced with versioning enabled. */
public record AssetVersionVO(
        UUID id,
        UUID assetId,
        int versionNumber,
        long fileSize,
        LocalDateTime createdAt) {
}
