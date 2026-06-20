package com.healthcare.appointment.event;

import com.healthcare.appointment.config.KafkaTopicConfig;
import com.healthcare.appointment.entity.OutboxEvent;
import com.healthcare.appointment.entity.OutboxStatus;
import com.healthcare.appointment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final int BATCH_SIZE = 50;

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay-ms:2000}")
    public void publishPendingEvents() {
        List<OutboxEvent> pending = fetchPendingBatch();
        if (pending.isEmpty()) {
            return;
        }

        log.debug("Outbox poll: found {} PENDING event(s)", pending.size());

        for (OutboxEvent event : pending) {
            publishOne(event);
        }
    }

    @Transactional
    protected List<OutboxEvent> fetchPendingBatch() {
        return outboxRepository.findPendingForUpdate(BATCH_SIZE);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    protected void publishOne(OutboxEvent event) {
        try {
            kafkaTemplate
                .send(event.getTopic(), event.getAggregateId().toString(), event.getPayload())
                .get();

            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(LocalDateTime.now());
            outboxRepository.save(event);

            log.info(
                "Outbox published — id={} aggregateId={} eventType={}",
                event.getId(), event.getAggregateId(), event.getEventType()
            );

        } catch (Exception ex) {
            event.setStatus(OutboxStatus.FAILED);
            event.setFailureReason(ex.getMessage());
            outboxRepository.save(event);

            log.error(
                "Outbox publish FAILED — id={} aggregateId={} eventType={} reason={}",
                event.getId(), event.getAggregateId(), event.getEventType(), ex.getMessage()
            );
        }
    }
}
