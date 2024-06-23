## Explain

For the convenience of sending and receiving Nats messages, encapsulate this starter, similar in usage to RocketMQ
starter.

## Pom

```xml
 todo
```

## 基础配置

```yaml
nats:
  spring:
    server: 127.0.0.1:4222
  consumer:
    default:
      consume-thread-min: 20
      consume-thread-max: 20
      block-queue-max: 10000
```

| field              | explain                             | Default value            |
|--------------------|-------------------------------------|--------------------------|
| consume-thread-min | ThreadPoool corePoolSize            | 20                       |
| consume-thread-max | ThreadPoool maximumPoolSize         | 20                       |
| block-queue-max    | ThreadPoool BlockingQueue  capacity | Integer.MAX_VALUE - 1024 |

### Producer

```java

@Autowired
NatsTemplate natsTemplate;

public void testSend() {
    TestData data = new TestData("name1", 123, true);
    natsTemplate.publishAsync("test.push.nums", data, "msgId");
}        
```

Field Explanation

| field   | explain                                                                                                           |
|---------|-------------------------------------------------------------------------------------------------------------------|
| subject | Same consumer filterSubject                                                                                       |
| body    | Sent messages of any type                                                                                         |
| msgId   | [Nats-Msg-Id ](https://docs.nats.io/using-nats/developer/develop_jetstream/model_deep_dive#message-deduplication) |

### Consumer

```java

@Slf4j
@Service
@NatsMessageListener(deliverSubject = "test-push-handler-5", durable = "test-push-handler-5", filterSubject = "test.push.nums", stream = "TEST-PUSH-EVENTS", deliverGroup = "deliver-test-group")
public class PushTestEventsMessageHandler implements NatsListener<TestData> {
    @Override
    public void onMessage(TestData message) {
        log.info("msg:{}", message);
        //processing logic 
    }
}
```

Field Explanation

Official reference  [consumers](https://docs.nats.io/nats-concepts/jetstream/consumers)

| field          | official fields                                                                         | 说明                                                                                                   |
|----------------|-----------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| deliverSubject | DeliverSubject                                                                          | Push message subject, set to push mode only                                                          |
| durable        | Durable                                                                                 | Persistent Consumer Name                                                                             |
| filterSubject  | [filtersubjects](https://docs.nats.io/nats-concepts/jetstream/consumers#filtersubjects) | Topics that overlap with those bound to the stream, used to filter delivery to subscribers           |
| stream         | [streams name](https://docs.nats.io/nats-concepts/jetstream/streams)                    | stream name                                                                                          |
| deliverGroup   | DeliverGroup                                                                            | Consumption group name, similar to RocketMQ consumption group, same group load balancing consumption |
| autoAck        | autoAck                                                                                 | After the onMessage method is executed, it will automatically ack and default to true.               |

## Logging

By default, the msgId will be printed, and it can be added without printing

```yaml
logging:
  level:
    com.github.jarome: warn
```
