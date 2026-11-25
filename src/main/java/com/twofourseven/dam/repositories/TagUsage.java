package com.twofourseven.dam.repositories;

import java.util.UUID;

/** Projection of a tag with the number of assets linked to it. */
public interface TagUsage {

    UUID getId();

    String getName();

    long getAssetCount();
}
