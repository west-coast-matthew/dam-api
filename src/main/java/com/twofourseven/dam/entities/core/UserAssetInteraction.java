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

import java.util.UUID;

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
public class UserAssetInteraction {

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
