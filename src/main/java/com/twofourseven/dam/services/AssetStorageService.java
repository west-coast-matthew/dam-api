package com.twofourseven.dam.services;

import java.io.InputStream;
import java.util.UUID;

/**
 * AssetStorageService.java
 *
 * <p>This service is responsible for handling storage operations for assets. From an application
 * perspective, the applications is not tighhtly coupled to an particular storage implementation. By
 * default, we utilize the local file system, however other implementations might utilize an
 * relational database of a remote S3 storage location.
 *
 * <p>Paths passed in and out are relative to the workspace's root, as stored in the database
 * (e.g. {@code catalogs/{catalog}/assets/55/0e/84/{asset}/original.png}). See STORAGE_SPECIFICATION.md.
 */
public interface AssetStorageService {

    /**
     * Writes {@code content} to {@code relativePath}, replacing any file already there.
     *
     * @return the number of bytes written
     */
    long store(UUID workspaceId, String relativePath, InputStream content);

    /** Deletes one file if it exists. Failures are logged, not thrown. */
    void delete(UUID workspaceId, String relativePath);

    /** Deletes a directory and everything in it if it exists. Failures are logged, not thrown. */
    void deleteDirectory(UUID workspaceId, String relativeDirectory);

    /** The folder holding an asset's files: {@code catalogs/{catalog}/assets/{aa}/{bb}/{cc}/{asset}}. */
    static String assetDirectory(UUID catalogId, UUID assetId) {
        String hex = assetId.toString().replace("-", "");
        return "catalogs/" + catalogId + "/assets/"
                + hex.substring(0, 2) + "/" + hex.substring(2, 4) + "/" + hex.substring(4, 6) + "/" + assetId;
    }
}
