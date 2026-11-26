package com.twofourseven.dam.services;

import com.twofourseven.dam.config.StorageProperties;
import com.twofourseven.dam.entities.core.Asset;
import com.twofourseven.dam.entities.core.Catalog;
import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.ResourceNotFoundException;
import com.twofourseven.dam.repositories.AssetRepository;
import com.twofourseven.dam.services.AssetDownloadService.DownloadBundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetDownloadServiceTest {

    @Mock
    private AssetRepository assetRepository;
    @Mock
    private AssetStorageService storage;

    private AssetDownloadService service;
    private final Catalog catalog = Fixtures.catalog("Photos");

    @BeforeEach
    void setUp() {
        service = new AssetDownloadService(assetRepository, storage,
                new StorageProperties("/unused", DataSize.ofMegabytes(1), 10, 3));
    }

    private Asset asset(String name) {
        Asset a = new Asset();
        a.setCatalog(catalog);
        a.setOriginalFilename(name);
        a.setStoragePath("assets/" + a.getId() + "/" + name);
        return a;
    }

    @Test
    void uniqueNamesAddsSuffixBeforeTheExtension() {
        assertThat(AssetDownloadService.uniqueNames(List.of("a.jpg", "A.JPG", "a.jpg", "b", "b", "x/y.png")))
                .containsExactly("a.jpg", "A (2).JPG", "a (3).jpg", "b", "b (2)", "x_y.png");
    }

    @Test
    void prepareKeepsRequestOrderAndDropsDuplicates() {
        Asset a = asset("a.jpg");
        Asset b = asset("a.jpg");
        when(assetRepository.findAllById(List.of(b.getId(), a.getId()))).thenReturn(List.of(a, b));

        DownloadBundle bundle = service.prepare(List.of(b.getId(), a.getId(), b.getId()));

        assertThat(bundle.filename()).matches("assets-\\d{8}-\\d{6}\\.zip");
        assertThat(bundle.entries()).extracting(AssetDownloadService.DownloadEntry::storagePath)
                .containsExactly(b.getStoragePath(), a.getStoragePath());
        assertThat(bundle.entries()).extracting(AssetDownloadService.DownloadEntry::name)
                .containsExactly("a.jpg", "a (2).jpg");
    }

    @Test
    void prepareRejectsEmptyTooManyAndUnknown() {
        assertThatThrownBy(() -> service.prepare(List.of())).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.prepare(null)).isInstanceOf(BadRequestException.class);
        List<UUID> four = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> service.prepare(four)).isInstanceOf(BadRequestException.class).hasMessageContaining("3");

        UUID missing = UUID.randomUUID();
        when(assetRepository.findAllById(List.of(missing))).thenReturn(List.of());
        assertThatThrownBy(() -> service.prepare(List.of(missing))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void writeZipStreamsEachReadableFile() throws IOException {
        Asset a = asset("a.jpg");
        Asset gone = asset("gone.jpg");
        UUID workspaceId = catalog.getWorkspace().getId();
        when(storage.open(eq(workspaceId), eq(a.getStoragePath())))
                .thenReturn(new ByteArrayInputStream("AAA".getBytes(StandardCharsets.UTF_8)));
        when(storage.open(eq(workspaceId), eq(gone.getStoragePath()))).thenThrow(new UncheckedIOException(new IOException("missing")));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.writeZip(service.bundle(List.of(a, gone), "picks"), out);

        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                names.add(e.getName());
                assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("AAA");
            }
        }
        assertThat(names).containsExactly("a.jpg");
    }

    @Test
    void bundleNamesTheZip() {
        assertThat(service.bundle(List.of(asset("a.jpg")), "spring-picks").filename()).isEqualTo("spring-picks.zip");
    }
}
