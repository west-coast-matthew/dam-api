package com.twofourseven.dam.services;

import com.twofourseven.dam.config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Stores asset files on the local disk under {@code ${dam.storage.root-dir}/workspaces/{workspace}/}.
 * Every resolved path is checked to stay inside the workspace folder.
 */
@Service
public class FileSystemAssetStorageService implements AssetStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileSystemAssetStorageService.class);

    private final Path root;

    public FileSystemAssetStorageService(StorageProperties properties) {
        this.root = Path.of(properties.rootDir()).toAbsolutePath().normalize();
    }

    @Override
    public long store(UUID workspaceId, String relativePath, InputStream content) {
        Path target = resolve(workspaceId, relativePath);
        Path temp = null;
        try {
            Files.createDirectories(target.getParent());
            // Write next to the target, then move into place so readers never see a partial file
            temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            long size = Files.copy(content, temp, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null;
            log.debug("Stored {} bytes at {}", size, target);
            return size;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store file " + relativePath, e);
        } finally {
            if (temp != null) {
                deleteQuietly(temp);
            }
        }
    }

    @Override
    public void delete(UUID workspaceId, String relativePath) {
        deleteQuietly(resolve(workspaceId, relativePath));
    }

    @Override
    public void deleteDirectory(UUID workspaceId, String relativeDirectory) {
        Path dir = resolve(workspaceId, relativeDirectory);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(FileSystemAssetStorageService::deleteQuietly);
        } catch (IOException e) {
            log.warn("Could not delete directory {}: {}", dir, e.getMessage());
        }
    }

    /** Resolves a stored relative path, rejecting anything that escapes the workspace folder. */
    Path resolve(UUID workspaceId, String relativePath) {
        Path workspaceRoot = root.resolve("workspaces").resolve(workspaceId.toString());
        Path resolved = workspaceRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(workspaceRoot) || resolved.equals(workspaceRoot)) {
            throw new IllegalArgumentException("Storage path escapes the workspace folder: " + relativePath);
        }
        return resolved;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not delete {}: {}", path, e.getMessage());
        }
    }
}
