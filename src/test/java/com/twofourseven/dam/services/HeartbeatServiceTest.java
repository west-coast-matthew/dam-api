package com.twofourseven.dam.services;

import com.twofourseven.dam.vo.HeartbeatVO;
import com.twofourseven.dam.vo.HeartbeatVO.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeartbeatServiceTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @InjectMocks
    private HeartbeatService heartbeatService;

    @Test
    void reportsUpWhenConnectionIsValid() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(true);

        HeartbeatVO result = heartbeatService.check();

        assertThat(result.status()).isEqualTo(Status.UP);
        assertThat(result.database()).isEqualTo(Status.UP);
        assertThat(result.timestamp()).isNotNull();
        verify(connection).close();
    }

    @Test
    void reportsDownWhenConnectionIsInvalid() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(false);

        HeartbeatVO result = heartbeatService.check();

        assertThat(result.status()).isEqualTo(Status.DOWN);
        assertThat(result.database()).isEqualTo(Status.DOWN);
    }

    @Test
    void reportsDownWhenConnectionCannotBeOpened() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));

        HeartbeatVO result = heartbeatService.check();

        assertThat(result.status()).isEqualTo(Status.DOWN);
        assertThat(result.database()).isEqualTo(Status.DOWN);
    }
}
