# Version5 RPC Framework - Quick Reference

## Module Overview

```
version5/
├── krpc-api/        # Service interfaces & DTOs
├── krpc-common/     # Messages, Serializers, Codecs
├── krpc-core/       # Framework core (Client, Server, Config)
├── krpc-consumer/   # Example consumer app
├── krpc-provider/   # Example provider app
└── pom.xml
```

## Consumer Usage

```java
// 1. Create proxy factory
ClientProxy clientProxy = new ClientProxy();

// 2. Get proxy for service interface
UserService userService = clientProxy.getProxy(UserService.class);

// 3. Call methods normally (transparent RPC)
User user = userService.getUserByUserId(1);
Integer id = userService.insertUserId(user);

// 4. Cleanup
clientProxy.close();
```

## Provider Usage

```java
// 1. Initialize config
KRpcApplication.initialize();

// 2. Create implementation
UserService userService = new UserServiceImpl();

// 3. Register with provider
ServiceProvider provider = new ServiceProvider(host, port);
provider.provideServiceInterface(userService);

// 4. Start server
RpcServer server = new NettyRpcServer(provider);
server.start(port);
```

## Key Interfaces

| Interface | Purpose | Implementation |
|-----------|---------|-----------------|
| `ServiceRegister` | Register services | `ZKServiceRegister` |
| `ServiceCenter` | Discover services | `ZKServiceCenter` |
| `RpcClient` | Send requests | `NettyRpcClient` |
| `RpcServer` | Receive requests | `NettyRpcServer` |
| `LoadBalance` | Select server | `ConsistencyHashBalance` |
| `Serializer` | Serialize objects | Hessian/JSON/Kryo/Protostuff |
| `RateLimit` | Limit traffic | `TokenBucketRateLimitImpl` |

## Core Classes

### ClientProxy
- **Role**: Dynamic proxy for RPC calls
- **Key Method**: `invoke()` - intercepts method calls
- **Path**: `/krpc-core/.../client/proxy/ClientProxy.java`

### ServiceProvider
- **Role**: Local service registry
- **Key Method**: `provideServiceInterface()` - register service
- **Path**: `/krpc-core/.../server/provider/ServiceProvider.java`

### NettyRpcClient
- **Role**: Network client
- **Key Method**: `sendRequest()` - send RPC request
- **Path**: `/krpc-core/.../client/rpcclient/impl/NettyRpcClient.java`

### NettyRpcServer
- **Role**: Network server
- **Key Method**: `start()` - start listening
- **Path**: `/krpc-core/.../server/server/impl/NettyRpcServer.java`

### CircuitBreaker
- **Role**: Fault tolerance
- **States**: CLOSED → OPEN → HALF_OPEN → CLOSED
- **Path**: `/krpc-core/.../client/circuitbreaker/CircuitBreaker.java`

### GuavaRetry
- **Role**: Automatic retry
- **Config**: Max 3 attempts, 2s delay
- **Path**: `/krpc-core/.../client/retry/GuavaRetry.java`

## Message Format (8 bytes header)

```
[0-1]   MessageType: 0=REQUEST, 1=RESPONSE
[2-3]   SerializerType: 0=Object, 1=JSON, 2=Kryo, 3=Hessian, 4=Protostuff
[4-7]   Payload Length
[8+]    Serialized Data
```

## RpcRequest Structure

```
interfaceName   → "com.kama.service.UserService"
methodName      → "getUserByUserId"
params          → [1, "test", ...]
paramsType      → [Integer.class, String.class, ...]
```

## RpcResponse Structure

```
code            → 200 (success) or 500 (error)
message         → Error description
dataType        → return type class (for deserialization)
data            → Actual return value
```

## Service Registration (ZK Structure)

```
/MyRPC/
├── com.kama.service.UserService/
│   ├── localhost:9999 (ephemeral)
│   └── localhost:9998 (ephemeral)
└── com.kama.service.OrderService/
    └── localhost:9997 (ephemeral)

/CanRetry/
├── localhost:9999/
│   ├── com.kama.service.UserService#getUserByUserId(java.lang.Integer)
│   └── com.kama.service.UserService#insertUserId(com.kama.pojo.User)
└── localhost:9998/
    └── com.kama.service.UserService#getUserByUserId(java.lang.Integer)
```

## Configuration (KRpcConfig)

```properties
# rpc.properties
name=krpc
port=9999
host=localhost
version=1.0.0
registry=nacos          # Service registry
serializer=hessian     # Serialization (type 3)
loadBalance=consistency-hash  # Load balancing
```

## Execution Flow Summary

```
Consumer Request:
  1. ClientProxy.invoke(method, args)
  2. Build RpcRequest
  3. Get CircuitBreaker → allowRequest()?
  4. ServiceCenter.serviceDiscovery() → InetSocketAddress
  5. ConsistencyHashBalance → select server from list
  6. Check ServiceCenter.checkRetry() → method eligible?
  7. GuavaRetry.sendServiceWithRetry() OR RpcClient.sendRequest()
  8. CircuitBreaker.recordSuccess/Failure()
  9. Return response.getData()

Network Transport:
  10. MyEncoder: serialize RpcRequest → bytes
  11. Network → Provider
  12. MyDecoder: deserialize bytes → RpcRequest

Provider Response:
  13. NettyRpcServerHandler.getResponse()
  14. RateLimit.getToken()? (rate limiting)
  15. ServiceProvider.getService(interfaceName) → impl
  16. Reflection: method.invoke(impl, params)
  17. Build RpcResponse with result
  18. MyEncoder: serialize → bytes
  19. Network → Consumer
  20. MyDecoder: deserialize → RpcResponse
  21. Return to caller
```

