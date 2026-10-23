package com.twofourseven.dam.controllers;

import com.twofourseven.dam.exceptions.GlobalExceptionHandler;
import com.twofourseven.dam.services.HeartbeatService;
import com.twofourseven.dam.vo.HeartbeatVO;
import com.twofourseven.dam.vo.HeartbeatVO.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web tier unit test: the service is mocked, so no database is needed. */
@ExtendWith(MockitoExtension.class)
class HeartbeatControllerTest {

    @Mock
    private HeartbeatService heartbeatService;

    @InjectMocks
    private HeartbeatController heartbeatController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(heartbeatController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returns200WhenHealthy() throws Exception {
        when(heartbeatService.check()).thenReturn(new HeartbeatVO(Status.UP, Status.UP, Instant.now()));

        mockMvc.perform(get("/heartbeat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"));
    }

    @Test
    void returns503WhenDatabaseIsDown() throws Exception {
        when(heartbeatService.check()).thenReturn(new HeartbeatVO(Status.DOWN, Status.DOWN, Instant.now()));

        mockMvc.perform(get("/heartbeat"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.database").value("DOWN"));
    }

    @Test
    void returns500WithSafeMessageOnUnexpectedError() throws Exception {
        when(heartbeatService.check()).thenThrow(new IllegalStateException("secret internal detail"));

        mockMvc.perform(get("/heartbeat"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.path").value("/heartbeat"));
    }

    @Test
    void returns405ForWrongMethod() throws Exception {
        mockMvc.perform(post("/heartbeat"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }
}
