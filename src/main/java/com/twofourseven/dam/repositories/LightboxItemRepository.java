package com.twofourseven.dam.repositories;

import com.twofourseven.dam.entities.core.Asset;
import com.twofourseven.dam.entities.core.LightboxItem;
import com.twofourseven.dam.entities.core.LightboxItemId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LightboxItemRepository extends JpaRepository<LightboxItem, LightboxItemId> {

    void deleteByAssetId(UUID assetId);

    /** A lightbox's assets in lightbox order (spec 009). The pageable must not carry a sort. */
    @Query(value = "select i.asset from LightboxItem i where i.lightbox.id = :lightboxId order by i.sortOrder asc, i.addedAt asc",
            countQuery = "select count(i) from LightboxItem i where i.lightbox.id = :lightboxId")
    Page<Asset> findAssetsByLightboxId(@Param("lightboxId") UUID lightboxId, Pageable pageable);
}
