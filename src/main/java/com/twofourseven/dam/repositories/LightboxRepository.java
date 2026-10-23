package com.twofourseven.dam.repositories;

import com.twofourseven.dam.entities.core.Lightbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LightboxRepository extends JpaRepository<Lightbox, UUID> {
}
