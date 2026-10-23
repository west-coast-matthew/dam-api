package com.twofourseven.dam.vo;

import java.time.Instant;

/**
 * Response body for the /heartbeat endpoint.
 *
 * @param status   overall status, UP only when every check passes
 * @param database status of the database connection
 */
public record HeartbeatVO(Status status, Status database, Instant timestamp) {

    public enum Status { UP, DOWN }
}
