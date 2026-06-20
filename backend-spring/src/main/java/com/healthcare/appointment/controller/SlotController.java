package com.healthcare.appointment.controller;

import com.healthcare.appointment.dto.SlotResponse;
import com.healthcare.appointment.service.SlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/slots")
@RequiredArgsConstructor
@Tag(name = "Slots", description = "Fetch available appointment slots")
public class SlotController {

    private final SlotService slotService;

    @GetMapping("/available")
    @Operation(summary = "Fetch available appointment slots, optionally filtered by date")
    public ResponseEntity<List<SlotResponse>> getAvailableSlots(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(slotService.getAvailableSlots(date));
    }
}
