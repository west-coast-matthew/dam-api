package com.twofourseven.dam.services;

import com.twofourseven.dam.entities.core.Asset;
import com.twofourseven.dam.entities.core.AssetStatus;
import com.twofourseven.dam.entities.core.AssetVersion;
import com.twofourseven.dam.entities.core.Catalog;
import com.twofourseven.dam.entities.core.Category;
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
import com.twofourseven.dam.vo.AssetVersionVO;
import com.twofourseven.dam.vo.BulkUploadResultVO;
import com.twofourseven.dam.vo.SkippedFileVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages assets: upload, metadata updates, file replacement (with optional versioning) and delete.
 *
 * <p>Categories: every asset has at least one. An asset created without categories, or updated with
 * an empty list, is put in its catalog's default category.
 *
 * <p>Versioning: when an asset's {@code versioningEnabled} flag is on, replacing its file keeps the
 * previous file on disk and records it as an {@link AssetVersion}; the asset keeps its id and points
 * at the new file. When the flag is off, the file is overwritten.
 */
@Service
public class AssetService {

    private static final Logger log = LoggerFactory.getLogger(AssetService.class);

    private final AssetRepository assetRepository;
    private final AssetVersionRepository assetVersionRepository;
    private final CatalogRepository catalogRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final LightboxItemRepository lightboxItemRepository;
    private final UserAssetInteractionRepository interactionRepository;
    private final AssetDownloadLogRepository downloadLogRepository;
    private final AssetStorageService storage;
    private final UploadedMediaReader uploadReader;
    private final CategoryService categoryService;

    public AssetService(AssetRepository assetRepository, AssetVersionRepository assetVersionRepository,
                        CatalogRepository catalogRepository, CategoryRepository categoryRepository,
                        TagRepository tagRepository, LightboxItemRepository lightboxItemRepository,
                        UserAssetInteractionRepository interactionRepository,
                        AssetDownloadLogRepository downloadLogRepository,
                        AssetStorageService storage, UploadedMediaReader uploadReader,
                        CategoryService categoryService) {
        this.assetRepository = assetRepository;
        this.assetVersionRepository = assetVersionRepository;
        this.catalogRepository = catalogRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.lightboxItemRepository = lightboxItemRepository;
        this.interactionRepository = interactionRepository;
        this.downloadLogRepository = downloadLogRepository;
        this.storage = storage;
        this.uploadReader = uploadReader;
        this.categoryService = categoryService;
    }

