package com.github.jarome.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NatsMessageListener {
    String durable() default "";

    /**
     * push 模式加
     */
    String deliverSubject() default "";

    /**
     * 消费组 push用
     */
    String deliverGroup() default "";

    String filterSubject();

    String stream();

    /**
     * 最大重试次数
     */
    int maxDeliver() default -1;

    /**
     * 是否自动ack
     */
    boolean autoAck() default true;

    /**
     * Max consumer thread number.
     */
    int consumeThreadMax() default 64;

    /**
     * consumer thread number.
     */
    int consumeThreadNumber() default 20;

    /**
     * Set ExecutorService params -- blockingQueueSize
     */
    int blockingQueueSize() default 2000;

    /**
     * Set ExecutorService params -- keepAliveTime
     */
    long keepAliveTime() default 1000 * 60;

    /**
     * 一次拉多少
     */
    int pullBatchSize() default 10;

    /**
     * 拉取下一个消息间隔
     */
    long pullInterval() default 1000;

    /**
     * 拉取最大超时时间 单位ms
     */
    long maxWaitTime() default 3000;

    /**
     * 拉取延迟时间 单位ms
     */
    long pullDelayTime() default 0;

}
