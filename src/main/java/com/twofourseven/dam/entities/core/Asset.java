package com.twofourseven.dam.entities.core;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Core entity representing an uploaded digital asset (image, video, document, etc.).
 *
 * <h2>Developer Onboarding Notes:</h2>
 * <ul>
 *   <li><b>Storage Strategy:</b> Points to a decoupled, hashed directory path on disk (managed outside category structures).</li>
 *   <li><b>Asynchronous Processing:</b> Includes an asset {@link AssetStatus} lifecycle flag (`PENDING_PROCESSING`, `READY`, `FAILED`) to handle thumbnail generation cleanly.</li>
 *   <li><b>Custom Metadata:</b> A JSON column (`customMetadata`) for dynamic catalog-specific schemas without rigid table alterations.</li>
 * </ul>
 */
@Entity
@Table(name = "assets")
@Getter
@Setter
public class Asset {

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

    // MariaDB JSON column (the original PostgreSQL "jsonb" definition is not valid on MariaDB)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_metadata")
    private Map<String, Object> customMetadata = new HashMap<>();

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
