package com.twofourseven.dam.repositories;

import com.twofourseven.dam.entities.core.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {

    Optional<Tag> findFirstByCatalogIdAndNameIgnoreCase(UUID catalogId, String name);
}
