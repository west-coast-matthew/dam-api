package com.twofourseven.dam.entities.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

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
public class AssetVersion {

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
