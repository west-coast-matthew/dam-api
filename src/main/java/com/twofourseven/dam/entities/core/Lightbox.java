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
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a user-curated collection or shopping cart of assets for specific projects.
 *
 * <h2>Developer Onboarding Notes:</h2>
 * <ul>
 *   <li><b>Purpose:</b> Enables users to group arbitrary assets together temporarily for tasks like design efforts or export staging.</li>
 *   <li><b>Sharing:</b> Supports a visibility toggle (`is_shared`) for public or client review links.</li>
 * </ul>
 */
@Entity
@Table(name = "lightboxes")
@Getter
@Setter
public class Lightbox {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_id", nullable = false)
    private Catalog catalog;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_shared", nullable = false)
    private Boolean isShared = false;

    @OneToMany(mappedBy = "lightbox", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<LightboxItem> items = new HashSet<>();
}
