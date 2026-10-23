package com.twofourseven.dam.entities.core;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
 * Represents the highest-level organizational container in the DAM architecture.
 *
 * <h2>Developer Onboarding Notes:</h2>
 * <ul>
 *   <li><b>Purpose:</b> Acts as a security, tenancy, and storage boundary for enterprise clients or organizational departments.</li>
 *   <li><b>Relationships:</b> Contains one or more {@link Catalog} entities.</li>
 *   <li><b>Storage:</b> Corresponds to the root folder prefix on disk for physical asset separation.</li>
 * </ul>
 */
@Entity
@Table(name = "workspaces")
@Getter
@Setter
public class Workspace {

    public static final int NAME_MAX_LENGTH = 255;
    public static final int DESCRIPTION_MAX_LENGTH = 2000;

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id = UUID.randomUUID();

    @NotBlank(message = "Workspace name is required")
    @Size(max = NAME_MAX_LENGTH, message = "Workspace name must be at most " + NAME_MAX_LENGTH + " characters")
    @Column(name = "name", nullable = false, unique = true, length = NAME_MAX_LENGTH)
    private String name;

    @Size(max = DESCRIPTION_MAX_LENGTH, message = "Workspace description must be at most " + DESCRIPTION_MAX_LENGTH + " characters")
    @Column(name = "description", length = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "workspace", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Catalog> catalogs = new HashSet<>();

    /** Adds a catalog to this workspace and sets the back reference. */
    public void addCatalog(Catalog catalog) {
        catalogs.add(catalog);
        catalog.setWorkspace(this);
    }
}