## Load Balancing (Consistency Hash)

```
Algorithm: FNV1_32_HASH
Virtual Nodes: 5 per real server
Ring Structure: TreeMap sorted by hash

Example:
Real Nodes:        Virtual Nodes:
localhost:9999  → VN0,VN1,VN2,VN3,VN4 (5 copies)
localhost:9998  → VN0,VN1,VN2,VN3,VN4 (5 copies)

Request UUID → Hash → Find next node on ring clockwise
Result: Distributed load with minimal reshuffling on failures
```

## Rate Limiting (Token Bucket)

```
Configuration:
  rate = X ms (time per token)
  capacity = N tokens (max)

Algorithm:
  1. Check if curCapacity > 0 → Grant & decrement
  2. If empty: Calculate elapsed time since last request
  3. Add (elapsed / rate) new tokens (up to capacity)
  4. If still no tokens → Reject request
```

## Circuit Breaker States

```
CLOSED (Normal)
  → recordSuccess(): Reset counters, stay CLOSED
  → recordFailure(): Increment counter
    → failureCount >= threshold? → OPEN
  → allowRequest(): Always true

OPEN (Broken)
  → allowRequest(): Always false (reject)
  → After retryTimePeriod elapsed: → HALF_OPEN

HALF_OPEN (Testing)
  → allowRequest(): true for test requests
  → recordSuccess(): successRate >= threshold? → CLOSED
  → recordFailure(): → OPEN
```

## Serializer Types (getType() return value)

```
0: ObjectSerializer      (Java native serialization)
1: JsonSerializer        (FastJSON - complex type handling)
2: KryoSerializer        (Kryo - fast)
3: HessianSerializer     (Hessian - DEFAULT in v5)
4: ProtostuffSerializer  (Protostuff - efficient)
```

## ThreadPool Architecture

```
Client Side:
  Static: 1 EventLoopGroup (shared across all clients)
         1 Bootstrap (shared connection factory)
  Per Request: 1 Channel (connected to provider)

Server Side:
  1 BossGroup (1 thread): Accept connections
  1 WorkerGroup (n threads): Handle I/O
```

## Annotations

```java
@Retryable
  // Mark method as retry-eligible
  // Auto-detected by ZKServiceRegister during registration
  // Enables GuavaRetry wrapper in ClientProxy

Example:
  @Retryable
  User getUserByUserId(Integer id);
```

## Common Configuration Scenarios

### Development (Single Machine)
```properties
host=localhost
port=9999
registry=nacos
serializer=hessian
loadBalance=consistency-hash
```

### Production (Multiple Nodes)
```properties
host=192.168.1.100
port=9999
registry=nacos           # More distributed-friendly than ZK
serializer=hessian       # Balanced performance
loadBalance=consistency-hash  # Minimal reshuffling
```

### Testing (Debug)
```properties
host=localhost
port=8888
serializer=json          # Human-readable
loadBalance=round-robin  # Simpler
```

## Common Tasks

### Add New Service

```java
// 1. Define interface in krpc-api
public interface NewService {
    @Retryable
    String operation(String input);
}

// 2. Implement in krpc-provider
public class NewServiceImpl implements NewService {
    @Override
    public String operation(String input) {
        return "result: " + input;
    }
}

// 3. Register in provider
ServiceProvider provider = new ServiceProvider(host, port);
provider.provideServiceInterface(new NewServiceImpl());

// 4. Use in consumer
ClientProxy proxy = new ClientProxy();
NewService client = proxy.getProxy(NewService.class);
String result = client.operation("test");
```

### Change Serializer

In ClientProxy or provider startup:
```java
// Instead of default Hessian (type 3), use JSON (type 1)
// Change in NettyClientInitializer and NettyServerInitializer:
pipeline.addLast(new MyEncoder(Serializer.getSerializerByCode(1)));  // JSON
```

### Tune Circuit Breaker

In CircuitBreakerProvider:
```java
// Default settings
new CircuitBreaker(
    5,      // failureThreshold: trip after 5 failures
    0.8,    // halfOpenSuccessRate: 80% success to close
    60000   // retryTimePeriod: test after 60s
);
```

## Troubleshooting

| Problem | Cause | Solution |
|---------|-------|----------|
| "Service not found" | Service not registered | Check ZK path, verify @Retryable, restart provider |
| "Connection timeout" | Provider offline | Check provider status, verify host:port |
| "Serialization error" | Type mismatch | Ensure serializer consistency, check dataType field |
| "Circuit breaker open" | Too many failures | Wait 60s for retry, check error logs |
| "Rate limit exceeded" | Too many requests | Reduce QPS or increase token bucket capacity |

## Key Files Reference

| Component | File Path |
|-----------|-----------|
| RpcRequest | `/krpc-common/.../RpcRequest.java` |
| RpcResponse | `/krpc-common/.../RpcResponse.java` |
| ClientProxy | `/krpc-core/.../ClientProxy.java` |
| ServiceProvider | `/krpc-core/.../ServiceProvider.java` |
| NettyRpcClient | `/krpc-core/.../NettyRpcClient.java` |
| NettyRpcServer | `/krpc-core/.../NettyRpcServer.java` |
| CircuitBreaker | `/krpc-core/.../CircuitBreaker.java` |
| GuavaRetry | `/krpc-core/.../GuavaRetry.java` |
| KRpcConfig | `/krpc-core/.../KRpcConfig.java` |
| KRpcApplication | `/krpc-core/.../KRpcApplication.java` |

---

For detailed information, see **VERSION5_LEARNING_GUIDE.md**
