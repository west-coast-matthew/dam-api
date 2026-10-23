package com.twofourseven.dam.entities.core;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key definition for the {@link LightboxItem} join entity.
 */
public class LightboxItemId implements Serializable {

    private UUID lightbox;
    private UUID asset;

    public LightboxItemId() {
    }

    public LightboxItemId(UUID lightbox, UUID asset) {
        this.lightbox = lightbox;
        this.asset = asset;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LightboxItemId that)) return false;
        return Objects.equals(lightbox, that.lightbox) && Objects.equals(asset, that.asset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lightbox, asset);
    }
}
