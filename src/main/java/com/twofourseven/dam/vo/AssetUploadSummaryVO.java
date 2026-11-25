package com.twofourseven.dam.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Result of {@code GET /assets/activity/summary} (spec 021): recent upload activity across a catalog,
 * or one category's subtree, broken down by category. Only categories with uploads in the window
 * are listed, in category-editor order (parents before children, siblings by position). Each one
 * lists the uploads whose assets are directly in it, so a parent's numbers don't repeat its children's.
 * File types are as in {@link AssetUploadActivityVO}.
 *
 * @param lastSampleTimePeriod       length of the window, in milliseconds
 * @param from                       start of the window
 * @param to                         end of the window (when the request was handled)
 * @param categoryId                 the category the summary was narrowed to; null for the whole catalog
 * @param totalAssetUploadOperations upload requests in the scope (a bulk upload counts once)
 * @param totalAssetsUploaded        assets created in the scope; an asset in several categories counts once
 */
public record AssetUploadSummaryVO(
        long lastSampleTimePeriod,
        LocalDateTime from,
        LocalDateTime to,
        UUID catalogId,
        UUID categoryId,
        long totalAssetUploadOperations,
        long totalAssetsUploaded,
        List<CategoryActivity> categories) {

    /**
     * One category's own uploads in the window.
     *
     * @param path            names from the top-level category down to this one, e.g. [Food, American, BBQ]
     * @param pathIds         the ids of the same categories
     * @param uploadEvents    one entry per upload request, newest first
     */
    public record CategoryActivity(
            UUID categoryId,
            String categoryName,
            List<String> path,
            List<UUID> pathIds,
            long uploadOperations,
            long assetsUploaded,
            List<UploadEvent> uploadEvents) {
    }

    /** @param operationId shared by the assets of one upload request */
    public record UploadEvent(UUID operationId, LocalDateTime dateTime, long uploadCount, String fileType) {
    }
}
