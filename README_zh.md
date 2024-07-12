## 说明
为方便发送和接收Nats消息，封装此starter，用法类似RocketMQ starter。
## pom引用
```xml
<dependency>
  <groupId>com.github.jarome</groupId>
  <artifactId>spring-boot-starter-nats</artifactId>
  <version>0.0.2</version>
</dependency>
```
## 基础配置
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
consumers参数
默认参数为` @NatsMessageListener `中的配置
如果不需要修改，整个consumers可以不写

| 字段 | 解释 | 默认值                    |
| --- | --- |------------------------|
| subject | 接收的主题 |
| autoAck | 自动ack | true                   |
| consumeThreadNumber | 消费者线程 核心线程数 | 20                     |
| consumeThreadMax | 消费者线程 最大线程数 | 64                     |
| blockingQueueSize | 消费者线程 阻塞队列长度 | 2000                   |
| keepAliveTime | 消费者线程  存活时间 | 1000*60 ms             |
| pullBatchSize | pull模式每次拉取数量 | 10                     |
| pullInterval | pull模式无消息时的拉取间隔 | 1000ms                 |
| maxWaitTime | pull模式拉取超时时间 | 3000ms （按照Nats限制实际最小为1ms） |
| pullDelayTime | pull模式的拉取延迟时间 |0ms |

### 生产者
```java
@Autowired
NatsTemplate natsTemplate;

public void testSend() {
    TestData data = new TestData("name1", 123, true);
    natsTemplate.publishAsync("test.push.nums", data, "msgId");
}        
```
字段解释

| 字段 | 说明 |
| --- | --- |
| subject | 同消费者的filterSubject |
| body | 发送的消息，任意类型 |
| msgId | [Nats-Msg-Id ](https://docs.nats.io/using-nats/developer/develop_jetstream/model_deep_dive#message-deduplication)去重用，默认2min间隔去重 |



### 消费者
```java
@Slf4j
@Service
@NatsMessageListener(deliverSubject = "test-push-handler-5", durable = "test-push-handler-5", filterSubject = "test.push.nums", stream = "TEST-PUSH-EVENTS", deliverGroup = "deliver-test-group")
public class PushTestEventsMessageHandler implements NatsListener<TestData> {
    @Override
    public void onMessage(TestData message) {
        log.info("接收参数:{}", message);
        //处理逻辑
    }
}

@Slf4j
@Service
@NatsMessageListener(durable = "test-pull-handler", filterSubject = "test.pull.nums", stream = "TEST-PULL-EVENTS")
public class PushTestEventsMessageHandler implements NatsListener<TestData> {
    @Override
    public void onMessage(TestData message) {
        log.info("Pull接收参数:{}", message);
        //处理逻辑
    }
}
```

字段解释
官方参考 [consumers](https://docs.nats.io/nats-concepts/jetstream/consumers)   deliverSubject和deliverGroup都设置了为push模式，都不设置为pull模式

| 字段 | 对应官方字段 | 说明 |
| --- | --- | --- |
| deliverSubject | DeliverSubject | 推送消息主题，设置了才能为push模式 |
| deliverGroup | DeliverGroup | 消费组名称，类似rocketmq的消费组，同组负载均衡消费，设置了才能为push模式 |
| durable | Durable | 持久化消费者名称 |
| filterSubject | [filtersubjects](https://docs.nats.io/nats-concepts/jetstream/consumers#filtersubjects) | 与绑定到流的主题重叠的主题，用于筛选向订阅者的传递 |
| stream | [streams name](https://docs.nats.io/nats-concepts/jetstream/streams) | stream名称 |
| autoAck | autoAck | onMessage方法执行完之后自动ack，默认true。 |
| maxDeliver | MaxDeliver | 尝试特定消息传递的最大次数。 默认-1 |
| consumeThreadNumber | 消费者线程 核心线程数 | 20 |
| consumeThreadMax | 消费者线程 最大线程数 | 20 |
| blockingQueueSize | 消费者线程 阻塞队列长度 | Integer.MAX_VALUE - 1024 |
| keepAliveTime | 消费者线程  存活时间 | 1000*60 ms |
| pullBatchSize | pull模式每次拉取数量 | 10 |
| pullInterval | pull模式无消息时的拉取间隔 | 1000ms |
| maxWaitTime | pull模式首次拉取时间延迟 | 0ms （按照Nats限制实际最小为1ms） |

## 日志
默认会打印msgId，可以添加不打印
```yaml
logging:
  level:
    com.huice.tm.nats: warn
```
### 说明

- pull模式默认为负载均衡，不存在消费组字段设置，有几个相同配置的代码就会有几个对应消费者进行轮训消费
- stream设置为队列模式，此中消息按照顺序消费，建议按照一个topic对应一个stream设置

