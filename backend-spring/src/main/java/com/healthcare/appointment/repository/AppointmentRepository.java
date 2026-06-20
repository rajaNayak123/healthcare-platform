package com.healthcare.appointment.repository;

import com.healthcare.appointment.entity.Appointment;
import com.healthcare.appointment.entity.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsBySlotIdAndStatusIn(Long slotId, List<AppointmentStatus> statuses);

    Optional<Appointment> findByIdAndUserId(Long id, Long userId);
}
