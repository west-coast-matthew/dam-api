package com.twofourseven.dam.repositories;

import com.twofourseven.dam.entities.core.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /** In sibling order; parents are not guaranteed to come before their children. */
    List<Category> findByCatalogIdOrderByPositionAscNameAsc(UUID catalogId);

    long countByCatalogIdAndParentIsNull(UUID catalogId);

    long countByParentId(UUID parentId);

    /** Top-level category names are unique within a catalog. */
    boolean existsByCatalogIdAndParentIsNullAndNameIgnoreCase(UUID catalogId, String name);

    boolean existsByCatalogIdAndParentIsNullAndNameIgnoreCaseAndIdNot(UUID catalogId, String name, UUID id);

    boolean existsByParentId(UUID parentId);

    Optional<Category> findFirstByCatalogIdAndIsDefaultTrue(UUID catalogId);
}
