package com.healthcare.appointment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateAppointmentRequest {

    @NotNull(message = "Slot id is required")
    private Long slotId;
}
