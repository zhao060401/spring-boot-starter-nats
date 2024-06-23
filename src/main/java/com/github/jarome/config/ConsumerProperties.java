package com.github.jarome.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Component
@ConfigurationProperties(prefix = "nats.consumer.default")
public class ConsumerProperties {
    /**
     * Minimum consumer thread number
     */
    private int consumeThreadMin = 20;

    /**
     * Max consumer thread number
     */
    private int consumeThreadMax = 20;

    /**
     * the capacity of the LinkedBlockingQueue in ThreadPoolExecutor,that use to consumer thread
     */
    private int blockQueueMax = Integer.MAX_VALUE - 1024;
    
    public int getConsumeThreadMin() {
        return consumeThreadMin;
    }

    public void setConsumeThreadMin(int consumeThreadMin) {
        this.consumeThreadMin = consumeThreadMin;
    }

    public int getConsumeThreadMax() {
        return consumeThreadMax;
    }

    public void setConsumeThreadMax(int consumeThreadMax) {
        this.consumeThreadMax = consumeThreadMax;
    }

    public int getBlockQueueMax() {
        return blockQueueMax;
    }

    public void setBlockQueueMax(int blockQueueMax) {
        this.blockQueueMax = blockQueueMax;
    }
}