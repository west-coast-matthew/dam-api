package com.twofourseven.dam.vo;

import java.util.List;
import java.util.UUID;

/**
 * A list of asset ids, for adding to, removing from or reordering a lightbox, and for group
 * downloads (spec 009).
 */
public record LightboxItemsVO(List<UUID> assetIds) {
}
