package com.twofourseven.dam.repositories;

import com.twofourseven.dam.entities.core.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByCatalogIdOrderByNameAsc(UUID catalogId);

    /** Top-level category names are unique within a catalog. */
    boolean existsByCatalogIdAndParentIsNullAndNameIgnoreCase(UUID catalogId, String name);

    boolean existsByCatalogIdAndParentIsNullAndNameIgnoreCaseAndIdNot(UUID catalogId, String name, UUID id);

    boolean existsByParentId(UUID parentId);
}
