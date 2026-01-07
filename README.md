## RPC和HTTP的区别
- RPC has small header overhead, 能灵活定制传输格式
- 服务发现：服务提供者启动时自动注册，消费者通过注册中心动态发现服务地址
- 负载均衡：RPC 在客户端实现负载均衡
- 容错机制：我的项目实现了重试（Retry）、熔断（Circuit Breaker）和限流（Rate Limiting）


## Serialization
### Kyro
速度快，体积小，只支持序列化和反序列化Java对象，线程不安全

1. 类注册（Class Registration）。在kyro序列化某一个类的对象之前，它会注册这个类，给这个类分配一个整数ID. ```kryo.register(User.class, 1);```因此在序列化时，只需要写入类的ID（1-4字节），效率更高
2. 序列化
    - 用 ASM 框架直接生成一段 Java 字节码，这段代码就是针对该类的序列化 / 反序列化逻辑
    - 把生成的字节码加载到 JVM 中，得到一个 专用的序列化器类
    - 后续对该类的对象进行序列化时，直接调用这个专用类的方法，完全不走反射
3. 反序列化
    - 从字节流里面读class ID, 然后从ClassResolver里面找到对应的class
    - 使用Objenesis创建对象，这样可以跳过构造方法里的业务逻辑，也可以创建某些缺少无参构造方法的类

### Hessian
- 跨语言，效率略低于kyro
- 每次调用 serialize/deserialize 都会创建局部的 HessianOutput/Input
- HessianInput绑定对应的ByteArrayInputStream即可
- 它不需要 IDL。只要两端的字段名匹配即可。

### Protobuf
1. 定义.proto 文件：krpc-common/src/main/proto/com/kama/proto/User.proto
2. 编译后，protoc生成接口文件krpc-common/target/generated-sources/protobuf/java/com/kama/proto/UserProto.java。这里还会为该类生成一个 parser。另外，protoc生成的类一定实现MessageLite接口。
3. 做序列化的时候，直接调用对象（messageLite 实例）的toByteArray()方法。
4. deserializer维护messageType -> Parser映射。在调用 deserialize 的时候，需要指定对应的 messageType, 取出对应的 parser，然后直接使用 parser 的parseFrom function 来反序列化出对象


## Netty
### BIO
每次调用 sendRequest 时，都会新建一个 Socket 连接到服务器(TCP 三次握手+四次挥手)

### 连接池
```krpc-core/src/main/java/com/kama/client/netty/ChannelProvider.java```

```java
private static final Map<String, Channel> channelPool = new ConcurrentHashMap<>();//连接池
```
实现了这个函数

```java
getChannel(InetSocketAddress address, Bootstrap bootstrap)
```

检查对于这个 address 有没有 active 的 channel，如果有，直接返回该 channel；如果没有，用 boostrap 创建一个新的 channel

```java
ChannelFuture future = bootstrap.connect(address).sync();
```
如果有 channel 被关闭了，则把该 channel 从连接池中移除
```java
channel.closeFuture().addListener(closeFuture -> {
    log.info("连接关闭，从连接池移除: {}", key);
    channelPool.remove(key);
});
```

### NettyClientInitializer
配置 IdleStateHandler, Decoder, Encoder, NettyClientHandler
- IdleStateHandler: 如果这个 channel 超过一定时间没有读事件，则触发一个READER_IDLE事件
-  NettyClientHandler：
    - 重写userEventTriggered function，用来响应READER_IDLE事件，具体是发送一个心跳检测
    - 重写channelRead0 function。作用是收到响应后，根据 response 中携带的requestId 回填对应的 responseFuture


### Server
- 创建两个线程池，一个用于处理新连接，另一个负责连接的 IO 事件
- Channel: 一般就用NioServerSocketChannel
- Pipeline: 定义 LengthFieldBasedFrameDecoder, IdleStateHandler, MyEncoder, MyDecoder和NettyRpcServerHandler
- LengthFieldBasedFrameDecoder
- NettyRpcServerHandler：
    - 接收 RpcRequest, 拿到它的 Interface,再从本机的映射表拿到对应的 service,开始进行方法调用
    - 若收到心跳检测，则构建并发送心跳响应。客户端收到心跳响应之后，定时器会被重置
    - 重写userEventTriggered，若收到了READER_IDLE事件（设置为 30 秒，即 client 的两倍时间），则直接关闭 channel


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

## Nacos
1. server 注册现有的服务
    ```java
    public void register(Class<?> clazz, InetSocketAddress serviceAddress) 
    ```
    - 创建一个 Nacos Instance, 设置 IP, port, weight
    - 注册这个服务：namingService.registerInstance(serviceName, instance);
    - serviceName 是这个实现类的全限定名
2. clientProxy 调用invoke()函数
    - 首先根据服务名返回服务器实例列表
    - load balance选择一个实例并返回
    - 用实例地址创建NettyRpcClient
    - responseFuture = rpcClient.sendRequest(request);发送请求
        - 创建一个Future，把 requestId 和 Future缓存到PENDING_FUTURES map 中
        - 使用连接池获取复用的 Channel
        - channel.writeAndFlush(request)发送请求
        - 当 clientHandler（另一个线程）收到响应时，根据 requestId 回填对应的 future，**不关闭 channel**
        - 主线程返回response
