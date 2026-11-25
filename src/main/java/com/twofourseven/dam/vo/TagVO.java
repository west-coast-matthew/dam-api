package com.twofourseven.dam.vo;

import java.util.UUID;

/**
 * A catalog tag as listed by {@code GET /tags} (spec 023).
 *
 * @param assetCount number of assets carrying the tag; 0 for tags no longer in use
 */
public record TagVO(UUID id, String name, long assetCount) {
}
