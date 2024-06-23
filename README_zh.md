## 说明
为方便发送和接收Nats消息，封装此starter，用法类似RocketMQ starter。
接收消息类型默认为push
## pom引用
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
| 字段 | 解释 | 默认值 |
| --- | --- | --- |
| consume-thread-min | 消费者线程 核心线程数 | 20 |
| consume-thread-max | 消费者线程 最大线程数 | 20 |
| block-queue-max | 消费者线程 阻塞队列长度 | Integer.MAX_VALUE - 1024 |

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
```

字段解释
官方参考 [consumers](https://docs.nats.io/nats-concepts/jetstream/consumers)

| 字段 | 对应官方字段 | 说明 |
| --- | --- | --- |
| deliverSubject | DeliverSubject | 推送消息主题，设置了才能为push模式 |
| durable | Durable | 持久化消费者名称 |
| filterSubject | [filtersubjects](https://docs.nats.io/nats-concepts/jetstream/consumers#filtersubjects) | 与绑定到流的主题重叠的主题，用于筛选向订阅者的传递 |
| stream | [streams name](https://docs.nats.io/nats-concepts/jetstream/streams) | stream名称 |
| deliverGroup | DeliverGroup | 消费组名称，类似rocketmq的消费组，同组负载均衡消费 |
| autoAck | autoAck | onMessage方法执行完之后自动ack，默认true。 |

## 日志
默认会打印msgId，可以添加不打印
```yaml
logging:
  level:
    com.github.jarome: warn
```
