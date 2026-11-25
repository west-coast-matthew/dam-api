package com.twofourseven.dam.services;

import com.twofourseven.dam.entities.core.Asset;
import com.twofourseven.dam.entities.core.Category;
import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.ResourceNotFoundException;
import com.twofourseven.dam.repositories.AssetRepository;
import com.twofourseven.dam.repositories.AssetSpecifications;
import com.twofourseven.dam.repositories.CatalogRepository;
import com.twofourseven.dam.repositories.CategoryRepository;
import com.twofourseven.dam.vo.AssetPageVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Browsing and searching assets (spec 022): a paged, filtered listing, plus reading an asset's file
 * and thumbnail so the UI can show previews.
 */
@Service
public class AssetBrowseService {

    public static final int DEFAULT_PAGE_SIZE = 24;
    public static final int MAX_PAGE_SIZE = 200;

    /**
     * What the listing can be sorted by: {@code added} (date added), {@code name} (file name),
     * {@code size} (file size) or {@code type} (media type, then file name). Ties are broken by id
     * so pages are stable.
     */
    public enum SortField {
        ADDED("createdAt", Sort.Direction.DESC),
        NAME("originalFilename", Sort.Direction.ASC),
        SIZE("fileSize", Sort.Direction.DESC),
        TYPE("mediaType", Sort.Direction.ASC);

        private final String property;
        /** Used when no direction is given: newest and largest first, names and types A–Z. */
        private final Sort.Direction defaultDirection;

        SortField(String property, Sort.Direction defaultDirection) {
            this.property = property;
            this.defaultDirection = defaultDirection;
        }
    }

    /**
     * @param sort      added (default), name, size or type; {@code newest} and {@code oldest} still
     *                  mean added descending and ascending
     * @param direction asc or desc; null uses the field's default
     */
    static Sort parseSort(String sort, String direction) {
        String value = sort == null || sort.isBlank() ? "added" : sort.strip().toLowerCase(Locale.ROOT);
        Sort.Direction implied = null;
        if (value.equals("newest") || value.equals("oldest")) {
            implied = value.equals("newest") ? Sort.Direction.DESC : Sort.Direction.ASC;
            value = "added";
        }
        SortField field;
        try {
            field = SortField.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid sort '" + sort + "'; use added, name, size or type");
        }
        Sort.Direction dir;
        if (direction == null || direction.isBlank()) {
            dir = implied != null ? implied : field.defaultDirection;
        } else {
            dir = switch (direction.strip().toLowerCase(Locale.ROOT)) {
                case "asc" -> Sort.Direction.ASC;
                case "desc" -> Sort.Direction.DESC;
                default -> throw new BadRequestException("Invalid direction '" + direction + "'; use asc or desc");
            };
        }
        Sort order = Sort.by(new Sort.Order(dir, field.property));
        if (field == SortField.TYPE) {
            order = order.and(Sort.by(Sort.Order.asc("originalFilename")));
        }
        return order.and(Sort.by(Sort.Order.asc("id")));
    }

    /**
     * Filters for {@link #search}. Only {@code catalogId} is required.
     *
     * @param includeSubcategories with {@code categoryId}: also assets in its subcategories
     * @param addedFrom            first day included (server-local date)
     * @param addedTo              last day included
     * @param tags                 tag names, ignoring case
     * @param matchAllTags         true: every tag must be present; false: any of them
     * @param text                 part of the file name, ignoring case
     * @param page                 0-based; null means 0
     * @param size                 null means {@value #DEFAULT_PAGE_SIZE}
     * @param sort                 added (default), name, size or type
     * @param direction            asc or desc; null uses the sort's default
     */
    public record SearchQuery(UUID catalogId, UUID categoryId, boolean includeSubcategories, LocalDate addedFrom,
                              LocalDate addedTo, List<String> tags, boolean matchAllTags, String text, Integer page,
                              Integer size, String sort, String direction) {
    }

    /** An asset file opened for reading; the caller closes {@code content}. */
    public record AssetContent(InputStream content, String mimeType, String filename, Long size) {
    }

    private final AssetRepository assetRepository;
    private final CatalogRepository catalogRepository;
    private final CategoryRepository categoryRepository;
    private final AssetStorageService storage;

    public AssetBrowseService(AssetRepository assetRepository, CatalogRepository catalogRepository,
                              CategoryRepository categoryRepository, AssetStorageService storage) {
        this.assetRepository = assetRepository;
        this.catalogRepository = catalogRepository;
        this.categoryRepository = categoryRepository;
        this.storage = storage;
    }

