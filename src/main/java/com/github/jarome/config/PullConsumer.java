package com.github.jarome.config;

public class PullConsumer {
    private String subject;
    /*
     * 是否自动ack
     */
    private Boolean autoAck;

    /**
     * Max consumer thread number.
     */
    private Integer consumeThreadMax;

    /**
     * consumer thread number.
     */
    private Integer consumeThreadNumber;

    /**
     * Set ExecutorService params -- blockingQueueSize
     */
    private Integer blockingQueueSize;

    /**
     * 一次拉多少
     */
    private Integer pullBatchSize;

    /**
     * 拉取间隔
     */
    private Long pullInterval;

    /**
     * 存活时间
     */
    private Long keepAliveTime;

    /**
     * 拉取延迟
     */
    private Long maxWaitTime;

    /**
     * 拉取代码首次延迟
     */
    private Long pullDelayTime;

    public Boolean getAutoAck() {
        return autoAck;
    }

    public void setAutoAck(Boolean autoAck) {
        this.autoAck = autoAck;
    }

    public Integer getConsumeThreadMax() {
        return consumeThreadMax;
    }

    public void setConsumeThreadMax(Integer consumeThreadMax) {
        this.consumeThreadMax = consumeThreadMax;
    }

    public Integer getConsumeThreadNumber() {
        return consumeThreadNumber;
    }

    public void setConsumeThreadNumber(Integer consumeThreadNumber) {
        this.consumeThreadNumber = consumeThreadNumber;
    }

    public Integer getBlockingQueueSize() {
        return blockingQueueSize;
    }

    public void setBlockingQueueSize(Integer blockingQueueSize) {
        this.blockingQueueSize = blockingQueueSize;
    }

    public Integer getPullBatchSize() {
        return pullBatchSize;
    }

    public void setPullBatchSize(Integer pullBatchSize) {
        this.pullBatchSize = pullBatchSize;
    }

    public Long getPullInterval() {
        return pullInterval;
    }

    public void setPullInterval(Long pullInterval) {
        this.pullInterval = pullInterval;
    }

    public Long getMaxWaitTime() {
        return maxWaitTime;
    }

    public void setMaxWaitTime(Long maxWaitTime) {
        this.maxWaitTime = maxWaitTime;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Long getKeepAliveTime() {
        return keepAliveTime;
    }

    public void setKeepAliveTime(Long keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    public Long getPullDelayTime() {
        return pullDelayTime;
    }

    public void setPullDelayTime(Long pullDelayTime) {
        this.pullDelayTime = pullDelayTime;
    }
}
