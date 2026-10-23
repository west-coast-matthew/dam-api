package com.twofourseven.dam.controllers;

import com.twofourseven.dam.services.HeartbeatService;
import com.twofourseven.dam.vo.HeartbeatVO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health check for the running instance.
 * Returns 200 when every check passes, 503 when any check fails.
 */
@RestController
public class HeartbeatController {

    private final HeartbeatService heartbeatService;

    public HeartbeatController(HeartbeatService heartbeatService) {
        this.heartbeatService = heartbeatService;
    }

    @GetMapping("/heartbeat")
    public ResponseEntity<HeartbeatVO> heartbeat() {
        HeartbeatVO result = heartbeatService.check();
        HttpStatus status = result.status() == HeartbeatVO.Status.UP ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(result);
    }
}
