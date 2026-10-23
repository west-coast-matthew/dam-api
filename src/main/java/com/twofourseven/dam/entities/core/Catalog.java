package com.twofourseven.dam.entities.core;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a discrete operational container nested within a Workspace.
 *
 * <h2>Developer Onboarding Notes:</h2>
 * <ul>
 *   <li><b>Purpose:</b> Groups related assets, categories, and tags together. Assets are strictly isolated at this level and cannot cross-reference assets in other catalogs.</li>
 *   <li><b>Relationships:</b> Belongs to a single {@link Workspace}; contains multiple {@link Asset}, {@link Category}, and {@link Tag} entities.</li>
 * </ul>
 */
@Entity
@Table(name = "catalogs")
@Getter
@Setter
public class Catalog {

    public static final int NAME_MAX_LENGTH = 255;
    public static final int DESCRIPTION_MAX_LENGTH = 2000;

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @NotBlank(message = "Catalog name is required")
    @Size(max = NAME_MAX_LENGTH, message = "Catalog name must be at most " + NAME_MAX_LENGTH + " characters")
    @Column(name = "name", nullable = false, unique = true, length = NAME_MAX_LENGTH)
    private String name;

    @Size(max = DESCRIPTION_MAX_LENGTH, message = "Catalog description must be at most " + DESCRIPTION_MAX_LENGTH + " characters")
    @Column(name = "description", length = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "catalog", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Category> categories = new HashSet<>();

    @OneToMany(mappedBy = "catalog", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Tag> tags = new HashSet<>();

    @OneToMany(mappedBy = "catalog", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Asset> assets = new HashSet<>();
}
