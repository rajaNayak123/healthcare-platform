package com.healthcare.appointment.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.healthcare.appointment.entity.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentEvent {
    private Long appointmentId;
    private Long userId;
    private String userEmail;
    private String userFullName;
    private Long slotId;
    private String doctorName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private java.time.LocalDate slotDate;

    @JsonFormat(pattern = "HH:mm:ss")
    private java.time.LocalTime slotTime;

    private EventType eventType;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
}
