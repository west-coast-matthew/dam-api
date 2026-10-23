package com.example.dam.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Container holding all JPA Entities for the Digital Asset Management (DAM) solution.
 * Designed for modularity, multi-tenancy boundaries, hierarchical navigation, and asset lifecycle tracking.
 */
public class DamEntities {

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
    public static class Workspace {

        @Id
        @Column(name = "id", updatable = false, nullable = false)
        private UUID id = UUID.randomUUID();

        @Column(name = "name", nullable = false)
        private String name;

        @Column(name = "created_at", nullable = false, updatable = false)
        private LocalDateTime createdAt = LocalDateTime.now();

        @OneToMany(mappedBy = "workspace", cascade = CascadeType.ALL, orphanRemoval = true)
        private Set<Catalog> catalogs = new HashSet<>();
    }

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
    public static class Catalog {

        @Id
        @Column(name = "id", updatable = false, nullable = false)
        private UUID id = UUID.randomUUID();

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "workspace_id", nullable = false)
        private Workspace workspace;

        @Column(name = "name", nullable = false)
        private String name;

        @Column(name = "created_at", nullable = false, updatable = false)
        private LocalDateTime createdAt = LocalDateTime.now();

        @OneToMany(mappedBy = "catalog", cascade = CascadeType.ALL, orphanRemoval = true)
        private Set<Category> categories = new HashSet<>();

        @OneToMany(mappedBy = "catalog", cascade = CascadeType.ALL, orphanRemoval = true)
        private Set<Tag> tags = new HashSet<>();

