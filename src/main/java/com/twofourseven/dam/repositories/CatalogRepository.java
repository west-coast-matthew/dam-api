package com.twofourseven.dam.repositories;

import com.twofourseven.dam.entities.core.Catalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CatalogRepository extends JpaRepository<Catalog, UUID> {

    boolean existsByNameIgnoreCase(String name);
}
