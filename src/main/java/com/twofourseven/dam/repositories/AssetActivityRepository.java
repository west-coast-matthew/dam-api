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

    /** One upload request's assets that are directly in one category (spec 021). */
    public record CategoryOperation(UUID categoryId, UUID operationId, LocalDateTime uploadedAt, long assetCount,
                                    long mediaTypeCount, String mediaType) {
    }

    /** Distinct upload operations and assets in a scope; an asset in several categories counts once. */
    public record Totals(long operations, long assets) {
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

    /**
     * Spec 021 scope: the subtree of {@code :categoryId}, or every category of {@code :catalogId}
     * (all its top-level categories and their subtrees).
     */
    private static String scopedTree(boolean byCategory) {
        String roots = byCategory ? "id = :categoryId" : "catalog_id = :catalogId AND parent_id IS NULL";
        return """
                WITH RECURSIVE tree (id) AS (
                    SELECT id FROM categories WHERE %s
                    UNION ALL
                    SELECT c.id FROM categories c JOIN tree t ON c.parent_id = t.id
                )
                """.formatted(roots);
    }

    /** Per category (assets directly in it, not below) and upload operation. */
    private static final String CATEGORY_OPERATIONS = """
            SELECT ac.category_id AS category_id,
                   COALESCE(a.upload_batch_id, a.id) AS operation_id,
                   MIN(a.created_at) AS uploaded_at,
                   COUNT(*) AS asset_count,
                   COUNT(DISTINCT a.media_type) AS media_type_count,
                   MIN(a.media_type) AS media_type
            FROM assets a
            JOIN asset_categories ac ON ac.asset_id = a.id
            JOIN tree t ON t.id = ac.category_id
            WHERE a.created_at >= :since
            GROUP BY ac.category_id, COALESCE(a.upload_batch_id, a.id)
            ORDER BY uploaded_at DESC
            """;

    private static final String TOTALS = """
            SELECT COUNT(DISTINCT COALESCE(w.upload_batch_id, w.id)) AS operations, COUNT(*) AS assets
            FROM (
                SELECT DISTINCT a.id, a.upload_batch_id
                FROM assets a
                JOIN asset_categories ac ON ac.asset_id = a.id
                JOIN tree t ON t.id = ac.category_id
                WHERE a.created_at >= :since
            ) w
            """;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Upload operations since {@code since}, split by the category their assets are directly in,
     * newest first. Scope: {@code categoryId}'s subtree when given, otherwise the whole catalog.
     */
    public List<CategoryOperation> categoryOperations(UUID catalogId, UUID categoryId, LocalDateTime since) {
        List<Object[]> rows = scoped(CATEGORY_OPERATIONS, catalogId, categoryId, since)
                .addScalar("category_id", UUID.class)
                .addScalar("operation_id", UUID.class)
                .addScalar("uploaded_at", LocalDateTime.class)
                .addScalar("asset_count", Long.class)
                .addScalar("media_type_count", Long.class)
                .addScalar("media_type", String.class)
                .getResultList();
        return rows.stream().map(r -> new CategoryOperation((UUID) r[0], (UUID) r[1], (LocalDateTime) r[2], (Long) r[3],
                (Long) r[4], (String) r[5])).toList();
    }

    /** Distinct upload operations and assets since {@code since} in the same scope as {@link #categoryOperations}. */
    public Totals totals(UUID catalogId, UUID categoryId, LocalDateTime since) {
        List<Object[]> rows = scoped(TOTALS, catalogId, categoryId, since)
                .addScalar("operations", Long.class)
                .addScalar("assets", Long.class)
                .getResultList();
        Object[] r = rows.getFirst();
        return new Totals(r[0] == null ? 0 : (Long) r[0], r[1] == null ? 0 : (Long) r[1]);
    }

    @SuppressWarnings("unchecked")
    private NativeQuery<Object[]> scoped(String select, UUID catalogId, UUID categoryId, LocalDateTime since) {
        boolean byCategory = categoryId != null;
        NativeQuery<Object[]> query = entityManager.createNativeQuery(scopedTree(byCategory) + select)
                .unwrap(NativeQuery.class);
        if (byCategory) {
            query.setParameter("categoryId", categoryId);
        } else {
            query.setParameter("catalogId", catalogId);
        }
        query.setParameter("since", since);
        return query;
    }

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
