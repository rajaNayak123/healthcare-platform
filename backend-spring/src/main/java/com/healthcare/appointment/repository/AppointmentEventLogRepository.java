package com.healthcare.appointment.repository;

import com.healthcare.appointment.entity.AppointmentEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentEventLogRepository extends JpaRepository<AppointmentEventLog, Long> {
    List<AppointmentEventLog> findByAppointmentIdOrderByCreatedAtDesc(Long appointmentId);
}
