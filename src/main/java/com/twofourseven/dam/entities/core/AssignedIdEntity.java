package com.twofourseven.dam.entities.core;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

/**
 * Base for entities whose UUID id is assigned in Java. Spring Data treats an entity with a
 * non-null id as existing and calls {@code merge()}, which saves a copy; if the original object is
 * also reachable through a cascade (e.g. {@code workspace.addCatalog(catalog)}), Hibernate then sees
 * two objects with the same id and the commit fails. Tracking "new" here makes {@code save()} call
 * {@code persist()} for objects created in Java, and {@code merge()} only for loaded ones.
 */
@MappedSuperclass
public abstract class AssignedIdEntity implements Persistable<UUID> {

    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
