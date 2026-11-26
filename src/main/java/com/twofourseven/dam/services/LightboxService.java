package com.twofourseven.dam.services;

import com.twofourseven.dam.entities.core.Asset;
import com.twofourseven.dam.entities.core.Catalog;
import com.twofourseven.dam.entities.core.Lightbox;
import com.twofourseven.dam.entities.core.LightboxItem;
import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.ResourceNotFoundException;
import com.twofourseven.dam.repositories.AssetRepository;
import com.twofourseven.dam.repositories.CatalogRepository;
import com.twofourseven.dam.repositories.LightboxItemRepository;
import com.twofourseven.dam.repositories.LightboxRepository;
import com.twofourseven.dam.services.AssetDownloadService.DownloadBundle;
import com.twofourseven.dam.vo.AssetPageVO;
import com.twofourseven.dam.vo.LightboxInstanceDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lightboxes: CRUD, plus the assets inside them and their downloads (spec 009). Items keep a
 * 0-based {@code sortOrder}; new ones go after the last. Until user accounts exist (spec 006) the owner's {@code userId} is supplied by the client.
 */
@Service
public class LightboxService {

    private static final Logger log = LoggerFactory.getLogger(LightboxService.class);

    /** Lightbox order: sortOrder, then when the item was added. */
    private static final Comparator<LightboxItem> ITEM_ORDER = Comparator
            .comparing((LightboxItem i) -> i.getSortOrder() == null ? Integer.MAX_VALUE : i.getSortOrder())
            .thenComparing(LightboxItem::getAddedAt, Comparator.nullsLast(Comparator.naturalOrder()));

    private final LightboxRepository lightboxRepository;
    private final CatalogRepository catalogRepository;
    private final AssetRepository assetRepository;
    private final LightboxItemRepository itemRepository;
    private final AssetDownloadService downloadService;

    public LightboxService(LightboxRepository lightboxRepository, CatalogRepository catalogRepository,
                           AssetRepository assetRepository, LightboxItemRepository itemRepository,
                           AssetDownloadService downloadService) {
        this.lightboxRepository = lightboxRepository;
        this.catalogRepository = catalogRepository;
        this.assetRepository = assetRepository;
        this.itemRepository = itemRepository;
        this.downloadService = downloadService;
    }

