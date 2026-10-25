package com.twofourseven.dam.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Asset file storage settings (dam.storage.*). See STORAGE_SPECIFICATION.md for the layout under
 * {@code rootDir}. Directories are created on first write, so the app starts even if it is missing.
 *
 * @param rootDir          absolute directory that holds {@code workspaces/...}
 * @param maxExtractedSize largest file accepted from inside an uploaded zip (guards against zip bombs)
 * @param maxBulkFiles     most media files accepted in one bulk upload
 */
@Validated
@ConfigurationProperties(prefix = "dam.storage")
public record StorageProperties(
        @NotBlank String rootDir,
        @NotNull DataSize maxExtractedSize,
        @Min(1) int maxBulkFiles) {
}
