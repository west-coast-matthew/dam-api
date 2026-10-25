package com.twofourseven.dam.entities.core;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a logical folder structure within a Catalog using an Adjacency List pattern.
 *
 * <h2>Developer Onboarding Notes:</h2>
 * <ul>
 *   <li><b>Purpose:</b> Empowers end-users to organize assets hierarchically (e.g., Animals -> Dogs) without relying on physical disk folders.</li>
 *   <li><b>Hierarchy:</b> Supports self-referencing parent-child relationships via {@code parent} and {@code children}. Top-level folders have a null parent.</li>
 *   <li><b>Relationships:</b> Mapped to assets through a many-to-many join table (`asset_categories`).</li>
 *   <li><b>Default:</b> Every catalog has one top-level default category ({@code isDefault}), created with
 *       the catalog. Assets that aren't given a category are put in it.</li>
 * </ul>
 */
@Entity
@Table(name = "categories")
@Getter
@Setter
public class Category {

    public static final String DEFAULT_NAME = "Default";
    public static final String DEFAULT_SLUG = "default";

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_id", nullable = false)
    private Catalog catalog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Category> children = new HashSet<>();

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false)
    private String slug;

    /** True for the catalog's default category. Exactly one per catalog; it can't be deleted or nested. */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @ManyToMany(mappedBy = "categories")
    private Set<Asset> assets = new HashSet<>();

    /** Creates the default category for {@code catalog} and adds it to the catalog (saved by its cascade). */
    public static Category createDefaultFor(Catalog catalog) {
        Category category = new Category();
        category.setName(DEFAULT_NAME);
        category.setSlug(DEFAULT_SLUG);
        category.setIsDefault(true);
        category.setCatalog(catalog);
        catalog.getCategories().add(category);
        return category;
    }
}
