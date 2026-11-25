package com.twofourseven.dam.services;

import com.twofourseven.dam.entities.core.Category;
import com.twofourseven.dam.entities.core.MediaType;
import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.ResourceNotFoundException;
import com.twofourseven.dam.repositories.AssetActivityRepository;
import com.twofourseven.dam.repositories.AssetActivityRepository.CategoryOperation;
import com.twofourseven.dam.repositories.AssetActivityRepository.Totals;
import com.twofourseven.dam.repositories.AssetActivityRepository.UploadOperation;
import com.twofourseven.dam.repositories.CatalogRepository;
import com.twofourseven.dam.repositories.CategoryRepository;
import com.twofourseven.dam.vo.AssetUploadActivityVO;
import com.twofourseven.dam.vo.AssetUploadActivityVO.RecentAsset;
import com.twofourseven.dam.vo.AssetUploadActivityVO.SubCategory;
import com.twofourseven.dam.vo.AssetUploadActivityVO.UploadAttempt;
import com.twofourseven.dam.vo.AssetUploadSummaryVO;
import com.twofourseven.dam.vo.AssetUploadSummaryVO.CategoryActivity;
import com.twofourseven.dam.vo.AssetUploadSummaryVO.UploadEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Recent upload activity for a category (spec 020): upload operations, totals, the newest assets and
 * per-subcategory counts in a time window. A category's numbers include its subcategories.
 */
@Service
public class AssetActivityService {

    public static final Duration DEFAULT_PERIOD = Duration.ofHours(24);
    public static final Duration MAX_PERIOD = Duration.ofDays(366);
    public static final int DEFAULT_RECENT_LIMIT = 10;
    public static final int MAX_RECENT_LIMIT = 100;

    private final AssetActivityRepository activityRepository;
    private final CategoryRepository categoryRepository;
    private final CatalogRepository catalogRepository;
    private final Clock clock;

    @Autowired
    public AssetActivityService(AssetActivityRepository activityRepository, CategoryRepository categoryRepository,
                                CatalogRepository catalogRepository) {
        this(activityRepository, categoryRepository, catalogRepository, Clock.systemDefaultZone());
    }

    /** {@code clock} must use the zone asset {@code createdAt} values are written in (the system zone). */
    AssetActivityService(AssetActivityRepository activityRepository, CategoryRepository categoryRepository,
                         CatalogRepository catalogRepository, Clock clock) {
        this.activityRepository = activityRepository;
        this.categoryRepository = categoryRepository;
        this.catalogRepository = catalogRepository;
        this.clock = clock;
    }

    /**
     * @param period how far back to look, e.g. {@code 5m}, {@code 15m}, {@code 1h}, {@code 8h},
     *               {@code 24h}, {@code 7d} or ISO-8601 ({@code PT15M}); null means 24 hours
     * @param limit  how many recent assets to return; null means {@value #DEFAULT_RECENT_LIMIT}
     * @throws ResourceNotFoundException if the category doesn't exist
     * @throws BadRequestException       if the period or limit is invalid
     */
    @Transactional(readOnly = true)
    public AssetUploadActivityVO activity(UUID categoryId, String period, Integer limit) {
        if (categoryId == null) {
            throw new BadRequestException("categoryId is required");
        }
        Duration window = parsePeriod(period);
        int recentLimit = recentLimit(limit);
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category " + categoryId + " not found"));

        LocalDateTime to = LocalDateTime.now(clock);
        LocalDateTime from = to.minus(window);

        List<UploadAttempt> attempts = activityRepository.uploadOperations(categoryId, from).stream()
                .map(op -> new UploadAttempt(op.uploadedAt(), op.assetCount(), fileType(op)))
                .toList();
        long totalAssets = attempts.stream().mapToLong(UploadAttempt::uploadCount).sum();
        List<RecentAsset> recent = activityRepository.recentAssets(categoryId, from, recentLimit).stream()
                .map(a -> new RecentAsset(a.id(), a.originalFilename(), fileType(a.mediaType()), a.createdAt()))
                .toList();
        List<SubCategory> subCategories = activityRepository.subCategoryCounts(categoryId, from).stream()
                .map(s -> new SubCategory(s.id(), s.name(), s.newAssetCount()))
                .toList();

        return new AssetUploadActivityVO(window.toMillis(), from, to, attempts.size(), totalAssets,
                category.getId(), category.getName(), attempts, recent, subCategories);
    }

