package com.twofourseven.dam.repositories;

import com.twofourseven.dam.entities.core.Asset;
import com.twofourseven.dam.entities.core.Category;
import com.twofourseven.dam.entities.core.Tag;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Filters for the paged asset browse/search (spec 022). Category and tag filters are EXISTS
 * subqueries rather than joins, so an asset matching several ways is still one row and paging
 * counts stay right.
 */
public final class AssetSpecifications {

    private AssetSpecifications() {
    }

    public static Specification<Asset> inCatalog(UUID catalogId) {
        return (root, query, cb) -> cb.equal(root.get("catalog").get("id"), catalogId);
    }

    /** In at least one of the categories. */
    public static Specification<Asset> inAnyCategory(Collection<UUID> categoryIds) {
        return (root, query, cb) -> {
            Subquery<UUID> sub = query.subquery(UUID.class);
            Root<Asset> a = sub.from(Asset.class);
            Join<Asset, Category> c = a.join("categories");
            sub.select(a.get("id")).where(cb.equal(a.get("id"), root.get("id")), c.get("id").in(categoryIds));
            return cb.exists(sub);
        };
    }

    /** Created at or after {@code from}. */
    public static Specification<Asset> createdFrom(LocalDateTime from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    /** Created before {@code to}. */
    public static Specification<Asset> createdBefore(LocalDateTime to) {
        return (root, query, cb) -> cb.lessThan(root.get("createdAt"), to);
    }

    /** Has a tag with this name, ignoring case. */
    public static Specification<Asset> hasTag(String name) {
        return hasAnyTag(List.of(name));
    }

    /** Has at least one tag with one of these names, ignoring case. */
    public static Specification<Asset> hasAnyTag(Collection<String> names) {
        Collection<String> lower = names.stream().map(n -> n.toLowerCase(Locale.ROOT)).toList();
        return (root, query, cb) -> {
            Subquery<UUID> sub = query.subquery(UUID.class);
            Root<Asset> a = sub.from(Asset.class);
            Join<Asset, Tag> t = a.join("tags");
            sub.select(a.get("id")).where(cb.equal(a.get("id"), root.get("id")), cb.lower(t.get("name")).in(lower));
            return cb.exists(sub);
        };
    }

    /** The file name contains {@code text}, ignoring case. */
    public static Specification<Asset> filenameContains(String text) {
        String pattern = "%" + text.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("originalFilename")), pattern, '\\');
    }
}
