package com.twofourseven.dam.services;

import com.twofourseven.dam.entities.core.Catalog;
import com.twofourseven.dam.entities.core.Lightbox;
import com.twofourseven.dam.exceptions.BadRequestException;
import com.twofourseven.dam.exceptions.ResourceNotFoundException;
import com.twofourseven.dam.repositories.CatalogRepository;
import com.twofourseven.dam.repositories.LightboxRepository;
import com.twofourseven.dam.vo.LightboxInstanceDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LightboxServiceTest {

    @Mock
    private LightboxRepository lightboxRepository;
    @Mock
    private CatalogRepository catalogRepository;
    @InjectMocks
    private LightboxService service;

    private final Catalog catalog = Fixtures.catalog("Photos");
    private final UUID userId = UUID.randomUUID();

    private Lightbox lightbox() {
        Lightbox l = new Lightbox();
        l.setCatalog(catalog);
        l.setUserId(userId);
        l.setTitle("Picks");
        return l;
    }

    @Test
    void createDefaultsToNotShared() {
        when(catalogRepository.findById(catalog.getId())).thenReturn(Optional.of(catalog));
        when(lightboxRepository.save(any(Lightbox.class))).thenAnswer(inv -> inv.getArgument(0));

        LightboxInstanceDto result = service.create(new LightboxInstanceDto(null, catalog.getId(), userId, " Picks ", null, null, null));

        assertThat(result.title()).isEqualTo("Picks");
        assertThat(result.shared()).isFalse();
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.itemCount()).isZero();
    }

    @Test
    void createRequiresCatalogAndUser() {
        assertThatThrownBy(() -> service.create(new LightboxInstanceDto(null, null, userId, "T", null, null, null)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("catalogId");
        assertThatThrownBy(() -> service.create(new LightboxInstanceDto(null, catalog.getId(), null, "T", null, null, null)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("userId");
    }

    @Test
    void createInUnknownCatalogIsNotFound() {
        UUID id = UUID.randomUUID();
        when(catalogRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new LightboxInstanceDto(null, id, userId, "T", null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateChangesTitleAndSharing() {
        Lightbox existing = lightbox();
        when(lightboxRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(lightboxRepository.save(existing)).thenReturn(existing);

        LightboxInstanceDto result = service.update(existing.getId(), new LightboxInstanceDto(null, null, null, "Final", "d", true, null));

        assertThat(result.title()).isEqualTo("Final");
        assertThat(result.shared()).isTrue();
    }

    @Test
    void updateCannotChangeOwner() {
        Lightbox existing = lightbox();
        when(lightboxRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(existing.getId(),
                new LightboxInstanceDto(null, null, UUID.randomUUID(), "T", null, null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void listFiltersByCatalogAndUser() {
        when(lightboxRepository.findByCatalogIdAndUserIdOrderByTitleAsc(catalog.getId(), userId)).thenReturn(List.of(lightbox()));

        assertThat(service.list(catalog.getId(), userId)).hasSize(1);
    }

    @Test
    void delete() {
        Lightbox existing = lightbox();
        when(lightboxRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        service.delete(existing.getId());

        verify(lightboxRepository).delete(existing);
    }
}
