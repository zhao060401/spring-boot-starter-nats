package com.github.jarome.config;

import com.github.jarome.annotation.NatsListener;
import com.github.jarome.annotation.NatsMessageListener;
import com.github.jarome.common.exception.NatsException;
import io.nats.client.*;
import io.nats.client.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.MethodParameter;
import org.springframework.messaging.converter.SmartMessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Configuration
@DependsOn({"natsConnection", "natsMessageConverter"})
public class NatsConsumerConfig implements ApplicationContextAware, SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(NatsConsumerConfig.class);
    Connection connection;
    ConfigurableApplicationContext applicationContext;
    NatsMessageConverter natsMessageConverter;
    ConsumerProperties consumerProperties;
    private List<JetStreamSubscription> consumers;

    public NatsConsumerConfig(Connection connection, ConfigurableApplicationContext applicationContext, ConsumerProperties consumerProperties, NatsMessageConverter natsMessageConverter) {
        this.connection = connection;
        this.applicationContext = applicationContext;
        this.consumerProperties = consumerProperties;
        this.natsMessageConverter = natsMessageConverter;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, Object> beans = this.applicationContext.getBeansWithAnnotation(NatsMessageListener.class).entrySet().stream().filter(entry -> !ScopedProxyUtils.isScopedTarget(entry.getKey())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        consumers = beans.entrySet().stream().map(e -> registerContainer(e.getKey(), e.getValue())).collect(Collectors.toList());
    }

    private JetStreamSubscription registerContainer(String beanName, Object bean) {
        Class<?> clazz = AopProxyUtils.ultimateTargetClass(bean);
        if (!NatsListener.class.isAssignableFrom(bean.getClass())) {
            throw new NatsException(clazz + " don't implement" + NatsMessageListener.class.getName() + " quick change it");
        }
        Type parameterType = getParameterType(clazz);
        MethodParameter methodParameter = getMethodParameter(clazz, parameterType);
        NatsListener<Object> natsListener = ((NatsListener<Object>) bean);
        NatsMessageListener annotation = clazz.getAnnotation(NatsMessageListener.class);
        boolean autoAck = annotation.autoAck();
        int threadMax = annotation.consumeThreadMax();
        int threadNumber = annotation.consumeThreadNumber();
        int queueSize = annotation.blockingQueueSize();
        long pullInterval = annotation.pullInterval();
        long maxWaitTime = annotation.maxWaitTime();
        long keepAliveTime = annotation.keepAliveTime();
        int pullBatchSize = annotation.pullBatchSize();
        String stream = annotation.stream();
        List<PullConsumer> consumers = consumerProperties.getConsumers();
        if (consumers != null && !consumers.isEmpty()) {
            for (PullConsumer pullConsumer : consumers) {
                if (pullConsumer.getSubject().equals(annotation.filterSubject())) {
                    autoAck = pullConsumer.getAutoAck() == null ? autoAck : pullConsumer.getAutoAck();
                    threadMax = pullConsumer.getConsumeThreadMax() == null ? threadMax : pullConsumer.getConsumeThreadMax();
                    threadNumber = pullConsumer.getConsumeThreadNumber() == null ? threadNumber : pullConsumer.getConsumeThreadNumber();
                    queueSize = pullConsumer.getBlockingQueueSize() == null ? queueSize : pullConsumer.getBlockingQueueSize();
                    pullInterval = pullConsumer.getPullInterval() == null ? pullInterval : Math.max(1, pullConsumer.getPullInterval());
                    maxWaitTime = pullConsumer.getMaxWaitTime() == null ? maxWaitTime : Math.max(1, pullConsumer.getMaxWaitTime());
                    keepAliveTime = pullConsumer.getKeepAliveTime() == null ? keepAliveTime : pullConsumer.getKeepAliveTime();
                    pullBatchSize = pullConsumer.getPullBatchSize() == null ? pullBatchSize : Math.max(1, pullConsumer.getPullBatchSize());
                }
            }
        }
        try {
            createStream(connection.jetStreamManagement(), stream, annotation.filterSubject());
            JetStream js = connection.jetStream();
            String deliverGroup = annotation.deliverGroup();
            String deliverSubject = annotation.deliverSubject();
            boolean isPost = false;
            ConsumerConfiguration.Builder builder = ConsumerConfiguration.builder().durable(annotation.durable()).deliverSubject(deliverSubject).ackPolicy(AckPolicy.Explicit).filterSubject(annotation.filterSubject()).maxDeliver(annotation.maxDeliver());
            if (StringUtils.hasLength(deliverSubject) && StringUtils.hasLength(deliverGroup)) {
                //post
                builder.deliverSubject(deliverSubject);
                builder.deliverGroup(deliverGroup);
                isPost = true;
            }
            ConsumerConfiguration cc = builder.build();
            ConsumerInfo consumerInfo = connection.jetStreamManagement().addOrUpdateConsumer(stream, cc);
            ThreadPoolExecutor threadPool = new ThreadPoolExecutor(threadNumber, threadMax, keepAliveTime, TimeUnit.MILLISECONDS, new LinkedBlockingDeque<>(queueSize));
            if (isPost) {
                Dispatcher dispatcher = connection.createDispatcher();
                PushSubscribeOptions so = PushSubscribeOptions.builder().stream(stream).name(consumerInfo.getName()).deliverGroup(deliverGroup).bind(true).build();
                boolean finalAutoAck = autoAck;
                return js.subscribe(annotation.filterSubject(), dispatcher, msg -> invoke(natsListener, finalAutoAck, convertMessage(msg, parameterType, methodParameter), msg, threadPool), false, so);
            } else {
                //pull
                PullSubscribeOptions so = PullSubscribeOptions.builder().stream(stream).name(consumerInfo.getName()).bind(true).build();
                JetStreamSubscription sub = js.subscribe(annotation.filterSubject(), so);
                long finalMaxWaitTime = maxWaitTime;
                long finalPullInterval = pullInterval;
                boolean finalAutoAck1 = autoAck;
                int finalPullBatchSize = pullBatchSize;
                new Thread(() -> {
                    try {
                        while (true) {
                            List<Message> msgList = sub.fetch(finalPullBatchSize, finalMaxWaitTime);
                            if (msgList == null || msgList.isEmpty()) {
                                sub.nextMessage(Duration.ofMillis(finalPullInterval));
                                continue;
                            }
                            for (Message msg : msgList) {
                                invoke(natsListener, finalAutoAck1, convertMessage(msg, parameterType, methodParameter), msg, threadPool);
                            }
                        }
                    } catch (Exception e) {
                        //Exceptions cannot be thrown or the thread will interrupt
                        log.error("Pull Message Service Run Method exception", e);
                    }
                }).start();
                return sub;
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new NatsException("Initialize consumer " + beanName + " fail", e);
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = (ConfigurableApplicationContext) applicationContext;
    }

    /**
     * Get the input parameter type of the interface
     *
     * @param clazz class
     * @return Types of formal parameters of the interface
     */
    private Type getParameterType(Class<?> clazz) {
        Type matchedGenericInterface = null;
        while (Objects.nonNull(clazz)) {
            Type[] interfaces = clazz.getGenericInterfaces();
            for (Type type : interfaces) {
                if (type instanceof ParameterizedType && (Objects.equals(((ParameterizedType) type).getRawType(), NatsListener.class))) {
                    matchedGenericInterface = type;
                    break;
                }
            }
            clazz = clazz.getSuperclass();
        }
        if (Objects.isNull(matchedGenericInterface)) {
            return Object.class;
        }
        Type[] actualTypeArguments = ((ParameterizedType) matchedGenericInterface).getActualTypeArguments();
        if (Objects.nonNull(actualTypeArguments) && actualTypeArguments.length > 0) {
            return actualTypeArguments[0];
        }
        return Object.class;
    }

    private MethodParameter getMethodParameter(Class<?> targetClass, Type messageType) {
        Class clazz;
        if (messageType instanceof ParameterizedType && natsMessageConverter.getMessageConverter() instanceof SmartMessageConverter) {
            clazz = (Class) ((ParameterizedType) messageType).getRawType();
        } else if (messageType instanceof Class) {
            clazz = (Class) messageType;
        } else {
            throw new NatsException("parameterType:" + messageType + " of onMessage method is not supported");
        }
        try {
            final Method method = targetClass.getMethod("onMessage", clazz);
            return new MethodParameter(method, 0);
        } catch (NoSuchMethodException e) {
            log.error(e.getMessage(), e);
            throw new NatsException("parameterType:" + messageType + " of onMessage method is not supported", e);
        }
    }

    private Object convertMessage(Message msg, Type parameterType, MethodParameter methodParameter) {
        String value = new String(msg.getData(), StandardCharsets.UTF_8);
        if (Objects.equals(parameterType, String.class)) {
            return value;
        }
        try {
            if (parameterType instanceof Class) {
                return natsMessageConverter.getMessageConverter().fromMessage(MessageBuilder.withPayload(value).build(), (Class<?>) parameterType);
            } else {
                //有泛型的
                return ((SmartMessageConverter) natsMessageConverter.getMessageConverter()).fromMessage(MessageBuilder.withPayload(value).build(), (Class<?>) ((ParameterizedType) parameterType).getRawType(), methodParameter);
            }
        } catch (Exception e) {
            log.error("convert failed. str:{}, msgType:{}", value, parameterType);
            throw new NatsException("cannot convert message to " + parameterType, e);
        }
    }


    private void invoke(NatsListener<Object> pulsarListener, boolean autoAck, Object obj, Message msg, ThreadPoolExecutor threadPoolExecutor) {
        threadPoolExecutor.execute(() -> {
            try {
                pulsarListener.onMessage(obj);
                if (autoAck) {
                    msg.ack();
                }
            } catch (Exception e) {
                msg.nak();
                log.error(e.getMessage(), e);
                throw new NatsException("invoke msg failed:" + e.getMessage(), e);
            }
        });
    }

    private StreamInfo createStream(JetStreamManagement jsm, String streamName, String... subjects) {
        StreamInfo streamInfo = getStreamInfo(jsm, streamName);
        if (streamInfo == null) {
            //创建
            log.info("not exist stream {} with subject(s) {}", streamName, subjects);
            streamInfo = createStream(jsm, streamName, StorageType.File, subjects);
        }
        return streamInfo;
    }


    private StreamInfo getStreamInfo(JetStreamManagement jsm, String streamName) {
        try {
            return jsm.getStreamInfo(streamName);
        } catch (IOException e) {
            throw new NatsException("cannot get stream info: " + e.getMessage(), e);
        } catch (JetStreamApiException e) {
            if (e.getErrorCode() == 404) {
                return null;
            }
        }
        return null;
    }

    public static StreamInfo createStream(JetStreamManagement jsm, String streamName, StorageType storageType, String... subjects) {
        try {
            StreamConfiguration sc = StreamConfiguration.builder().name(streamName).storageType(storageType).maxAge(Duration.ofDays(2)).retentionPolicy(RetentionPolicy.WorkQueue).subjects(subjects).build();
            StreamInfo si = jsm.addStream(sc);
            log.info("Created stream {} with subject(s) {}", streamName, si.getConfiguration().getSubjects());
            return si;
        } catch (Exception e) {
            throw new NatsException("create stream error: " + e.getMessage(), e);
        }
    }

    public List<JetStreamSubscription> getConsumers() {
        return consumers;
    }
}
