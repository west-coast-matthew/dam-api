package com.twofourseven.dam.entities.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents flat descriptive labels scoped to a specific Catalog.
 *
 * <h2>Developer Onboarding Notes:</h2>
 * <ul>
 *   <li><b>Purpose:</b> Provides flexible, non-hierarchical keyword labeling for searchability across assets.</li>
 *   <li><b>Relationships:</b> Scoped to a single {@link Catalog}; linked to assets via `asset_tags`.</li>
 * </ul>
 */
@Entity
@Table(name = "tags")
@Getter
@Setter
public class Tag {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_id", nullable = false)
    private Catalog catalog;

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToMany(mappedBy = "tags")
    private Set<Asset> assets = new HashSet<>();
}
