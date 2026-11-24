package com.twofourseven.dam.repositories;

import java.util.UUID;

/**
 * Just enough of an asset to find its file in storage.
 *
 * @param storagePath path relative to the workspace folder, as stored in {@code assets.storage_path}
 */
public record AssetFileRef(UUID assetId, UUID catalogId, UUID workspaceId, String storagePath, String originalFilename) {
}
