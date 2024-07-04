package com.github.jarome.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
@ConfigurationProperties(prefix = "nats")
public class ConsumerProperties {

    /**
     * key:subject
     */
    private List<PullConsumer> consumers;

    public List<PullConsumer> getConsumers() {
        return consumers;
    }

    public void setConsumers(List<PullConsumer> consumers) {
        this.consumers = consumers;
    }
}