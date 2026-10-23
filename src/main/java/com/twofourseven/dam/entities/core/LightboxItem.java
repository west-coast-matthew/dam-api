package com.twofourseven.dam.entities.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Intermediate mapping entity linking an asset inside a specific Lightbox with custom ordering.
 */
@Entity
@Table(name = "lightbox_items")
@IdClass(LightboxItemId.class)
@Getter
@Setter
public class LightboxItem {

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