    /** Lightboxes, optionally filtered by catalog and/or owner. */
    @Transactional(readOnly = true)
    public List<LightboxInstanceDto> list(UUID catalogId, UUID userId) {
        List<Lightbox> lightboxes;
        if (catalogId != null && userId != null) {
            lightboxes = lightboxRepository.findByCatalogIdAndUserIdOrderByTitleAsc(catalogId, userId);
        } else if (catalogId != null) {
            lightboxes = lightboxRepository.findByCatalogIdOrderByTitleAsc(catalogId);
        } else if (userId != null) {
            lightboxes = lightboxRepository.findByUserIdOrderByTitleAsc(userId);
        } else {
            lightboxes = lightboxRepository.findAllByOrderByTitleAsc();
        }
        return lightboxes.stream().map(LightboxService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public LightboxInstanceDto get(UUID id) {
        return toDto(find(id));
    }

    @Transactional
    public LightboxInstanceDto create(LightboxInstanceDto dto) {
        if (dto.catalogId() == null) {
            throw new BadRequestException("catalogId is required");
        }
        if (dto.userId() == null) {
            throw new BadRequestException("userId is required");
        }
        Catalog catalog = catalogRepository.findById(dto.catalogId())
                .orElseThrow(() -> new ResourceNotFoundException("Catalog " + dto.catalogId() + " not found"));
        Lightbox lightbox = new Lightbox();
        lightbox.setCatalog(catalog);
        lightbox.setUserId(dto.userId());
        apply(lightbox, dto);
        if (dto.assetIds() != null && !dto.assetIds().isEmpty()) {
            append(lightbox, findAssets(catalog.getId(), dto.assetIds()));
        }
        Lightbox saved = lightboxRepository.save(lightbox);
        log.info("Created lightbox '{}' ({}) in catalog {} with {} item(s)", saved.getTitle(), saved.getId(),
                catalog.getId(), saved.getItems().size());
        return toDto(saved);
    }

    @Transactional
    public LightboxInstanceDto update(UUID id, LightboxInstanceDto dto) {
        Lightbox lightbox = find(id);
        if (dto.catalogId() != null && !dto.catalogId().equals(lightbox.getCatalog().getId())) {
            throw new BadRequestException("A lightbox cannot be moved to another catalog");
        }
        if (dto.userId() != null && !dto.userId().equals(lightbox.getUserId())) {
            throw new BadRequestException("A lightbox's owner cannot be changed");
        }
        apply(lightbox, dto);
        return toDto(lightboxRepository.save(lightbox));
    }

    /** Deletes the lightbox and its items; the assets themselves are untouched. */
    @Transactional
    public void delete(UUID id) {
        Lightbox lightbox = find(id);
        lightboxRepository.delete(lightbox);
        log.info("Deleted lightbox '{}' ({})", lightbox.getTitle(), id);
    }

    /**
     * One page of the lightbox's assets, in lightbox order. Paging as in {@code GET /assets/search}.
     *
     * @throws BadRequestException for bad paging
     */
    @Transactional(readOnly = true)
    public AssetPageVO items(UUID id, Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? AssetBrowseService.DEFAULT_PAGE_SIZE : size;
        if (p < 0) {
            throw new BadRequestException("page must be 0 or more");
        }
        if (s < 1 || s > AssetBrowseService.MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + AssetBrowseService.MAX_PAGE_SIZE);
        }
        find(id);
        Page<Asset> result = itemRepository.findAssetsByLightboxId(id, PageRequest.of(p, s));
        return new AssetPageVO(result.getContent().stream().map(AssetService::toDto).toList(), p, s,
                result.getTotalElements(), result.getTotalPages());
    }

    /**
     * Appends the assets after the last item, skipping ones already in the lightbox.
     *
     * @throws ResourceNotFoundException for an unknown lightbox or asset
     * @throws BadRequestException       for an asset from another catalog, or no ids
     */
    @Transactional
    public LightboxInstanceDto addItems(UUID id, List<UUID> assetIds) {
        requireIds(assetIds);
        Lightbox lightbox = find(id);
        int added = append(lightbox, findAssets(lightbox.getCatalog().getId(), assetIds));
        log.debug("Added {} item(s) to lightbox {}", added, id);
        return toDto(lightboxRepository.save(lightbox));
    }

    /** Removes one asset from the lightbox; 404 if it isn't in it. The asset itself is untouched. */
    @Transactional
    public void removeItem(UUID id, UUID assetId) {
        Lightbox lightbox = find(id);
        if (!lightbox.getItems().removeIf(i -> i.getAsset().getId().equals(assetId))) {
            throw new ResourceNotFoundException("Asset " + assetId + " is not in lightbox " + id);
        }
        renumber(lightbox);
        lightboxRepository.save(lightbox);
    }

    /** Removes the assets, ignoring ids that aren't in the lightbox. */
    @Transactional
    public LightboxInstanceDto removeItems(UUID id, List<UUID> assetIds) {
        requireIds(assetIds);
        Lightbox lightbox = find(id);
        Set<UUID> remove = new HashSet<>(assetIds);
        if (lightbox.getItems().removeIf(i -> remove.contains(i.getAsset().getId()))) {
            renumber(lightbox);
        }
        return toDto(lightboxRepository.save(lightbox));
    }

    /**
     * Sets the item order. The list must hold exactly the lightbox's assets, each once.
     *
     * @throws BadRequestException otherwise
     */
    @Transactional
    public LightboxInstanceDto reorder(UUID id, List<UUID> assetIds) {
        if (assetIds == null) {
            throw new BadRequestException("assetIds is required");
        }
        Lightbox lightbox = find(id);
        Map<UUID, LightboxItem> byAsset = lightbox.getItems().stream()
                .collect(Collectors.toMap(i -> i.getAsset().getId(), Function.identity()));
        if (assetIds.size() != byAsset.size() || !byAsset.keySet().equals(new HashSet<>(assetIds))) {
            throw new BadRequestException("assetIds must list each of the lightbox's " + byAsset.size() + " asset(s) exactly once");
        }
        for (int i = 0; i < assetIds.size(); i++) {
            byAsset.get(assetIds.get(i)).setSortOrder(i);
        }
        return toDto(lightboxRepository.save(lightbox));
    }

    /** The lightbox's items as a zip named after its title. */
    @Transactional(readOnly = true)
    public DownloadBundle download(UUID id) {
        Lightbox lightbox = find(id);
        List<Asset> assets = ordered(lightbox).stream().map(LightboxItem::getAsset).toList();
        if (assets.isEmpty()) {
            throw new BadRequestException("Lightbox " + id + " has no items to download");
        }
        return downloadService.bundle(assets, Strings.slugify(lightbox.getTitle()));
    }

    /** Assets by id, in the order given, duplicates dropped; all must be in the catalog. */
    private List<Asset> findAssets(UUID catalogId, List<UUID> assetIds) {
        List<UUID> ids = new ArrayList<>(new LinkedHashSet<>(assetIds));
        if (ids.contains(null)) {
            throw new BadRequestException("assetIds must not contain null");
        }
        Map<UUID, Asset> found = assetRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Asset::getId, Function.identity()));
        List<Asset> assets = new ArrayList<>(ids.size());
        for (UUID assetId : ids) {
            Asset asset = found.get(assetId);
            if (asset == null) {
                throw new ResourceNotFoundException("Asset " + assetId + " not found");
            }
            if (!asset.getCatalog().getId().equals(catalogId)) {
                throw new BadRequestException("Asset " + assetId + " is not in catalog " + catalogId);
            }
            assets.add(asset);
        }
        return assets;
    }

    /** Adds the assets not already present after the last item; returns how many were added. */
    private static int append(Lightbox lightbox, List<Asset> assets) {
        Set<UUID> present = lightbox.getItems().stream().map(i -> i.getAsset().getId()).collect(Collectors.toSet());
        int next = lightbox.getItems().stream()
                .mapToInt(i -> i.getSortOrder() == null ? -1 : i.getSortOrder()).max().orElse(-1) + 1;
        LocalDateTime now = LocalDateTime.now();
        int added = 0;
        for (Asset asset : assets) {
            if (present.add(asset.getId())) {
                LightboxItem item = new LightboxItem();
                item.setLightbox(lightbox);
                item.setAsset(asset);
                item.setSortOrder(next++);
                item.setAddedAt(now);
                lightbox.getItems().add(item);
                added++;
            }
        }
        return added;
    }

    /** Rewrites sortOrder as 0..n-1 in the current order. */
    private static void renumber(Lightbox lightbox) {
        List<LightboxItem> items = ordered(lightbox);
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setSortOrder(i);
        }
    }

    private static List<LightboxItem> ordered(Lightbox lightbox) {
        return lightbox.getItems().stream().sorted(ITEM_ORDER).toList();
    }

    private static void requireIds(List<UUID> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            throw new BadRequestException("assetIds must not be empty");
        }
    }

    private static void apply(Lightbox lightbox, LightboxInstanceDto dto) {
        lightbox.setTitle(Strings.trim(dto.title()));
        lightbox.setDescription(Strings.trim(dto.description()));
        lightbox.setIsShared(Boolean.TRUE.equals(dto.shared()));
    }

    private Lightbox find(UUID id) {
        return lightboxRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lightbox " + id + " not found"));
    }

    static LightboxInstanceDto toDto(Lightbox l) {
        return new LightboxInstanceDto(l.getId(), l.getCatalog().getId(), l.getUserId(), l.getTitle(),
                l.getDescription(), l.getIsShared(), l.getItems().size(), null);
    }
}
