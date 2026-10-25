package com.twofourseven.dam.services;

import com.twofourseven.dam.config.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Uses a temporary folder; no database involved. */
class FileSystemAssetStorageServiceTest {

    @TempDir
    Path root;

    private FileSystemAssetStorageService storage;
    private final UUID workspaceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        storage = new FileSystemAssetStorageService(new StorageProperties(root.toString(), DataSize.ofGigabytes(1), 1000));
    }

    @Test
    void assetDirectoryIsShardedByAssetId() {
        UUID catalogId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID assetId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        assertThat(AssetStorageService.assetDirectory(catalogId, assetId))
                .isEqualTo("catalogs/11111111-1111-1111-1111-111111111111/assets/55/0e/84/550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    void storeWritesUnderWorkspaceFolderAndReplaces() throws Exception {
        String path = "catalogs/c/assets/aa/bb/cc/a/original.png";

        long size = storage.store(workspaceId, path, new ByteArrayInputStream("hello".getBytes()));
        storage.store(workspaceId, path, new ByteArrayInputStream("bye".getBytes()));

        Path file = root.resolve("workspaces").resolve(workspaceId.toString()).resolve(path);
        assertThat(size).isEqualTo(5);
        assertThat(Files.readString(file)).isEqualTo("bye");
        try (var files = Files.list(file.getParent())) {
            assertThat(files).hasSize(1); // no temp files left behind
        }
    }

    @Test
    void deleteAndDeleteDirectory() throws Exception {
        storage.store(workspaceId, "catalogs/c/assets/x/a/original.png", new ByteArrayInputStream("1".getBytes()));
        storage.store(workspaceId, "catalogs/c/assets/x/a/version_2.png", new ByteArrayInputStream("2".getBytes()));
        Path dir = root.resolve("workspaces").resolve(workspaceId.toString()).resolve("catalogs/c/assets/x/a");

        storage.delete(workspaceId, "catalogs/c/assets/x/a/original.png");
        assertThat(dir.resolve("original.png")).doesNotExist();
        assertThat(dir.resolve("version_2.png")).exists();

        storage.deleteDirectory(workspaceId, "catalogs/c/assets/x/a");
        assertThat(dir).doesNotExist();
    }

    @Test
    void deletingMissingFilesIsQuiet() {
        storage.delete(workspaceId, "catalogs/none.png");
        storage.deleteDirectory(workspaceId, "catalogs/none");
    }

    @Test
    void pathTraversalIsRejected() {
        assertThatThrownBy(() -> storage.store(workspaceId, "../other/evil.png", new ByteArrayInputStream(new byte[1])))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.deleteDirectory(workspaceId, "."))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
