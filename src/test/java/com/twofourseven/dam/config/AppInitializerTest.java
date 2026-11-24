package com.twofourseven.dam.config;

import com.twofourseven.dam.services.AppInitializationService;
import com.twofourseven.dam.vo.WorkspaceVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppInitializerTest {

    @Mock
    private AppInitializationService initializationService;

    @InjectMocks
    private AppInitializer initializer;

    @Test
    void stagesInitialDataWhenNoWorkspaceExists() {
        when(initializationService.isInitialized()).thenReturn(false);
        when(initializationService.initialize())
                .thenReturn(new WorkspaceVO(UUID.randomUUID(), "default", null, LocalDateTime.now(), List.of()));

        initializer.onApplicationReady();

        verify(initializationService).initialize();
    }

    @Test
    void skipsWhenWorkspacesExist() {
        when(initializationService.isInitialized()).thenReturn(true);

        initializer.onApplicationReady();

        verify(initializationService, never()).initialize();
    }

    @Test
    void doesNotFailStartupWhenTheDatabaseIsDown() {
        when(initializationService.isInitialized())
                .thenThrow(new DataAccessResourceFailureException("Connection refused"));

        assertThatCode(() -> initializer.onApplicationReady()).doesNotThrowAnyException();
        verify(initializationService, never()).initialize();
    }
}
