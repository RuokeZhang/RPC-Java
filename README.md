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