package com.twofourseven.dam.repositories;

import com.twofourseven.dam.entities.core.Tag;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {

    Optional<Tag> findFirstByCatalogIdAndNameIgnoreCase(UUID catalogId, String name);

    /**
     * The catalog's tags with their asset counts, sorted by name ignoring case. {@code pattern} is a
     * lower-case LIKE pattern (escape character {@code \}); null matches every tag.
     */
    @Query("""
            select t.id as id, t.name as name, count(a) as assetCount
            from Tag t left join t.assets a
            where t.catalog.id = :catalogId
              and (:pattern is null or lower(t.name) like :pattern escape '\\')
            group by t.id, t.name
            order by lower(t.name), t.name""")
    List<TagUsage> findUsageByCatalog(@Param("catalogId") UUID catalogId, @Param("pattern") String pattern, Limit limit);
}
