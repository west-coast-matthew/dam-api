package com.twofourseven.dam.repositories;

import com.twofourseven.dam.entities.core.UserAssetInteraction;
import com.twofourseven.dam.entities.core.UserAssetInteractionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAssetInteractionRepository extends JpaRepository<UserAssetInteraction, UserAssetInteractionId> {
}
