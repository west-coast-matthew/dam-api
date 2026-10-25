package com.twofourseven.dam.repositories;

import com.twofourseven.dam.entities.core.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID> {

    List<Asset> findAllByOrderByCreatedAtDesc();

    List<Asset> findByCatalogIdOrderByCreatedAtDesc(UUID catalogId);

    boolean existsByCatalogId(UUID catalogId);
}