    /**
     * Spec 021: upload activity broken down by category, for a whole catalog or one category's
     * subtree. Only categories with uploads in the window are listed, in editor order.
     *
     * @param catalogId  the catalog; optional when {@code categoryId} is given
     * @param categoryId narrows the summary to this category and its subcategories; optional
     * @param period     as in {@link #activity}; null means 24 hours
     * @throws BadRequestException       if neither id is given, they don't match, or the period is invalid
     * @throws ResourceNotFoundException if the catalog or category doesn't exist
     */
    @Transactional(readOnly = true)
    public AssetUploadSummaryVO summary(UUID catalogId, UUID categoryId, String period) {
        if (catalogId == null && categoryId == null) {
            throw new BadRequestException("catalogId or categoryId is required");
        }
        Duration window = parsePeriod(period);
        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Category " + categoryId + " not found"));
            UUID owner = category.getCatalog().getId();
            if (catalogId != null && !catalogId.equals(owner)) {
                throw new BadRequestException("Category " + categoryId + " is not in catalog " + catalogId);
            }
            catalogId = owner;
        } else if (!catalogRepository.existsById(catalogId)) {
            throw new ResourceNotFoundException("Catalog " + catalogId + " not found");
        }

        LocalDateTime to = LocalDateTime.now(clock);
        LocalDateTime from = to.minus(window);

        Map<UUID, List<CategoryOperation>> byCategory = new HashMap<>();
        for (CategoryOperation op : activityRepository.categoryOperations(catalogId, categoryId, from)) {
            byCategory.computeIfAbsent(op.categoryId(), id -> new ArrayList<>()).add(op);
        }
        Totals totals = byCategory.isEmpty() ? new Totals(0, 0) : activityRepository.totals(catalogId, categoryId, from);

        List<CategoryActivity> categories = new ArrayList<>();
        for (Category c : inEditorOrder(categoryRepository.findByCatalogIdOrderByPositionAscNameAsc(catalogId))) {
            List<CategoryOperation> ops = byCategory.get(c.getId());
            if (ops == null) {
                continue;
            }
            List<UploadEvent> events = ops.stream()
                    .map(op -> new UploadEvent(op.operationId(), op.uploadedAt(), op.assetCount(),
                            fileType(op.mediaTypeCount(), op.mediaType())))
                    .toList();
            List<Category> path = pathOf(c);
            categories.add(new CategoryActivity(c.getId(), c.getName(),
                    path.stream().map(Category::getName).toList(),
                    path.stream().map(Category::getId).toList(),
                    events.size(), events.stream().mapToLong(UploadEvent::uploadCount).sum(), events));
        }
        return new AssetUploadSummaryVO(window.toMillis(), from, to, catalogId, categoryId,
                totals.operations(), totals.assets(), categories);
    }

    /** Depth-first: each category followed by its children, siblings in the given (position) order. */
    private static List<Category> inEditorOrder(List<Category> categories) {
        Map<UUID, List<Category>> children = new LinkedHashMap<>();
        for (Category c : categories) {
            UUID parentId = c.getParent() == null ? null : c.getParent().getId();
            children.computeIfAbsent(parentId, id -> new ArrayList<>()).add(c);
        }
        List<Category> out = new ArrayList<>(categories.size());
        addSubtrees(children, null, out);
        return out;
    }

    private static void addSubtrees(Map<UUID, List<Category>> children, UUID parentId, List<Category> out) {
        for (Category c : children.getOrDefault(parentId, List.of())) {
            out.add(c);
            addSubtrees(children, c.getId(), out);
        }
    }

    /** From the top-level ancestor down to {@code category}. */
    private static List<Category> pathOf(Category category) {
        List<Category> path = new ArrayList<>();
        for (Category c = category; c != null && path.size() < 1000; c = c.getParent()) {
            path.addFirst(c);
        }
        return path;
    }

    static Duration parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            return DEFAULT_PERIOD;
        }
        Duration duration;
        try {
            duration = DurationStyle.detectAndParse(period.strip());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid period '" + period + "'; use e.g. 5m, 15m, 1h, 8h, 24h, 7d or PT15M");
        }
        if (duration.isZero() || duration.isNegative()) {
            throw new BadRequestException("period must be positive");
        }
        if (duration.compareTo(MAX_PERIOD) > 0) {
            throw new BadRequestException("period can be at most " + MAX_PERIOD.toDays() + " days");
        }
        return duration;
    }

    private static int recentLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_RECENT_LIMIT;
        }
        if (limit < 1 || limit > MAX_RECENT_LIMIT) {
            throw new BadRequestException("limit must be between 1 and " + MAX_RECENT_LIMIT);
        }
        return limit;
    }

    private static String fileType(UploadOperation op) {
        return fileType(op.mediaTypeCount(), op.mediaType());
    }

    private static String fileType(long mediaTypeCount, String mediaType) {
        return mediaTypeCount > 1 ? "mixed" : fileType(mediaType);
    }

    /** image, video, pdf (the only supported document type), audio or other. */
    static String fileType(String mediaType) {
        if (mediaType == null) {
            return "other";
        }
        return switch (MediaType.valueOf(mediaType)) {
            case DOCUMENT -> "pdf";
            case IMAGE, VIDEO, AUDIO, OTHER -> mediaType.toLowerCase(Locale.ROOT);
        };
    }
}
