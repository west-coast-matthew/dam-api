package com.twofourseven.dam.services;

import com.twofourseven.dam.entities.core.Category;
import com.twofourseven.dam.entities.core.MediaType;
import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.ResourceNotFoundException;
import com.twofourseven.dam.repositories.AssetActivityRepository;
import com.twofourseven.dam.repositories.AssetActivityRepository.UploadOperation;
import com.twofourseven.dam.repositories.CategoryRepository;
import com.twofourseven.dam.vo.AssetUploadActivityVO;
import com.twofourseven.dam.vo.AssetUploadActivityVO.RecentAsset;
import com.twofourseven.dam.vo.AssetUploadActivityVO.SubCategory;
import com.twofourseven.dam.vo.AssetUploadActivityVO.UploadAttempt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
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
    private final Clock clock;

    @Autowired
    public AssetActivityService(AssetActivityRepository activityRepository, CategoryRepository categoryRepository) {
        this(activityRepository, categoryRepository, Clock.systemDefaultZone());
    }

    /** {@code clock} must use the zone asset {@code createdAt} values are written in (the system zone). */
    AssetActivityService(AssetActivityRepository activityRepository, CategoryRepository categoryRepository, Clock clock) {
        this.activityRepository = activityRepository;
        this.categoryRepository = categoryRepository;
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
        return op.mediaTypeCount() > 1 ? "mixed" : fileType(op.mediaType());
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
