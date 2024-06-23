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

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
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
    ThreadPoolExecutor consumeExecutor;
    BlockingQueue<Runnable> consumeRequestQueue;
    private List<JetStreamSubscription> consumers;

    public NatsConsumerConfig(Connection connection, ConfigurableApplicationContext applicationContext, ConsumerProperties consumerProperties, NatsMessageConverter natsMessageConverter) {
        this.connection = connection;
        this.applicationContext = applicationContext;
        this.consumerProperties = consumerProperties;
        this.natsMessageConverter = natsMessageConverter;
        this.consumeRequestQueue = new LinkedBlockingQueue<>(consumerProperties.getBlockQueueMax());
        this.consumeExecutor = new ThreadPoolExecutor(this.consumerProperties.getConsumeThreadMin(), this.consumerProperties.getConsumeThreadMax(), 1000 * 60, TimeUnit.MILLISECONDS, this.consumeRequestQueue, new ThreadFactoryImpl("NatsConsume_"));
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
        try {
            createStream(connection.jetStreamManagement(), annotation.stream(), annotation.filterSubject());
            JetStream js = connection.jetStream();
            String deliverGroup = annotation.deliverGroup();
            ConsumerConfiguration cc = ConsumerConfiguration.builder().durable(annotation.durable()).deliverSubject(annotation.deliverSubject()).ackPolicy(AckPolicy.Explicit).filterSubject(annotation.filterSubject()).deliverGroup(deliverGroup).build();
            Dispatcher dispatcher = connection.createDispatcher();
            ConsumerInfo consumerInfo = connection.jetStreamManagement().addOrUpdateConsumer(annotation.stream(), cc);
            PushSubscribeOptions so = PushSubscribeOptions.builder().stream(annotation.stream()).name(consumerInfo.getName()).deliverGroup(deliverGroup).bind(true).build();
            return js.subscribe(annotation.filterSubject(), dispatcher, msg -> {
                List<String> Sid = msg.getHeaders() == null ? null : msg.getHeaders().get("Nats-Msg-Id");
                log.info("MsgId:{}", Sid);
                invoke(natsListener, annotation, convertMessage(msg, parameterType, methodParameter), msg);
            }, false, so);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new NatsException("Initialize consumer " + beanName + " fail");
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
        if (messageType instanceof ParameterizedType && natsMessageConverter instanceof SmartMessageConverter) {
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
            throw new NatsException("parameterType:" + messageType + " of onMessage method is not supported");
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


    private void invoke(NatsListener<Object> pulsarListener, NatsMessageListener annotation, Object obj, Message msg) {
        consumeExecutor.execute(() -> {
            try {
                pulsarListener.onMessage(obj);
                if (annotation.autoAck()) {
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
            StreamConfiguration sc = StreamConfiguration.builder().name(streamName).storageType(storageType).retentionPolicy(RetentionPolicy.WorkQueue).subjects(subjects).build();
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
