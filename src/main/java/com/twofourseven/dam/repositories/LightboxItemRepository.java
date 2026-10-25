package com.twofourseven.dam.repositories;

import com.twofourseven.dam.entities.core.LightboxItem;
import com.twofourseven.dam.entities.core.LightboxItemId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LightboxItemRepository extends JpaRepository<LightboxItem, LightboxItemId> {

    void deleteByAssetId(UUID assetId);
}
