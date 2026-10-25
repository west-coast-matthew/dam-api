package com.twofourseven.dam.services;

import com.twofourseven.dam.config.StorageProperties;
import com.twofourseven.dam.entities.core.Asset;
import com.twofourseven.dam.entities.core.AssetStatus;
import com.twofourseven.dam.entities.core.AssetVersion;
import com.twofourseven.dam.entities.core.Catalog;
import com.twofourseven.dam.entities.core.Category;
import com.twofourseven.dam.entities.core.MediaType;
import com.twofourseven.dam.entities.core.Tag;
import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.ResourceNotFoundException;
import com.twofourseven.dam.repositories.AssetDownloadLogRepository;
import com.twofourseven.dam.repositories.AssetRepository;
import com.twofourseven.dam.repositories.AssetVersionRepository;
import com.twofourseven.dam.repositories.CatalogRepository;
import com.twofourseven.dam.repositories.CategoryRepository;
import com.twofourseven.dam.repositories.LightboxItemRepository;
import com.twofourseven.dam.repositories.TagRepository;
import com.twofourseven.dam.repositories.UserAssetInteractionRepository;
import com.twofourseven.dam.vo.AssetInstanceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Repositories and storage are mocked; the real upload reader is used. */
@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock
    private AssetRepository assetRepository;
    @Mock
    private AssetVersionRepository assetVersionRepository;
    @Mock
    private CatalogRepository catalogRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private LightboxItemRepository lightboxItemRepository;
    @Mock
    private UserAssetInteractionRepository interactionRepository;
    @Mock
    private AssetDownloadLogRepository downloadLogRepository;
    @Mock
    private AssetStorageService storage;
    @Mock
    private CategoryService categoryService;

    private AssetService service;
    private final Catalog catalog = Fixtures.catalog("Photos");
    private final Category defaultCategory = Category.createDefaultFor(catalog);
    /** Paths written to storage, in order, with their content. */
    private final List<String> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        UploadedMediaReader reader = new UploadedMediaReader(new StorageProperties("/unused", DataSize.ofMegabytes(1)));
        service = new AssetService(assetRepository, assetVersionRepository, catalogRepository, categoryRepository,
                tagRepository, lightboxItemRepository, interactionRepository, downloadLogRepository, storage, reader,
                categoryService);
        lenient().when(categoryService.defaultCategoryOf(catalog)).thenReturn(defaultCategory);
        lenient().when(storage.store(any(), anyString(), any())).thenAnswer(inv -> {
            byte[] bytes = inv.<InputStream>getArgument(2).readAllBytes();
            stored.add(inv.getArgument(1) + "=" + new String(bytes));
            return (long) bytes.length;
        });
        lenient().when(assetRepository.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content.getBytes());
    }

    private Asset existingAsset(boolean versioning) {
        Asset asset = new Asset();
        asset.setCatalog(catalog);
        asset.setOriginalFilename("photo.png");
        asset.setMediaType(MediaType.IMAGE);
        asset.setMimeType("image/png");
        asset.setFileSize(10L);
        asset.setStatus(AssetStatus.READY);
        asset.setVersioningEnabled(versioning);
        asset.setStoragePath(dir(asset) + "/original.png");
        when(assetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));
        return asset;
    }

    private String dir(Asset asset) {
        return AssetStorageService.assetDirectory(catalog.getId(), asset.getId());
    }

    @Test
    void createStoresOriginalAndDerivesMediaType() {
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));

        AssetInstanceDto result = service.create(
                AssetInstanceDto.request(catalog.getId(), true, Map.of("photographer", "Ann"), null, null),
                file("Sunset.JPG", "jpeg!"));

        assertThat(result.mediaType()).isEqualTo(MediaType.IMAGE);
        assertThat(result.mimeType()).isEqualTo("image/jpeg");
        assertThat(result.originalFilename()).isEqualTo("Sunset.JPG");
        assertThat(result.fileSize()).isEqualTo(5L);
        assertThat(result.status()).isEqualTo(AssetStatus.PENDING_PROCESSING);
        assertThat(result.versioningEnabled()).isTrue();
        assertThat(result.currentVersion()).isEqualTo(1);
        assertThat(result.customMetadata()).containsEntry("photographer", "Ann");
        assertThat(result.workspaceId()).isEqualTo(catalog.getWorkspace().getId());
        assertThat(result.catalogName()).isEqualTo("Photos");
        String expectedDir = AssetStorageService.assetDirectory(catalog.getId(), result.id());
        assertThat(stored).containsExactly(expectedDir + "/original.jpg=jpeg!");
        verify(storage).store(eq(catalog.getWorkspace().getId()), anyString(), any());
    }

    @Test
    void createFromZipUsesInnerFile() {
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        MockMultipartFile zip = new MockMultipartFile("file", "upload.zip", "application/zip",
                ZipFixtures.zip("movie/trailer.mp4", "video!"));

        AssetInstanceDto result = service.create(AssetInstanceDto.request(catalog.getId(), null, null, null, null), zip);

        assertThat(result.originalFilename()).isEqualTo("trailer.mp4");
        assertThat(result.mediaType()).isEqualTo(MediaType.VIDEO);
        assertThat(result.versioningEnabled()).isFalse();
        assertThat(stored).singleElement().asString().endsWith("/original.mp4=video!");
    }

    @Test
    void createWithoutCategoriesUsesDefault() {
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));

        AssetInstanceDto result = service.create(AssetInstanceDto.request(catalog.getId(), null, null, null, null),
                file("a.png", "x"));

        assertThat(result.categoryIds()).containsExactly(defaultCategory.getId());
    }

    @Test
    void createWithCategoriesDoesNotAddDefault() {
        Category animals = Fixtures.category(catalog, null, "Animals");
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        when(categoryRepository.findAllById(any())).thenReturn(List.of(animals));

        AssetInstanceDto result = service.create(AssetInstanceDto.request(catalog.getId(), null, null,
                List.of(animals.getId()), null), file("a.png", "x"));

        assertThat(result.categoryIds()).containsExactly(animals.getId());
        verify(categoryService, never()).defaultCategoryOf(any());
    }

    @Test
    void updateWithEmptyCategoriesFallsBackToDefault() {
        Asset asset = existingAsset(false);
        asset.getCategories().add(Fixtures.category(catalog, null, "Animals"));
        when(categoryRepository.findAllById(any())).thenReturn(List.of());

        AssetInstanceDto result = service.update(asset.getId(), AssetInstanceDto.request(null, null, null, List.of(), null));

        assertThat(result.categoryIds()).containsExactly(defaultCategory.getId());
    }

    @Test
    void savingAnOlderAssetWithoutCategoriesAssignsDefault() {
        Asset asset = existingAsset(false);

        service.replaceFile(asset.getId(), file("photo.png", "new"));

        assertThat(asset.getCategories()).containsExactly(defaultCategory);
    }

    @Test
    void createRequiresCatalog() {
        assertThatThrownBy(() -> service.create(AssetInstanceDto.request(null, null, null, null, null), file("a.png", "x")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("catalogId");
    }

    @Test
    void createInUnknownCatalogIsNotFound() {
        UUID id = UUID.randomUUID();
        when(catalogRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(AssetInstanceDto.request(id, null, null, null, null), file("a.png", "x")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createWithUnsupportedFileStoresNothing() {
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));

        assertThatThrownBy(() -> service.create(AssetInstanceDto.request(catalog.getId(), null, null, null, null),
                file("song.mp3", "x")))
                .isInstanceOf(BadRequestException.class);
        assertThat(stored).isEmpty();
        verify(assetRepository, never()).save(any());
    }

    @Test
    void createLinksCategoriesAndFindsOrCreatesTags() {
        Category animals = Fixtures.category(catalog, null, "Animals");
        Tag existing = new Tag();
        existing.setCatalog(catalog);
        existing.setName("Nature");
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        when(categoryRepository.findAllById(any())).thenReturn(List.of(animals));
        when(tagRepository.findFirstByCatalogIdAndNameIgnoreCase(catalog.getId(), "nature")).thenReturn(Optional.of(existing));
        when(tagRepository.findFirstByCatalogIdAndNameIgnoreCase(catalog.getId(), "Sky")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));

        AssetInstanceDto result = service.create(AssetInstanceDto.request(catalog.getId(), null, null,
                List.of(animals.getId()), List.of("nature", " Sky ", "NATURE", "")), file("a.png", "x"));

        assertThat(result.categoryIds()).containsExactly(animals.getId());
        assertThat(result.tags()).containsExactly("Nature", "Sky");
    }

    @Test
    void categoryFromAnotherCatalogIsRejected() {
        Category foreign = Fixtures.category(Fixtures.catalog("Other"), null, "X");
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        when(categoryRepository.findAllById(any())).thenReturn(List.of(foreign));

        assertThatThrownBy(() -> service.create(AssetInstanceDto.request(catalog.getId(), null, null,
                List.of(foreign.getId()), null), file("a.png", "x")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("different catalog");
    }

    @Test
    void unknownCategoryIsRejected() {
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        when(categoryRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(AssetInstanceDto.request(catalog.getId(), null, null,
                List.of(UUID.randomUUID()), null), file("a.png", "x")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unknown category");
    }

    @Test
    void replaceFileWithVersioningKeepsPreviousFileAsVersion() {
        Asset asset = existingAsset(true);
        String original = asset.getStoragePath();

        AssetInstanceDto result = service.replaceFile(asset.getId(), file("photo-v2.webp", "new!"));

        assertThat(result.id()).isEqualTo(asset.getId());
        assertThat(result.currentVersion()).isEqualTo(2);
        assertThat(result.mimeType()).isEqualTo("image/webp");
        assertThat(result.originalFilename()).isEqualTo("photo-v2.webp");
        assertThat(result.status()).isEqualTo(AssetStatus.PENDING_PROCESSING);
        assertThat(asset.getStoragePath()).isEqualTo(dir(asset) + "/version_2.webp");
        assertThat(asset.getVersions()).singleElement().satisfies(v -> {
            assertThat(v.getVersionNumber()).isEqualTo(1);
            assertThat(v.getStoragePath()).isEqualTo(original);
            assertThat(v.getFileSize()).isEqualTo(10L);
            assertThat(v.getAsset()).isSameAs(asset);
        });
        verify(storage, never()).delete(any(), anyString());
    }

    @Test
    void secondVersionedReplaceIncrementsAgain() {
        Asset asset = existingAsset(true);

        service.replaceFile(asset.getId(), file("b.png", "2"));
        AssetInstanceDto result = service.replaceFile(asset.getId(), file("c.png", "3"));

        assertThat(result.currentVersion()).isEqualTo(3);
        assertThat(asset.getVersions()).extracting(AssetVersion::getVersionNumber).containsExactlyInAnyOrder(1, 2);
        assertThat(stored).containsExactly(dir(asset) + "/version_2.png=2", dir(asset) + "/version_3.png=3");
    }

    @Test
    void replaceFileWithoutVersioningOverwrites() {
        Asset asset = existingAsset(false);

        AssetInstanceDto result = service.replaceFile(asset.getId(), file("photo.png", "new"));

        assertThat(result.currentVersion()).isEqualTo(1);
        assertThat(asset.getVersions()).isEmpty();
        assertThat(stored).containsExactly(dir(asset) + "/original.png=new");
        verify(storage, never()).delete(any(), anyString());
    }

    @Test
    void replaceFileWithoutVersioningDeletesOldFileWhenExtensionChanges() {
        Asset asset = existingAsset(false);
        String old = asset.getStoragePath();

        service.replaceFile(asset.getId(), file("photo.jpg", "new"));

        assertThat(asset.getStoragePath()).isEqualTo(dir(asset) + "/original.jpg");
        assertThat(asset.getMediaType()).isEqualTo(MediaType.IMAGE);
        verify(storage).delete(catalog.getWorkspace().getId(), old);
    }

    @Test
    void updateChangesOnlySuppliedFields() {
        Asset asset = existingAsset(false);
        asset.getCustomMetadata().put("k", "v");

        AssetInstanceDto result = service.update(asset.getId(), AssetInstanceDto.request(null, true, null, null, null));

        assertThat(result.versioningEnabled()).isTrue();
        assertThat(result.customMetadata()).containsEntry("k", "v");
        assertThat(stored).isEmpty();
    }

    @Test
    void updateCannotMoveCatalog() {
        Asset asset = existingAsset(false);

        assertThatThrownBy(() -> service.update(asset.getId(),
                AssetInstanceDto.request(UUID.randomUUID(), null, null, null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void deleteRemovesDependentsAndFiles() {
        Asset asset = existingAsset(false);

        service.delete(asset.getId());

        verify(lightboxItemRepository).deleteByAssetId(asset.getId());
        verify(interactionRepository).deleteByAssetId(asset.getId());
        verify(downloadLogRepository).deleteByAssetId(asset.getId());
        verify(assetRepository).delete(asset);
        verify(storage).deleteDirectory(catalog.getWorkspace().getId(), dir(asset));
    }

    @Test
    void versionsOfUnknownAssetIsNotFound() {
        UUID id = UUID.randomUUID();
        when(assetRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.versions(id)).isInstanceOf(ResourceNotFoundException.class);
    }
}
