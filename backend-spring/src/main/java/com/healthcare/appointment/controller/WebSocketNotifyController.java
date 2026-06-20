package com.healthcare.appointment.controller;

import com.healthcare.appointment.dto.AppointmentStatusMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/ws")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "WebSocket Notify", description = "Internal: push appointment status updates to connected clients")
public class WebSocketNotifyController {

    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("/notify")
    @Operation(summary = "Push an appointment status update over WebSocket (internal use)")
    public ResponseEntity<Void> notify(@Valid @RequestBody AppointmentStatusMessage message) {

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
