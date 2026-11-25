package com.twofourseven.dam.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Upload activity statistics (spec 020), read with native MariaDB SQL rather than by loading
 * entities. A category's activity covers its whole subtree, found with a recursive CTE; an asset in
 * several categories of the subtree is counted once. Ids are read back with
 * {@link NativeQuery#addScalar(String, Class)} so UUIDs convert the same way as in the entities,
 * whatever column type the database uses for them.
 */
@Repository
public class AssetActivityRepository {

    /** One upload request: the assets sharing an upload_batch_id (or a single asset uploaded before db/020). */
    public record UploadOperation(UUID operationId, LocalDateTime uploadedAt, long assetCount, long mediaTypeCount,
                                  String mediaType) {
    }

    public record RecentAsset(UUID id, String originalFilename, String mediaType, LocalDateTime createdAt) {
    }

    /** A direct child of the selected category, with the new assets in its subtree. */
    public record SubCategoryCount(UUID id, String name, long newAssetCount) {
    }

    /** The selected category's subtree and its assets created since {@code :since}. */
    private static final String SUBTREE_ASSETS = """
            WITH RECURSIVE tree (id) AS (
                SELECT id FROM categories WHERE id = :categoryId
                UNION ALL
                SELECT c.id FROM categories c JOIN tree t ON c.parent_id = t.id
            ),
            window_assets AS (
                SELECT DISTINCT a.id, a.upload_batch_id, a.created_at, a.media_type, a.original_filename
                FROM assets a
                JOIN asset_categories ac ON ac.asset_id = a.id
                JOIN tree t ON t.id = ac.category_id
                WHERE a.created_at >= :since
            )
            """;

    private static final String OPERATIONS = SUBTREE_ASSETS + """
            SELECT COALESCE(upload_batch_id, id) AS operation_id,
                   MIN(created_at) AS uploaded_at,
                   COUNT(*) AS asset_count,
                   COUNT(DISTINCT media_type) AS media_type_count,
                   MIN(media_type) AS media_type
            FROM window_assets
            GROUP BY COALESCE(upload_batch_id, id)
            ORDER BY uploaded_at DESC
            """;

    private static final String RECENT = SUBTREE_ASSETS + """
            SELECT id, original_filename, media_type, created_at
            FROM window_assets
            ORDER BY created_at DESC
            """;

    private static final String SUB_CATEGORIES = """
            WITH RECURSIVE tree (root_id, id) AS (
                SELECT id, id FROM categories WHERE parent_id = :categoryId
                UNION ALL
                SELECT t.root_id, c.id FROM categories c JOIN tree t ON c.parent_id = t.id
            )
            SELECT child.id AS id, child.name AS name, COUNT(DISTINCT a.id) AS new_asset_count
            FROM categories child
            JOIN tree t ON t.root_id = child.id
            LEFT JOIN asset_categories ac ON ac.category_id = t.id
            LEFT JOIN assets a ON a.id = ac.asset_id AND a.created_at >= :since
            WHERE child.parent_id = :categoryId
            GROUP BY child.id, child.name, child.sort_order
            ORDER BY child.sort_order, child.name
            """;

    @PersistenceContext
    private EntityManager entityManager;

    /** Upload operations in the category's subtree since {@code since}, newest first. */
    public List<UploadOperation> uploadOperations(UUID categoryId, LocalDateTime since) {
        List<Object[]> rows = query(OPERATIONS, categoryId, since)
                .addScalar("operation_id", UUID.class)
                .addScalar("uploaded_at", LocalDateTime.class)
                .addScalar("asset_count", Long.class)
                .addScalar("media_type_count", Long.class)
                .addScalar("media_type", String.class)
                .getResultList();
        return rows.stream().map(r -> new UploadOperation((UUID) r[0], (LocalDateTime) r[1], (Long) r[2], (Long) r[3],
                (String) r[4])).toList();
    }

    /** The newest {@code limit} assets in the category's subtree created since {@code since}. */
    public List<RecentAsset> recentAssets(UUID categoryId, LocalDateTime since, int limit) {
        List<Object[]> rows = query(RECENT, categoryId, since)
                .addScalar("id", UUID.class)
                .addScalar("original_filename", String.class)
                .addScalar("media_type", String.class)
                .addScalar("created_at", LocalDateTime.class)
                .setMaxResults(limit)
                .getResultList();
        return rows.stream().map(r -> new RecentAsset((UUID) r[0], (String) r[1], (String) r[2], (LocalDateTime) r[3]))
                .toList();
    }

    /** The category's direct children, in editor order, with new assets in each child's subtree. */
    public List<SubCategoryCount> subCategoryCounts(UUID categoryId, LocalDateTime since) {
        List<Object[]> rows = query(SUB_CATEGORIES, categoryId, since)
                .addScalar("id", UUID.class)
                .addScalar("name", String.class)
                .addScalar("new_asset_count", Long.class)
                .getResultList();
        return rows.stream().map(r -> new SubCategoryCount((UUID) r[0], (String) r[1], (Long) r[2])).toList();
    }

    @SuppressWarnings("unchecked")
    private NativeQuery<Object[]> query(String sql, UUID categoryId, LocalDateTime since) {
        NativeQuery<Object[]> query = entityManager.createNativeQuery(sql).unwrap(NativeQuery.class);
        query.setParameter("categoryId", categoryId);
        query.setParameter("since", since);
        return query;
    }
}
