package com.twofourseven.dam.entities.core;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key definition for user-specific asset interactions.
 */
public class UserAssetInteractionId implements Serializable {

    private UUID userId;
    private UUID asset;

    public UserAssetInteractionId() {
    }

    public UserAssetInteractionId(UUID userId, UUID asset) {
        this.userId = userId;
        this.asset = asset;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserAssetInteractionId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(asset, that.asset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, asset);
    }
}