        @OneToMany(mappedBy = "catalog", cascade = CascadeType.ALL, orphanRemoval = true)
        private Set<Asset> assets = new HashSet<>();
    }

    /**
     * Represents a logical folder structure within a Catalog using an Adjacency List pattern.
     * 
     * <h2>Developer Onboarding Notes:</h2>
     * <ul>
     *   <li><b>Purpose:</b> Empowers end-users to organize assets hierarchically (e.g., Animals -> Dogs) without relying on physical disk folders.</li>
     *   <li><b>Hierarchy:</b> Supports self-referencing parent-child relationships via {@code parent} and {@code children}. Top-level folders have a null parent.</li>
     *   <li><b>Relationships:</b> Mapped to assets through a many-to-many join table (`asset_categories`).</li>
     * </ul>
     */
    @Entity
    @Table(name = "categories")
    @Getter
    @Setter
    public static class Category {

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

        @ManyToMany(mappedBy = "categories")
        private Set<Asset> assets = new HashSet<>();
    }

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
    public static class Tag {

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

    /**
     * Core entity representing an uploaded digital asset (image, video, document, etc.).
     * 
     * <h2>Developer Onboarding Notes:</h2>
     * <ul>
     *   <li><b>Storage Strategy:</b> Points to a decoupled, hashed directory path on disk (managed outside category structures).</li>
     *   <li><b>Asynchronous Processing:</b> Includes an asset {@link AssetStatus} lifecycle flag (`PENDING`, `READY`, `FAILED`) to handle thumbnail generation cleanly.</li>
     *   <li><b>Custom Metadata:</b> Features a PostgreSQL {@code JSONB} / MySQL {@code JSON} map (`customMetadata`) for dynamic catalog-specific schemas without rigid table alterations.</li>
     * </ul>
     */
    @Entity
    @Table(name = "assets")
    @Getter
    @Setter
    public static class Asset {

        @Id
        @Column(name = "id", updatable = false, nullable = false)
        private UUID id = UUID.randomUUID();

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "catalog_id", nullable = false)
        private Catalog catalog;

        @Column(name = "original_filename", nullable = false)
        private String originalFilename;

        @Column(name = "storage_path", nullable = false)
        private String storagePath;

        @Enumerated(EnumType.STRING)
        @Column(name = "media_type", nullable = false)
        private MediaType mediaType;

        @Column(name = "mime_type", nullable = false)
        private String mimeType;

        @Column(name = "file_size", nullable = false)
        private Long fileSize;

        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false)
        private AssetStatus status = AssetStatus.PENDING_PROCESSING;

        @JdbcTypeCode(SqlTypes.JSON)
        @Column(name = "custom_metadata", columnDefinition = "jsonb")
        private java.util.Map<String, Object> customMetadata = new java.util.HashMap<>();

        @Column(name = "created_at", nullable = false, updatable = false)
        private LocalDateTime createdAt = LocalDateTime.now();

        @OneToMany(mappedBy = "asset", cascade = CascadeType.ALL, orphanRemoval = true)
        private Set<AssetVersion> versions = new HashSet<>();

        @ManyToMany
        @JoinTable(
            name = "asset_categories",
            joinColumns = @JoinColumn(name = "asset_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
        )
        private Set<Category> categories = new HashSet<>();

        @ManyToMany
        @JoinTable(
            name = "asset_tags",
            joinColumns = @JoinColumn(name = "asset_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
        )
        private Set<Tag> tags = new HashSet<>();
    }

    /**
     * Tracks version iterations for an existing asset file update.
     * 
     * <h2>Developer Onboarding Notes:</h2>
     * <ul>
     *   <li><b>Purpose:</b> Allows file replacements (e.g., an updated graphic) while preserving original asset metadata, IDs, and Lightbox links.</li>
     * </ul>
     */
    @Entity
    @Table(name = "asset_versions")
    @Getter
    @Setter
    public static class AssetVersion {

        @Id
        @Column(name = "id", updatable = false, nullable = false)
        private UUID id = UUID.randomUUID();

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "asset_id", nullable = false)
        private Asset asset;

        @Column(name = "version_number", nullable = false)
        private Integer versionNumber;

        @Column(name = "storage_path", nullable = false)
        private String storagePath;

        @Column(name = "file_size", nullable = false)
        private Long fileSize;

        @Column(name = "created_at", nullable = false, updatable = false)
        private LocalDateTime createdAt = LocalDateTime.now();
    }

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
    public static class Lightbox {

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

    /**
     * Composite primary key definition for the {@link LightboxItem} join entity.
     */
    public static class LightboxItemId implements Serializable {
        private UUID lightbox;
        private UUID asset;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LightboxItemId)) return false;
            LightboxItemId that = (LightboxItemId) o;
            return Objects.equals(lightbox, that.lightbox) && Objects.equals(asset, that.asset);
        }

        @Override
        public int hashCode() {
            return Objects.hash(lightbox, asset);
        }
    }

    /**
     * Intermediate mapping entity linking an asset inside a specific Lightbox with custom ordering.
     */
    @Entity
    @Table(name = "lightbox_items")
    @IdClass(LightboxItemId.class)
    @Getter
    @Setter
    public static class LightboxItem {

        @Id
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "lightbox_id", nullable = false)
        private Lightbox lightbox;

        @Id
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "asset_id", nullable = false)
        private Asset asset;

        @Column(name = "sort_order")
        private Integer sortOrder = 0;

        @Column(name = "added_at", nullable = false)
        private LocalDateTime addedAt = LocalDateTime.now();
    }

    /**
     * Composite primary key definition for user-specific asset interactions.
     */
    public static class UserAssetInteractionId implements Serializable {
        private UUID userId;
        private UUID asset;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UserAssetInteractionId)) return false;
            UserAssetInteractionId that = (UserAssetInteractionId) o;
            return Objects.equals(userId, that.userId) && Objects.equals(asset, that.asset);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, asset);
        }
    }

    /**
     * Manages per-user interactions (such as personal favorites and star rankings) with assets.
     * 
     * <h2>Developer Onboarding Notes:</h2>
     * <ul>
     *   <li><b>Design Pattern:</b> Uses a composite key (`userId`, `assetId`) so ratings and favorites are isolated per individual user rather than globally on the asset.</li>
     * </ul>
     */
    @Entity
    @Table(name = "user_asset_interactions")
    @IdClass(UserAssetInteractionId.class)
    @Getter
    @Setter
    public static class UserAssetInteraction {

        @Id
        @Column(name = "user_id", nullable = false)
        private UUID userId;

        @Id
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "asset_id", nullable = false)
        private Asset asset;

        @Column(name = "is_favorite", nullable = false)
        private Boolean isFavorite = false;

        @Column(name = "rating")
        private Integer rating; // Value between 1 and 5
    }

    /**
     * Immutable audit trail log tracking asset downloads and exports.
     */
    @Entity
    @Table(name = "asset_download_logs")
    @Getter
    @Setter
    public static class AssetDownloadLog {

        @Id
        @Column(name = "id", updatable = false, nullable = false)
        private UUID id = UUID.randomUUID();

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "asset_id", nullable = false)
        private Asset asset;

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        @Column(name = "downloaded_at", nullable = false, updatable = false)
        private LocalDateTime downloadedAt = LocalDateTime.now();
    }

    /**
     * Supported media format classifications for assets.
     */
    public enum MediaType {
        IMAGE,
        VIDEO,
        DOCUMENT,
        AUDIO,
        OTHER
    }

    /**
     * Processing status lifecycle flags for asynchronous handlers.
     */
    public enum AssetStatus {
        PENDING_PROCESSING,
        READY,
        FAILED
    }
}