package com.twofourseven.dam.services;

import com.twofourseven.dam.vo.HeartbeatVO;
import com.twofourseven.dam.vo.HeartbeatVO.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;

/** Checks the health of the running instance and its dependencies. */
@Service
public class HeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatService.class);
    private static final int DB_VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    public HeartbeatService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public HeartbeatVO check() {
        Status database = checkDatabase();
        // Overall status is UP only when every dependency is UP
        Status overall = database;
        return new HeartbeatVO(overall, database, Instant.now());
    }

    Status checkDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(DB_VALIDATION_TIMEOUT_SECONDS);
            log.debug("Database connection valid: {}", valid);
            return valid ? Status.UP : Status.DOWN;
        } catch (Exception ex) {
            log.warn("Database health check failed: {}", ex.getMessage());
            return Status.DOWN;
        }
    }
}
