package com.kenyarealestate.review.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Bean public NewTopic verificationEvents() {
        return TopicBuilder.name("verification-events").partitions(3).replicas(1).build();
    }
    @Bean public NewTopic paymentEvents() {
        return TopicBuilder.name("payment-events").partitions(3).replicas(1).build();
    }
    @Bean public NewTopic propertyEvents() {
        return TopicBuilder.name("property-events").partitions(3).replicas(1).build();
    }
    @Bean public NewTopic viewingEvents() {
        return TopicBuilder.name("viewing-events").partitions(3).replicas(1).build();
    }
    @Bean public NewTopic reviewEvents() {
        return TopicBuilder.name("review-events").partitions(3).replicas(1).build();
    }

    @Bean public NewTopic verificationEventsDlt() { return TopicBuilder.name("verification-events.DLT").partitions(1).replicas(1).build(); }
    @Bean public NewTopic paymentEventsDlt()       { return TopicBuilder.name("payment-events.DLT").partitions(1).replicas(1).build(); }
    @Bean public NewTopic propertyEventsDlt()      { return TopicBuilder.name("property-events.DLT").partitions(1).replicas(1).build(); }
    @Bean public NewTopic viewingEventsDlt()       { return TopicBuilder.name("viewing-events.DLT").partitions(1).replicas(1).build(); }
    @Bean public NewTopic reviewEventsDlt()        { return TopicBuilder.name("review-events.DLT").partitions(1).replicas(1).build(); }
}
