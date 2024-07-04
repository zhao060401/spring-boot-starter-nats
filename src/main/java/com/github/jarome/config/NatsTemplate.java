package com.github.jarome.config;

import com.github.jarome.common.exception.NatsException;
import io.nats.client.JetStream;
import io.nats.client.PublishOptions;
import io.nats.client.api.PublishAck;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.converter.SmartMessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;

import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;

public class NatsTemplate {
    private final JetStream jetStream;
    private final NatsMessageConverter natsMessageConverter;
    private String charset = "UTF-8";

    public NatsTemplate(JetStream jetStream, NatsMessageConverter natsMessageConverter) {
        this.jetStream = jetStream;
        this.natsMessageConverter = natsMessageConverter;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    /**
     * Send a message to the specified subject and waits for a response from
     * Jetstream. The message body <strong>will not</strong> be copied. The expected
     * usage with string content is something like:
     *
     * <pre>
     * nc = Nats.connect()
     * JetStream js = nc.JetStream()
     * js.publish("destination", "message".getBytes("UTF-8"), publishOptions)
     * </pre>
     * <p>
     * where the sender creates a byte array immediately before calling publish.
     * See {@link #publish(String, byte[]) publish()} for more details on
     * publish during reconnect.
     *
     * @param subject the subject to send the message to
     * @param body    the message body
     * @param options publisher options
     * @return The acknowledgement of the publish
     */
    public PublishAck publish(String subject, byte[] body, PublishOptions options) {
        try {
            return jetStream.publish(subject, body, options);
        } catch (Exception e) {
            throw new NatsException("publish error:" + e.getMessage(), e);
        }
    }


    /**
     * push msg
     *
     * @param subject the subject to send the message to
     * @param body    the message body
     * @return The acknowledgement of the publish
     */
    public PublishAck publish(String subject, byte[] body) {
        try {
            return jetStream.publish(subject, body);
        } catch (Exception e) {
            throw new NatsException("publish error:" + e.getMessage(), e);
        }
    }

    /**
     * push msg
     *
     * @param subject the subject to send the message to
     * @param body    the message body
     * @param msgId   Nats-Msg-Id
     * @return The acknowledgement of the publish
     */
    public PublishAck publish(String subject, Object body, String msgId) {
        Message<?> message = MessageBuilder.withPayload(body).build();
        byte[] bytes = convertToBytes(message);
        return publish(subject, bytes, PublishOptions.builder().messageId(msgId).build());
    }

    public PublishAck publish(String subject, Object body) {
        Message<?> message = MessageBuilder.withPayload(body).build();
        byte[] bytes = convertToBytes(message);
        return publish(subject, bytes, PublishOptions.builder().build());
    }


    /**
     * Send a message to the specified subject but does not wait for a response from
     * Jetstream. The expected usage with string content is something like:
     *
     * <pre>
     * nc = Nats.connect()
     * JetStream js = nc.JetStream()
     * CompletableFuture&lt;PublishAck&gt; future =
     *     js.publishAsync("destination", "message".getBytes("UTF-8"), publishOptions)
     * </pre>
     * <p>
     * where the sender creates a byte array immediately before calling publish.
     * See {@link #publish(String, byte[]) publish()} for more details on
     * publish during reconnect.
     * The future me be completed with an exception, either
     * an IOException covers various communication issues with the NATS server such as timeout or interruption
     * - or - a JetStreamApiException the request had an error related to the data
     *
     * @param subject the subject to send the message to
     * @param body    the message body
     * @param options publisher options
     * @return The future
     */
    public CompletableFuture<PublishAck> publishAsync(String subject, byte[] body, PublishOptions options) {
        try {
            return jetStream.publishAsync(subject, body, options);
        } catch (Exception e) {
            throw new NatsException("publishAsync error:" + e.getMessage(), e);
        }
    }


    public CompletableFuture<PublishAck> publishAsync(String subject, byte[] body) {
        try {
            return jetStream.publishAsync(subject, body);
        } catch (Exception e) {
            throw new NatsException("publishAsync error:" + e.getMessage(), e);
        }
    }


    public CompletableFuture<PublishAck> publishAsync(String subject, Object body, String msgId) {
        Message<?> message = MessageBuilder.withPayload(body).build();
        byte[] bytes = convertToBytes(message);
        return publishAsync(subject, bytes, PublishOptions.builder().messageId(msgId).build());
    }


    public CompletableFuture<PublishAck> publishAsync(String subject, Object body) {
        Message<?> message = MessageBuilder.withPayload(body).build();
        byte[] bytes = convertToBytes(message);
        return publishAsync(subject, bytes, PublishOptions.builder().build());
    }

    /**
     * 转换成byte
     *
     * @param message 消息
     * @return byte数组
     */
    private byte[] convertToBytes(Message<?> message) {
        byte[] payloads;
        try {
            message = this.doConvert(message.getPayload(), message.getHeaders());
            Object payloadObj = message.getPayload();
            if (null == payloadObj) {
                throw new NatsException("the message cannot be empty");
            }
            if (payloadObj instanceof String) {
                payloads = ((String) payloadObj).getBytes(Charset.forName(charset));
            } else if (payloadObj instanceof byte[]) {
                payloads = (byte[]) message.getPayload();
            } else {
                String jsonObj = (String) this.natsMessageConverter.getMessageConverter().fromMessage(message, payloadObj.getClass());
                if (null == jsonObj) {
                    throw new NatsException(String.format("empty after conversion [natsMessageConverter:%s,payloadClass:%s,payloadObj:%s]", this.natsMessageConverter.getMessageConverter().getClass(), payloadObj.getClass(), payloadObj));
                }
                payloads = jsonObj.getBytes(Charset.forName(charset));
            }
        } catch (Exception e) {
            throw new NatsException("convert to bytes failed.", e);
        }
        return payloads;
    }


    private Message<?> doConvert(Object payload, MessageHeaders headers) {
        Message<?> message = this.natsMessageConverter instanceof SmartMessageConverter ? ((SmartMessageConverter) this.natsMessageConverter.getMessageConverter()).toMessage(payload, headers, null) : this.natsMessageConverter.getMessageConverter().toMessage(payload, headers);
        if (message == null) {
            String payloadType = payload.getClass().getName();
            Object contentType = headers != null ? headers.get(MessageHeaders.CONTENT_TYPE) : null;
            throw new MessageConversionException("Unable to convert payload with type='" + payloadType + "', contentType='" + contentType + "', " + "converter=[" + this.natsMessageConverter.getMessageConverter() + "]");
        }
        MessageBuilder<?> builder = MessageBuilder.fromMessage(message);
        builder.setHeaderIfAbsent(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.TEXT_PLAIN);
        return builder.build();
    }
}
