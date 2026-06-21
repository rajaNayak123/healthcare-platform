package com.healthcare.appointment.controller;

import com.healthcare.appointment.dto.AppointmentStatusMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/ws")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "WebSocket Notify", description = "Internal: push appointment status updates to connected clients")
public class WebSocketNotifyController {

    private final SimpMessagingTemplate messagingTemplate;

    @Value("${app.internal-token:default-internal-token-secret}")
    private String internalToken;

    @PostMapping("/notify")
    @Operation(summary = "Push an appointment status update over WebSocket (internal use)")
    public ResponseEntity<Void> notify(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody AppointmentStatusMessage message) {

        if (token == null || !token.equals(internalToken)) {
            log.warn("Unauthorized request to /ws/notify. Provided token: {}", token);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (message.getTimestamp() == null) {
            message.setTimestamp(LocalDateTime.now());
        }

        String destination = "/topic/appointments/" + message.getAppointmentId();
        messagingTemplate.convertAndSend(destination, message);

        log.info("WS broadcast → {} | appointmentId={} status={} eventType={}",
                destination,
                message.getAppointmentId(),
                message.getStatus(),
                message.getEventType());

        return ResponseEntity.ok().build();
    }
}
