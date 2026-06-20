package com.healthcare.appointment.service;

import com.healthcare.appointment.dto.AppointmentResponse;
import com.healthcare.appointment.dto.CreateAppointmentRequest;
import com.healthcare.appointment.dto.SlotResponse;
import com.healthcare.appointment.entity.*;
import com.healthcare.appointment.event.AppointmentEvent;
import com.healthcare.appointment.event.AppointmentEventProducer;
import com.healthcare.appointment.exception.DuplicateBookingException;
import com.healthcare.appointment.exception.ResourceNotFoundException;
import com.healthcare.appointment.exception.SlotUnavailableException;
import com.healthcare.appointment.repository.AppointmentRepository;
import com.healthcare.appointment.repository.SlotRepository;
import com.healthcare.appointment.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final SlotRepository slotRepository;
    private final UserRepository userRepository;
    private final AppointmentEventProducer eventProducer;

    private static final List<AppointmentStatus> ACTIVE_STATUSES =
            List.of(AppointmentStatus.PENDING, AppointmentStatus.BOOKED, AppointmentStatus.NOTIFIED);

    @Transactional
    public AppointmentResponse createAppointment(Long userId, CreateAppointmentRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Slot slot = slotRepository.findByIdForUpdate(request.getSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found"));

        if (Boolean.TRUE.equals(slot.getIsBooked())) {
            throw new SlotUnavailableException("This slot has already been booked. Please choose another slot.");
        }

        if (appointmentRepository.existsBySlotIdAndStatusIn(slot.getId(), ACTIVE_STATUSES)) {
            throw new DuplicateBookingException("This slot is already reserved.");
        }

        slot.setIsBooked(true);
        slotRepository.save(slot);

        Appointment appointment = Appointment.builder()
                .user(user)
                .slot(slot)
                .status(AppointmentStatus.BOOKED)
                .build();
        appointment = appointmentRepository.save(appointment);

        AppointmentEvent event = AppointmentEvent.builder()
                .appointmentId(appointment.getId())
                .userId(user.getId())
                .userEmail(user.getEmail())
                .userFullName(user.getFullName())
                .slotId(slot.getId())
                .doctorName(slot.getDoctorName())
                .slotDate(slot.getSlotDate())
                .slotTime(slot.getStartTime())
                .eventType(EventType.APPOINTMENT_CREATED)
                .timestamp(LocalDateTime.now())
                .build();
        eventProducer.publish(event);

        log.info("Appointment {} created for user {}", appointment.getId(), userId);

        return toResponse(appointment);
    }

    @Transactional
    public void cancelAppointment(Long userId, Long appointmentId) {
        Appointment appointment = appointmentRepository.findByIdAndUserId(appointmentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new SlotUnavailableException("Appointment is already cancelled");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.save(appointment);

        Slot slot = slotRepository.findByIdForUpdate(appointment.getSlot().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found"));
        slot.setIsBooked(false);
        slotRepository.save(slot);

        AppointmentEvent event = AppointmentEvent.builder()
                .appointmentId(appointment.getId())
                .userId(userId)
                .userEmail(appointment.getUser().getEmail())
                .userFullName(appointment.getUser().getFullName())
                .slotId(slot.getId())
                .doctorName(slot.getDoctorName())
                .slotDate(slot.getSlotDate())
                .slotTime(slot.getStartTime())
                .eventType(EventType.APPOINTMENT_CANCELLED)
                .timestamp(LocalDateTime.now())
                .build();
        eventProducer.publish(event);

        log.info("Appointment {} cancelled by user {}", appointmentId, userId);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getUserAppointments(Long userId) {
        return appointmentRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toResponse).toList();
    }

    private AppointmentResponse toResponse(Appointment appointment) {
        Slot slot = appointment.getSlot();
        SlotResponse slotResponse = SlotResponse.builder()
                .id(slot.getId())
                .doctorName(slot.getDoctorName())
                .specialization(slot.getSpecialization())
                .slotDate(slot.getSlotDate())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .isBooked(slot.getIsBooked())
                .build();

        return AppointmentResponse.builder()
                .id(appointment.getId())
                .userId(appointment.getUser().getId())
                .patientName(appointment.getUser().getFullName())
                .slot(slotResponse)
                .status(appointment.getStatus().name())
                .createdAt(appointment.getCreatedAt())
                .updatedAt(appointment.getUpdatedAt())
                .build();
    }
}