    /**
     * @throws BadRequestException       for a missing catalog id, bad paging, dates or sort, or a
     *                                   category from another catalog
     * @throws ResourceNotFoundException if the catalog or category doesn't exist
     */
    @Transactional(readOnly = true)
    public AssetPageVO search(SearchQuery q) {
        if (q.catalogId() == null) {
            throw new BadRequestException("catalogId is required");
        }
        int page = q.page() == null ? 0 : q.page();
        int size = q.size() == null ? DEFAULT_PAGE_SIZE : q.size();
        if (page < 0) {
            throw new BadRequestException("page must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (q.addedFrom() != null && q.addedTo() != null && q.addedTo().isBefore(q.addedFrom())) {
            throw new BadRequestException("addedTo must not be before addedFrom");
        }
        Sort sort = parseSort(q.sort(), q.direction());
        if (!catalogRepository.existsById(q.catalogId())) {
            throw new ResourceNotFoundException("Catalog " + q.catalogId() + " not found");
        }

        Specification<Asset> spec = AssetSpecifications.inCatalog(q.catalogId());
        if (q.categoryId() != null) {
            spec = spec.and(AssetSpecifications.inAnyCategory(categoryScope(q.catalogId(), q.categoryId(),
                    q.includeSubcategories())));
        }
        if (q.addedFrom() != null) {
            spec = spec.and(AssetSpecifications.createdFrom(q.addedFrom().atStartOfDay()));
        }
        if (q.addedTo() != null) {
            spec = spec.and(AssetSpecifications.createdBefore(q.addedTo().plusDays(1).atStartOfDay()));
        }
        List<String> tags = cleanTags(q.tags());
        if (!tags.isEmpty()) {
            if (q.matchAllTags()) {
                for (String tag : tags) {
                    spec = spec.and(AssetSpecifications.hasTag(tag));
                }
            } else {
                spec = spec.and(AssetSpecifications.hasAnyTag(tags));
            }
        }
        if (q.text() != null && !q.text().isBlank()) {
            spec = spec.and(AssetSpecifications.filenameContains(q.text().strip()));
        }

        Page<Asset> result = assetRepository.findAll(spec, PageRequest.of(page, size, sort));
        return new AssetPageVO(result.getContent().stream().map(AssetService::toDto).toList(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    /** The original file, for previews and downloads. */
    @Transactional(readOnly = true)
    public AssetContent file(UUID id) {
        Asset asset = find(id);
        return new AssetContent(open(asset, asset.getStoragePath(), "file"), asset.getMimeType(),
                asset.getOriginalFilename(), asset.getFileSize());
    }

    /** The JPEG thumbnail; 404 while it is still being made, or when the file type has none. */
    @Transactional(readOnly = true)
    public AssetContent thumbnail(UUID id) {
        Asset asset = find(id);
        String path = AssetStorageService.thumbnailPath(asset.getCatalog().getId(), asset.getId());
        String name = asset.getOriginalFilename().replaceFirst("\\.[^.]+$", "") + "-thumbnail.jpg";
        return new AssetContent(open(asset, path, "thumbnail"), "image/jpeg", name, null);
    }

    private InputStream open(Asset asset, String path, String what) {
        try {
            return storage.open(asset.getCatalog().getWorkspace().getId(), path);
        } catch (UncheckedIOException e) {
            throw new ResourceNotFoundException("Asset " + asset.getId() + " has no " + what);
        }
    }

    private Asset find(UUID id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset " + id + " not found"));
    }

    /** The category, plus its descendants when asked. */
    private Set<UUID> categoryScope(UUID catalogId, UUID categoryId, boolean includeSubcategories) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category " + categoryId + " not found"));
        if (!category.getCatalog().getId().equals(catalogId)) {
            throw new BadRequestException("Category " + categoryId + " is not in catalog " + catalogId);
        }
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(categoryId);
        if (!includeSubcategories) {
            return ids;
        }
        Map<UUID, List<UUID>> children = new HashMap<>();
        for (Category c : categoryRepository.findByCatalogIdOrderByPositionAscNameAsc(catalogId)) {
            if (c.getParent() != null) {
                children.computeIfAbsent(c.getParent().getId(), k -> new ArrayList<>()).add(c.getId());
            }
        }
        Deque<UUID> todo = new ArrayDeque<>(List.of(categoryId));
        while (!todo.isEmpty()) {
            for (UUID child : children.getOrDefault(todo.pop(), List.of())) {
                if (ids.add(child)) {
                    todo.push(child);
                }
            }
        }
        return ids;
    }

    /** Tags may come as repeated params or comma-separated; blanks and duplicates are dropped. */
    static List<String> cleanTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String raw : tags) {
            if (raw == null) {
                continue;
            }
            for (String part : raw.split(",")) {
                String tag = part.strip();
                if (!tag.isEmpty() && seen.add(tag.toLowerCase(Locale.ROOT))) {
                    out.add(tag);
                }
            }
        }
        return out;
    }
}