    /** All assets, or only those in {@code catalogId} when it is not null. Newest first. */
    @Transactional(readOnly = true)
    public List<AssetInstanceDto> list(UUID catalogId) {
        List<Asset> assets = catalogId == null
                ? assetRepository.findAllByOrderByCreatedAtDesc()
                : assetRepository.findByCatalogIdOrderByCreatedAtDesc(catalogId);
        return assets.stream().map(AssetService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AssetInstanceDto get(UUID id) {
        return toDto(find(id));
    }

    @Transactional(readOnly = true)
    public List<AssetVersionVO> versions(UUID id) {
        find(id);
        return assetVersionRepository.findByAssetIdOrderByVersionNumberAsc(id).stream()
                .map(v -> new AssetVersionVO(v.getId(), id, v.getVersionNumber(), v.getFileSize(), v.getCreatedAt()))
                .toList();
    }

    /**
     * Uploads a new asset. The media type comes from the file extension; a zip must hold exactly
     * one media file, which becomes the asset.
     */
    @Transactional
    public AssetInstanceDto create(AssetInstanceDto dto, MultipartFile file) {
        Catalog catalog = requireCatalog(dto);
        Asset asset = new Asset();
        asset.setCatalog(catalog);
        applyMetadata(asset, dto);

        try (UploadedMedia media = uploadReader.read(file)) {
            storeOriginal(asset, media);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        ensureCategory(asset);
        Asset saved = assetRepository.save(asset);
        log.info("Created {} asset '{}' ({}) in catalog {}", saved.getMediaType(), saved.getOriginalFilename(),
                saved.getId(), catalog.getId());
        return toDto(saved);
    }

    /**
     * Bulk upload: creates one asset per supported media file in a zip. The writable fields of
     * {@code dto} (catalog, categories, tags, metadata, versioning) apply to every asset; assets get
     * the catalog's default category when no categories are given. Unsupported files are skipped and
     * reported. All assets are saved in one transaction, so a failure creates none of them.
     */
    @Transactional
    public BulkUploadResultVO createBulk(AssetInstanceDto dto, MultipartFile zip) {
        Catalog catalog = requireCatalog(dto);
        // Resolve shared metadata once rather than per file
        Set<Category> categories = dto.categoryIds() == null || dto.categoryIds().isEmpty()
                ? Set.of(categoryService.defaultCategoryOf(catalog))
                : resolveCategories(catalog, dto.categoryIds());
        Set<Tag> tags = dto.tags() == null ? Set.of() : resolveTags(catalog, dto.tags());

        List<AssetInstanceDto> created = new ArrayList<>();
        List<SkippedFileVO> skipped = uploadReader.readEach(zip, media -> {
            Asset asset = new Asset();
            asset.setCatalog(catalog);
            asset.setVersioningEnabled(Boolean.TRUE.equals(dto.versioningEnabled()));
            if (dto.customMetadata() != null) {
                asset.setCustomMetadata(new HashMap<>(dto.customMetadata()));
            }
            asset.setCategories(new HashSet<>(categories));
            asset.setTags(new HashSet<>(tags));
            storeOriginal(asset, media);
            created.add(toDto(assetRepository.save(asset)));
        });
        log.info("Bulk upload created {} assets in catalog {} ({} files skipped)", created.size(), catalog.getId(), skipped.size());
        return new BulkUploadResultVO(created, skipped);
    }

    private Catalog requireCatalog(AssetInstanceDto dto) {
        if (dto == null || dto.catalogId() == null) {
            throw new BadRequestException("catalogId is required");
        }
        return catalogRepository.findById(dto.catalogId())
                .orElseThrow(() -> new ResourceNotFoundException("Catalog " + dto.catalogId() + " not found"));
    }

    /** Writes a new asset's file as original.{ext}; removed again if the transaction rolls back. */
    private void storeOriginal(Asset asset, UploadedMedia media) {
        UUID workspaceId = asset.getCatalog().getWorkspace().getId();
        String directory = AssetStorageService.assetDirectory(asset.getCatalog().getId(), asset.getId());
        String path = directory + "/original." + media.type().extension();
        TransactionCallbacks.afterRollback(() -> storage.deleteDirectory(workspaceId, directory));
        long size = storage.store(workspaceId, path, media.content());
        setFile(asset, media, path, size);
    }

    /**
     * Updates the writable metadata. Null fields are left unchanged; the file is not touched
     * (use {@link #replaceFile}).
     */
    @Transactional
    public AssetInstanceDto update(UUID id, AssetInstanceDto dto) {
        Asset asset = find(id);
        if (dto.catalogId() != null && !dto.catalogId().equals(asset.getCatalog().getId())) {
            throw new BadRequestException("An asset cannot be moved to another catalog");
        }
        applyMetadata(asset, dto);
        ensureCategory(asset);
        return toDto(assetRepository.save(asset));
    }

    /**
     * Replaces the asset's file. With versioning enabled the previous file is kept as a new
     * {@link AssetVersion}; otherwise it is overwritten. The asset goes back to PENDING_PROCESSING.
     */
    @Transactional
    public AssetInstanceDto replaceFile(UUID id, MultipartFile file) {
        Asset asset = find(id);
        UUID workspaceId = asset.getCatalog().getWorkspace().getId();
        String directory = AssetStorageService.assetDirectory(asset.getCatalog().getId(), asset.getId());
        String oldPath = asset.getStoragePath();

        try (UploadedMedia media = uploadReader.read(file)) {
            String ext = media.type().extension();
            String newPath;
            if (Boolean.TRUE.equals(asset.getVersioningEnabled())) {
                int current = currentVersion(asset);
                AssetVersion previous = new AssetVersion();
                previous.setAsset(asset);
                previous.setVersionNumber(current);
                previous.setStoragePath(oldPath);
                previous.setFileSize(asset.getFileSize());
                asset.getVersions().add(previous);
                newPath = directory + "/version_" + (current + 1) + "." + ext;
                log.info("Asset {}: keeping version {} and storing version {}", id, current, current + 1);
            } else {
                newPath = directory + "/" + stem(oldPath) + "." + ext;
            }
            if (!newPath.equals(oldPath)) {
                TransactionCallbacks.afterRollback(() -> storage.delete(workspaceId, newPath));
            }
            long size = storage.store(workspaceId, newPath, media.content());
            if (!asset.getVersioningEnabled() && !newPath.equals(oldPath)) {
                TransactionCallbacks.afterCommit(() -> storage.delete(workspaceId, oldPath));
            }
            setFile(asset, media, newPath, size);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ensureCategory(asset);
        return toDto(assetRepository.save(asset));
    }

    /**
     * Deletes the asset, its versions, its lightbox entries, per-user interactions and download
     * log rows, then its files once the transaction commits.
     */
    @Transactional
    public void delete(UUID id) {
        Asset asset = find(id);
        UUID workspaceId = asset.getCatalog().getWorkspace().getId();
        String directory = AssetStorageService.assetDirectory(asset.getCatalog().getId(), asset.getId());

        lightboxItemRepository.deleteByAssetId(id);
        interactionRepository.deleteByAssetId(id);
        downloadLogRepository.deleteByAssetId(id);
        assetRepository.delete(asset);

        TransactionCallbacks.afterCommit(() -> storage.deleteDirectory(workspaceId, directory));
        log.info("Deleted asset '{}' ({})", asset.getOriginalFilename(), id);
    }

    private void applyMetadata(Asset asset, AssetInstanceDto dto) {
        if (dto.versioningEnabled() != null) {
            asset.setVersioningEnabled(dto.versioningEnabled());
        }
        if (dto.customMetadata() != null) {
            asset.setCustomMetadata(new HashMap<>(dto.customMetadata()));
        }
        if (dto.categoryIds() != null) {
            asset.setCategories(resolveCategories(asset.getCatalog(), dto.categoryIds()));
        }
        if (dto.tags() != null) {
            asset.setTags(resolveTags(asset.getCatalog(), dto.tags()));
        }
    }

    /** Every asset needs a category: one without any goes in its catalog's default category. */
    private void ensureCategory(Asset asset) {
        if (asset.getCategories().isEmpty()) {
            asset.getCategories().add(categoryService.defaultCategoryOf(asset.getCatalog()));
        }
    }

    private Set<Category> resolveCategories(Catalog catalog, List<UUID> ids) {
        Set<UUID> wanted = new LinkedHashSet<>(ids);
        wanted.remove(null);
        List<Category> found = categoryRepository.findAllById(wanted);
        if (found.size() != wanted.size()) {
            Set<UUID> missing = new LinkedHashSet<>(wanted);
            found.forEach(c -> missing.remove(c.getId()));
            throw new BadRequestException("Unknown category ids: " + missing);
        }
        for (Category category : found) {
            if (!category.getCatalog().getId().equals(catalog.getId())) {
                throw new BadRequestException("Category '" + category.getName() + "' belongs to a different catalog");
            }
        }
        return new HashSet<>(found);
    }

    /** Finds tags by name (case-insensitive) in the catalog, creating any that don't exist. */
    private Set<Tag> resolveTags(Catalog catalog, List<String> names) {
        Map<String, String> unique = new LinkedHashMap<>();
        for (String name : names) {
            String trimmed = Strings.trim(name);
            if (trimmed != null && !trimmed.isEmpty()) {
                if (trimmed.length() > 255) {
                    throw new BadRequestException("Tag names must be at most 255 characters");
                }
                unique.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
            }
        }
        Set<Tag> tags = new HashSet<>();
        for (String name : unique.values()) {
            tags.add(tagRepository.findFirstByCatalogIdAndNameIgnoreCase(catalog.getId(), name).orElseGet(() -> {
                Tag tag = new Tag();
                tag.setCatalog(catalog);
                tag.setName(name);
                return tagRepository.save(tag);
            }));
        }
        return tags;
    }

    private static void setFile(Asset asset, UploadedMedia media, String path, long size) {
        asset.setOriginalFilename(media.filename());
        asset.setMediaType(media.type().mediaType());
        asset.setMimeType(media.type().mimeType());
        asset.setStoragePath(path);
        asset.setFileSize(size);
        // Thumbnails etc. are regenerated for the new file
        asset.setStatus(AssetStatus.PENDING_PROCESSING);
    }

    /** "…/version_3.png" -> "version_3" */
    private static String stem(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static int currentVersion(Asset asset) {
        return asset.getVersions().stream().mapToInt(AssetVersion::getVersionNumber).max().orElse(0) + 1;
    }

    private Asset find(UUID id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset " + id + " not found"));
    }

    static AssetInstanceDto toDto(Asset a) {
        Catalog catalog = a.getCatalog();
        List<UUID> categoryIds = a.getCategories().stream().map(Category::getId).sorted().toList();
        List<String> tags = a.getTags().stream().map(Tag::getName).sorted(Comparator.comparing(s -> s.toLowerCase(Locale.ROOT))).toList();
        return new AssetInstanceDto(a.getId(), catalog.getId(), catalog.getName(), catalog.getWorkspace().getId(),
                a.getOriginalFilename(), a.getMediaType(), a.getMimeType(), a.getFileSize(), a.getStatus(),
                a.getVersioningEnabled(), currentVersion(a), a.getCustomMetadata(), categoryIds, tags, a.getCreatedAt());
    }
}
