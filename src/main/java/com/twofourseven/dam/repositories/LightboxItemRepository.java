package com.twofourseven.dam.repositories;

import com.twofourseven.dam.entities.core.LightboxItem;
import com.twofourseven.dam.entities.core.LightboxItemId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LightboxItemRepository extends JpaRepository<LightboxItem, LightboxItemId> {
}
