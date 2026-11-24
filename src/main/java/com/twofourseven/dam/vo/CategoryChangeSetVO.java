package com.twofourseven.dam.vo;

import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code POST /categories/changes}: every edit made in the category editor, applied
 * together in one transaction or not at all.
 *
 * <p>A {@code parentId} is a string because it may name a category created in the same change set
 * by its {@code tempId} as well as an existing category's UUID. Null means top-level.
 *
 * @param created new categories; a parent may be another created category
 * @param updated the new state of existing categories whose name, description, parent or position changed
 * @param deleted ids of deleted categories; their descendants are deleted with them
 */
public record CategoryChangeSetVO(List<Created> created, List<Updated> updated, List<UUID> deleted) {

    public CategoryChangeSetVO {
        created = created == null ? List.of() : created;
        updated = updated == null ? List.of() : updated;
        deleted = deleted == null ? List.of() : deleted;
    }

    /** A new category. {@code tempId} is chosen by the client and only lives for this request. */
    public record Created(String tempId, String parentId, String name, String description, Integer position) {
    }

    /** Replaces an existing category's editable fields. */
    public record Updated(UUID id, String parentId, String name, String description, Integer position) {
    }
}
