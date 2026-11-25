package com.twofourseven.dam.services;

import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.ResourceNotFoundException;
import com.twofourseven.dam.repositories.CatalogRepository;
import com.twofourseven.dam.repositories.TagRepository;
import com.twofourseven.dam.repositories.TagUsage;
import com.twofourseven.dam.vo.TagVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;
    @Mock
    private CatalogRepository catalogRepository;
    @InjectMocks
    private TagService service;

    private final UUID catalogId = UUID.randomUUID();

    private static TagUsage usage(UUID id, String name, long count) {
        return new TagUsage() {
            public UUID getId() { return id; }
            public String getName() { return name; }
            public long getAssetCount() { return count; }
        };
    }

    @Test
    void listsTagsWithCountsUsingTheDefaultLimit() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(catalogRepository.existsById(catalogId)).thenReturn(true);
        when(tagRepository.findUsageByCatalog(catalogId, null, Limit.of(TagService.DEFAULT_LIMIT)))
                .thenReturn(List.of(usage(a, "brand", 3), usage(b, "Unused", 0)));

        assertThat(service.list(catalogId, "  ", null))
                .containsExactly(new TagVO(a, "brand", 3), new TagVO(b, "Unused", 0));
    }

    @Test
    void filtersWithAnEscapedCaseInsensitivePatternAndLimit() {
        when(catalogRepository.existsById(catalogId)).thenReturn(true);
        when(tagRepository.findUsageByCatalog(catalogId, "%50\\%\\_off%", Limit.of(5))).thenReturn(List.of());

        assertThat(service.list(catalogId, " 50%_OFF ", 5)).isEmpty();
    }

    @Test
    void likePatternEscapesWildcards() {
        assertThat(TagService.likePattern(null)).isNull();
        assertThat(TagService.likePattern("Sky")).isEqualTo("%sky%");
        assertThat(TagService.likePattern("a\\b")).isEqualTo("%a\\\\b%");
    }

    @Test
    void unknownCatalogIsNotFound() {
        when(catalogRepository.existsById(catalogId)).thenReturn(false);

        assertThatThrownBy(() -> service.list(catalogId, null, null)).isInstanceOf(ResourceNotFoundException.class);
        verify(tagRepository, never()).findUsageByCatalog(any(), isNull(), any());
    }

    @Test
    void limitMustBeBetween1And200() {
        assertThatThrownBy(() -> service.list(catalogId, null, 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.list(catalogId, null, 201)).isInstanceOf(BadRequestException.class);
        verify(catalogRepository, never()).existsById(any());
    }
}
