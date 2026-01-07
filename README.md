## Modules
- krpc-api: public interfaces and models
- krpc-common: message schema, serializers, codec
- krpc-core: client/server, load-balancer, retry, breaker
- krpc-provider: service implementation (provider demo)
- krpc-consumer: client caller (consumer demo)

## Test
```shell
mvn test -Dtest=NettyRpcIntegrationTest -pl krpc-core
```
## 请求可靠性
### Guava Retry(Client)
[repo](https://github.com/rholder/guava-retrying)

Guava Retry是 Google Guava 提供的一个重试工具库。  
在client 调用一个方法时，需要检查该方法是否支持 retry. 
- 先查本地缓存，看这个方法能否 retry
- 若缓存没有，去Nacos 找该 instance 的 metadata,里面有记录哪些方法是可以 retry 的
- 如果该方法支持 retry,则调用可重试版本的 sendRequest

### Circuit Breaker(Client)
客户端在 invoke()这个方法的时候，获取到 **serviceName+method** 对应的熔断器，检查是否 allowRequest。熔断器与具体哪个 nacos instance 无关

```java
enum CircuitBreakerState {
    CLOSED,  // 关闭状态（正常工作）
    OPEN,    // 开启状态（熔断中，拒绝请求）
    HALF_OPEN// 半开启状态（尝试恢复）
}
```
- 如果 invoke()调用成功了，会增加 successCount, 这个 count 可能会影响到当前 breaker 的状态改变
- 如果当前 breaker 是 CLOSED，那么可以正常工作
- 如果是 OPEN，需要判断当前请求的事件和上次请求失败时间的**差距**是否大于某个阈值。如果大于了，可以改成 HALF OPEN 状态
- 如果是 HALF_OPEN, 那么累计 requestCount，允许请求被发送

### Service Discovery Fallback(Client)
- 服务发现优先从 Nacos 获取健康实例；若 Nacos 不可达，回退到本地缓存的上次成功实例列表。
- 负载均衡在缓存列表上继续工作，避免注册中心短暂不可用导致完全中断。
- 成功从 Nacos 拿到列表后会刷新本地缓存。

### 限流(Server)
粒度是接口名
```java
// 令牌产生速率（单位：ms）
private final int rate;
// 桶容量
private final int capacity;
// 当前桶容量
private volatile int curCapacity;
// 上次请求时间戳
private volatile long lastTimestamp;
```
- 如果令牌够，直接返回 True
- 如果不够了，检查当前事件-lastTimestamp是否大于 rate. 如果大于，计算一下这段时间之内生成的令牌数量，并加入令牌同
- 如果小于，则返回 False

server 在getResponse()这一步去检查令牌是否充足，来判断是否调用对应的方法

## Load Balance
定义了统一接口，多种实现方式
```java
public interface LoadBalance {
    String balance(List<String> addressList);
    void addNode(String node);
    void delNode(String node);
}
```
### Round Robin
- 用AtomicInteger维护一个索引，每次调用.balance的时候，索引+1，并且对server列表长度取模，得到目标server
- 列表用的是CopyOnWriteArrayList
### Random
创建了Random()实例，随机返回一个server instance
### Weighted Round Robin
- 平滑加权轮询（类似 nginx），支持 setWeight / setWeights 设置权重，默认权重为 1
- 每次请求根据权重比例分配，节点列表变更时自动剔除失效节点
### Least Connection
- 按当前在途连接数选择负载最低节点，请求结束后需调用 releaseConnection 归还计数
- 节点列表变更时自动剔除失效节点，避免计数泄漏

## 粘包 / 拆包
粘包：发送方发送了两个或多个数据包，但接收方一次性收到了这些数据包的合并结果。  
拆包：发送方发送了一个数据包，但接收方分多次才收到完整的数据。

自定义协议：
- Message Type(2 字节)：标识消息类型（请求或响应）
- Serializer Type (2 字节)：标识使用的序列化器。
- Data Length (4 字节)：核心字段，记录后续 Body（序列化后的对象）的字节长度
- Body (N 字节)：实际的序列化数据

```java
// 参数：最大帧长度、长度字段偏移量、长度字段长度
pipeline.addLast(new LengthFieldBasedFrameDecoder(
    MAX_FRAME_LENGTH,
    4,  // lengthFieldOffset: 跳过 messageType(2) + serializerType(2)
    4,  // lengthFieldLength: length 占 4 字节
    0,  // lengthAdjustment: body 紧跟 length，无需调整
    0   // initialBytesToStrip: 不跳过头部，交由 MyDecoder 解析
));
```
接收端使用LengthFieldBasedFrameDecoder，读取content的长度，然后自动“粘合”或“拆分”字节流，保证传给下一个handler的 ByteBuf是一帧完整的数据，myDecoder直接把content的字节流转换成Java对象即可