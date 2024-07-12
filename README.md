## Explain
For the convenience of sending and receiving Nats messages, encapsulate this starter, similar in usage to RocketMQ starter.
## pom
```xml
<dependency>
  <groupId>com.github.jarome</groupId>
  <artifactId>spring-boot-starter-nats</artifactId>
  <version>0.0.2</version>
</dependency>
```
## Basic Configuration
```yaml
nats:
  spring:
    server: 127.0.0.1:4222
  consumers:
    - subject: "push.event.nums"
      autoAck: true
      consumeThreadNumber: 20
      consumeThreadMax: 64
      blockingQueueSize: 2000
      pullBatchSize: 10
      pullInterval: 1000
      keepAliveTime: 60000
      maxWaitTime: 0
    - subject: "push.event.nums2"
      autoAck: true
      consumeThreadMax: 64

```
Consumers parameter
The default parameter is the configuration in `@NatsMessageListener`
If no modifications are needed, the entire consumers can be left blank

| Field               | Explain                                             | Default                |
|---------------------|-----------------------------------------------------|------------------------|
| subject             | subject                                             |
| autoAck             | can auto ack                                        | true                   |
| consumeThreadNumber | ThreadPoool corePoolSize                            | 20                     |
| consumeThreadMax    | ThreadPoool maximumPoolSize                         | 64                     |
| blockingQueueSize   | ThreadPoool BlockingQueue  capacity                 | 2000                   |
| keepAliveTime       | ThreadPoool keepAliveTime                           | 1000*60 ms             |
| pullBatchSize       | pulls the fetch each time in pull mode              | 10                     |
| pullInterval        | pull interval when there is no message in pull mode | 1000ms                 |
| maxWaitTime         | pull timeout in pull mode          | 3000ms （According to the Nats limit, the actual minimum is 1ms） |
| pullDelayTime         | pull time delay in pull mode           | 0ms （According to the Nats limit, the actual minimum is 1ms） |

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

| Field | Explain                                                                                                            |
| --- |--------------------------------------------------------------------------------------------------------------------|
| subject | Same consumer filterSubject                                                                                        |
| body | Sent messages of any type                                                                                          |
| msgId | [Nats-Msg-Id ](https://docs.nats.io/using-nats/developer/develop_jetstream/model_deep_dive#message-deduplication) Reuse, default to 2 minute interval for deduplication |



### Consumer
```java
@Slf4j
@Service
@NatsMessageListener(deliverSubject = "test-push-handler-5", durable = "test-push-handler-5", filterSubject = "test.push.nums", stream = "TEST-PUSH-EVENTS", deliverGroup = "deliver-test-group")
public class PushTestEventsMessageHandler implements NatsListener<TestData> {
    @Override
    public void onMessage(TestData message) {
        log.info("PUSH call data:{}", message);
        //Process Logic
    }
}

@Slf4j
@Service
@NatsMessageListener(durable = "test-pull-handler", filterSubject = "test.pull.nums", stream = "TEST-PULL-EVENTS")
public class PushTestEventsMessageHandler implements NatsListener<TestData> {
    @Override
    public void onMessage(TestData message) {
        log.info("Pull call data:{}", message);
        //Process Logic
    }
}
```

Field Explanation
Official reference [consumers](https://docs.nats.io/nats-concepts/jetstream/consumers)   Both deliverSubject and deliverGroup are set to push mode and not to pull mode

| Field | Official Fields     | Explain                                                                                                                     |
| --- |-----------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| deliverSubject | DeliverSubject                                                                          | Push message subject, set to push mode only                                                                                 |
| deliverGroup | DeliverGroup                                                                            | Consumption group name, similar to RocketMQ consumption group, same group load balancing consumption, set to push mode only |
| durable | Durable                                                                                 | durable consumer name                                                                                                       |
| filterSubject | [filtersubjects](https://docs.nats.io/nats-concepts/jetstream/consumers#filtersubjects) | Topics that overlap with those bound to the stream, used to filter delivery to subscribers                                  |
| stream | [streams name](https://docs.nats.io/nats-concepts/jetstream/streams)                    | stream name                                                                                                                 |
| autoAck | autoAck                                                                                 | After the onMessage method is executed, it will automatically ack and default to true.                                                                                           |
| maxDeliver | MaxDeliver                                                                              | The maximum number of attempts to deliver a specific message. Default -1                                                                                                       |
| consumeThreadNumber | ThreadPoool corePoolSize                                                                                | 20                                                                                                                          |
| consumeThreadMax | ThreadPoool maximumPoolSize                                                                            | 64                                                                                                                          |
| blockingQueueSize | ThreadPoool BlockingQueue  capacity                                                                         | 2000                                                                                                                        |
| keepAliveTime | ThreadPoool keepAliveTime                                                                           | 1000*60 ms                                                                                                                  |
| pullBatchSize       | pulls the fetch each time in pull mode              | 10                                                                                                                          |
| pullInterval        | pull interval when there is no message in pull mode | 1000ms                                                                                                                      |
| maxWaitTime         | first pull time delay in pull mode          | 0ms （According to the Nats limit, the actual minimum is                                                                     

## log
By default, the msgId will be printed, and it can be added without printing
```yaml
logging:
  level:
    com.github.jarome: warn
```
### Explain

-The default pull mode is load balancing, and there is no consumption group field setting. If there are several codes with the same configuration, several corresponding consumers will rotate and consume
-Set the stream to queue mode, where messages are consumed in order. It is recommended to set one topic for each stream
