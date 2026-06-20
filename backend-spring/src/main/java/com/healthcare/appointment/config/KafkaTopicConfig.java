package com.healthcare.appointment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String APPOINTMENT_EVENTS_TOPIC = "appointment-events";

    @Bean
    public NewTopic appointmentEventsTopic() {
        return TopicBuilder.name(APPOINTMENT_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
