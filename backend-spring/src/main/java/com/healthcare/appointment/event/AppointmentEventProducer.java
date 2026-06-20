package com.healthcare.appointment.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.appointment.config.KafkaTopicConfig;
import com.healthcare.appointment.entity.AppointmentEventLog;
import com.healthcare.appointment.entity.EventStatus;
import com.healthcare.appointment.entity.EventType;
import com.healthcare.appointment.entity.OutboxEvent;
import com.healthcare.appointment.repository.AppointmentEventLogRepository;
import com.healthcare.appointment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AppointmentEventProducer {

    private final AppointmentEventLogRepository eventLogRepository;
    private final OutboxEventRepository         outboxEventRepository;
    private final ObjectMapper                  objectMapper;

    public void publish(AppointmentEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);

            AppointmentEventLog logEntry = AppointmentEventLog.builder()
                    .appointmentId(event.getAppointmentId())
                    .eventType(event.getEventType())
                    .eventStatus(EventStatus.PUBLISHED)
                    .payload(payload)
                    .build();
            eventLogRepository.save(logEntry);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateId(event.getAppointmentId())
                    .aggregateType("Appointment")
                    .eventType(event.getEventType())
                    .topic(KafkaTopicConfig.APPOINTMENT_EVENTS_TOPIC)
                    .payload(payload)
                    .build();
            outboxEventRepository.save(outboxEvent);

            log.info(
                "Outbox event queued — appointmentId={} eventType={} outboxId={}",
                event.getAppointmentId(), event.getEventType(), outboxEvent.getId()
            );

        } catch (Exception e) {
            log.error("Failed to persist outbox event for appointment {}", event.getAppointmentId(), e);
            throw new RuntimeException("Failed to queue appointment event", e);
        }
    }
}
