package com.twofourseven.dam.repositories;

import com.twofourseven.dam.entities.core.Asset;
import com.twofourseven.dam.entities.core.AssetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
/** {@link JpaSpecificationExecutor} backs the paged browse/search (spec 022, see {@link AssetSpecifications}). */
public interface AssetRepository extends JpaRepository<Asset, UUID>, JpaSpecificationExecutor<Asset> {

    List<Asset> findAllByOrderByCreatedAtDesc();

    List<Asset> findByCatalogIdOrderByCreatedAtDesc(UUID catalogId);

    boolean existsByCatalogId(UUID catalogId);

    /** Where an asset's file is, without loading the entity (used by background thumbnail workers). */
    @Query("select new com.twofourseven.dam.repositories.AssetFileRef(a.id, a.catalog.id, a.catalog.workspace.id,"
            + " a.storagePath, a.originalFilename) from Asset a where a.id = :id")
    Optional<AssetFileRef> findFileRefById(@Param("id") UUID id);

    /**
     * Sets the status only if the asset still points at {@code storagePath}, so a worker that
     * finishes after the file was replaced doesn't overwrite the newer file's status.
     *
     * @return 1 if updated, 0 if the asset is gone or its file changed
     */
    @Transactional
    @Modifying
    @Query("update Asset a set a.status = :status where a.id = :id and a.storagePath = :storagePath")
    int updateStatusIfFileUnchanged(@Param("id") UUID id, @Param("storagePath") String storagePath,
                                    @Param("status") AssetStatus status);
}
