package com.twofourseven.dam.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Result of {@code GET /assets/activity}: upload activity for a category and its subcategories in a
 * recent time window. File types are {@code image}, {@code video}, {@code pdf}, {@code audio} or
 * {@code other}, and {@code mixed} for an upload holding more than one type.
 *
 * @param lastSampleTimePeriod       length of the window, in milliseconds
 * @param from                       start of the window
 * @param to                         end of the window (when the request was handled)
 * @param totalAssetUploadOperations upload requests in the window (a bulk upload counts once)
 * @param totalAssetsUploaded        assets created in the window
 * @param uploadAttempts             one entry per upload request, newest first
 * @param recentAssets               the newest assets in the window, newest first
 * @param subCategories              the category's direct children with their new asset counts;
 *                                   empty when it has none
 */
public record AssetUploadActivityVO(
        long lastSampleTimePeriod,
        LocalDateTime from,
        LocalDateTime to,
        long totalAssetUploadOperations,
        long totalAssetsUploaded,
        UUID categoryId,
        String categoryName,
        List<UploadAttempt> uploadAttempts,
        List<RecentAsset> recentAssets,
        List<SubCategory> subCategories) {

    /** One upload request: when it happened, how many assets it created and their file type. */
    public record UploadAttempt(LocalDateTime dateTime, long uploadCount, String fileType) {
    }

    /** @param title the uploaded file name */
    public record RecentAsset(UUID id, String title, String fileType, LocalDateTime uploadedAt) {
    }

    /** @param newAssetCount assets created in the window in this subcategory or below it */
    public record SubCategory(UUID id, String title, long newAssetCount) {
    }
}
