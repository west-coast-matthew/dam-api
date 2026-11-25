package com.twofourseven.dam.services;

import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.ResourceNotFoundException;
import com.twofourseven.dam.repositories.CatalogRepository;
import com.twofourseven.dam.repositories.TagRepository;
import com.twofourseven.dam.vo.TagVO;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Lists a catalog's tags (spec 023) so clients can offer existing tags for selection. Tags are
 * created and assigned through the asset endpoints ({@link AssetService}); tags left without assets
 * are kept.
 */
@Service
public class TagService {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    private final TagRepository tagRepository;
    private final CatalogRepository catalogRepository;

    public TagService(TagRepository tagRepository, CatalogRepository catalogRepository) {
        this.tagRepository = tagRepository;
        this.catalogRepository = catalogRepository;
    }

    /**
     * @param q     optional case-insensitive "contains" filter on the name
     * @param limit 1–{@value #MAX_LIMIT}; null = {@value #DEFAULT_LIMIT}
     */
    @Transactional(readOnly = true)
    public List<TagVO> list(UUID catalogId, String q, Integer limit) {
        int max = limit == null ? DEFAULT_LIMIT : limit;
        if (max < 1 || max > MAX_LIMIT) {
            throw new BadRequestException("limit must be between 1 and " + MAX_LIMIT);
        }
        if (!catalogRepository.existsById(catalogId)) {
            throw new ResourceNotFoundException("Catalog " + catalogId + " not found");
        }
        return tagRepository.findUsageByCatalog(catalogId, likePattern(q), Limit.of(max)).stream()
                .map(t -> new TagVO(t.getId(), t.getName(), t.getAssetCount()))
                .toList();
    }

    /** "50%_off" -> "%50\%\_off%"; blank -> null (no filter). */
    static String likePattern(String q) {
        String text = Strings.trim(q);
        if (text == null || text.isEmpty()) {
            return null;
        }
        String escaped = text.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
