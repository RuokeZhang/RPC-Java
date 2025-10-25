# Version5 RPC Framework - Comprehensive Learning Guide

## Table of Contents
1. [Project Overview](#project-overview)
2. [Module Structure](#module-structure)
3. [Core Interfaces & Implementations](#core-interfaces--implementations)
4. [Key Classes and Their Roles](#key-classes-and-their-roles)
5. [Message Flow & Communication](#message-flow--communication)
6. [Configuration System](#configuration-system)
7. [Advanced Features](#advanced-features)

---

## Project Overview

The Version5 RPC (Remote Procedure Call) framework is a distributed service communication framework built on **Java 17** with **Spring Boot 3.3.5**, **Netty 4.1.51**, and **Nacos** for service registration and discovery.

### Key Technologies
- **Networking**: Netty (async I/O)
- **Service Registry**: Nacos
- **Serialization**: Multiple options (Hessian, JSON, Kryo, Protostuff, Object)
- **Load Balancing**: Consistency Hash
- **Fault Tolerance**: Circuit Breaker Pattern
- **Retry Mechanism**: Guava Retrying Library
- **Rate Limiting**: Token Bucket Algorithm

---

## Module Structure

### 1. **krpc-api** (`/Users/ruoke/Documents/java_web/RPC-Java/version5/krpc-api/`)
**Purpose**: Service interface definitions and shared data objects

**Key Files**:
- `UserService.java` - Example RPC service interface
  - Methods decorated with `@Retryable` annotation for retry-enabled operations
  - Contains `getUserByUserId(Integer id)` and `insertUserId(User user)`
- `User.java` - POJO (Plain Old Java Object) representing data transfer objects
- `Retryable.java` - Custom annotation to mark methods eligible for retry

**Role**: This is the contract layer - shared between consumer and provider

---

### 2. **krpc-common** (`/Users/ruoke/Documents/java_web/RPC-Java/version5/krpc-common/`)
**Purpose**: Common utilities, message definitions, and serialization infrastructure

**Key Components**:

#### Message Format Classes:
- **`RpcRequest.java`** - Encapsulates client request
  ```java
  - interfaceName: String      // Interface being called
  - methodName: String         // Method name
  - params: Object[]          // Method parameters
  - paramsType: Class<?>[]    // Parameter types for reflection
  ```

- **`RpcResponse.java`** - Encapsulates server response
  ```java
  - code: int                 // Status code (200 = success, 500 = error)
  - message: String          // Status message
  - dataType: Class<?>       // Return type for proper deserialization
  - data: Object            // Actual return value
  ```

- **`MessageType.java`** - Enum defining message types
  ```java
  REQUEST(0)  // Client -> Server request
  RESPONSE(1) // Server -> Client response
  ```

#### Serialization Framework:
- **`Serializer.java`** (Interface) - Core serialization abstraction
  ```java
  byte[] serialize(Object obj);
  Object deserialize(byte[] bytes, int messageType);
  int getType();
  // Static factory registry with 5 serializer options
  ```

- **Implementations**:
  1. **`ObjectSerializer`** (Type: 0) - Java native serialization
  2. **`JsonSerializer`** (Type: 1) - FastJSON serialization with type handling
  3. **`KryoSerializer`** (Type: 2) - Kryo fast serialization
  4. **`HessianSerializer`** (Type: 3) - Default serializer for v5
  5. **`ProtostuffSerializer`** (Type: 4) - Protostuff serialization

#### Network Codec:
- **`MyEncoder.java`** - Netty encoder for serialization
  - Writes: [MessageType(2B)] [SerializerType(2B)] [Length(4B)] [SerializedData(nB)]
  
- **`MyDecoder.java`** - Netty decoder for deserialization
  - Reads protocol headers and reconstructs objects

#### Utilities:
- **`ConfigUtil.java`** - Configuration file loading
- **`SpiLoader.java`** - Service Provider Interface loader
- **`SerializeException.java`** - Custom serialization exception

---

### 3. **krpc-core** (`/Users/ruoke/Documents/java_web/RPC-Java/version5/krpc-core/`)
**Purpose**: Core RPC framework implementation

#### A. Client-Side Components:

**Service Discovery & Load Balancing**:
- **`ServiceCenter.java`** (Interface)
  ```java
  // Discover service address from registry
  InetSocketAddress serviceDiscovery(RpcRequest request);
  
  // Check if method supports retry
  boolean checkRetry(InetSocketAddress serviceAddress, String methodSignature);
  
  // Cleanup
  void close();
  ```

- **`NacosServiceCenter.java`** - Nacos-based implementation
  - Maintains `ServiceCache` for local address caching
  - Uses Nacos event listener for real-time updates
  - Integrates `ConsistencyHashBalance` for load balancing
  - Caches retry-eligible methods in separate namespace

- **`ServiceCache.java`** - In-memory service address cache
  ```
  Maps: serviceName -> List[address:port]
  Thread-safe: Uses ConcurrentHashMap
  ```

**Load Balancing**:
- **`LoadBalance.java`** (Interface)
  ```java
  String balance(List<String> addressList);
  void addNode(String node);
  void delNode(String node);
  ```

- **`ConsistencyHashBalance.java`** - Consistency hashing implementation
  - Uses FNV1_32_HASH algorithm
  - Virtual nodes: 5 per real server (configurable)
  - Ring topology with TreeMap
  - Automatic failover support

**Fault Tolerance**:
- **`CircuitBreaker.java`** - Circuit breaker state machine
  - States: CLOSED → OPEN → HALF_OPEN → CLOSED
  - Configurable failure threshold
  - Success rate threshold for half-open state
  - Exponential backoff timing

- **`CircuitBreakerProvider.java`** - Factory for circuit breaker instances
  - Per-method circuit breaker isolation
  - Singleton pattern per method

**Retry Mechanism**:
- **`GuavaRetry.java`** - Guava-based retry wrapper
  - Retry on: any exception or code 500
  - Strategy: Fixed 2-second wait
  - Limit: 3 total attempts
  - Includes retry listener logging

**Client Proxy**:
- **`ClientProxy.java`** - Dynamic proxy for remote calls
  ```
  Flow:
  1. Intercepts method calls via InvocationHandler
  2. Constructs RpcRequest from method metadata
  3. Gets circuit breaker for isolation
  4. Discovers service address via ServiceCenter
  5. Checks if method is retry-eligible
  6. Sends via RpcClient with retry if eligible
  7. Records success/failure for circuit breaker
  8. Returns response data to caller
  ```

**RPC Client**:
- **`RpcClient.java`** (Interface)
  ```java
  RpcResponse sendRequest(RpcRequest request);
  void close();
  ```

- **`NettyRpcClient.java`** - Netty-based client implementation
  - Static Bootstrap and EventLoopGroup (shared across instances)
  - Synchronous communication using channel sync()
  - AttributeKey for storing response on channel
  - Graceful shutdown with shutdownGracefully()

- **`NettyClientInitializer.java`** - Channel pipeline setup
  ```
  Pipeline: MyEncoder -> MyDecoder -> NettyClientHandler
  Uses HessianSerializer (Type 3) by default
  ```

- **`NettyClientHandler.java`** - Inbound handler
  - Receives RpcResponse
  - Stores response in channel attributes
  - Closes channel after response

#### B. Server-Side Components:

**Service Registration**:
- **`ServiceRegister.java`** (Interface)
  ```java
  void register(Class<?> clazz, InetSocketAddress serviceAddress);
  ```

- **`NacosServiceRegister.java`** - Nacos registration
  - Registers service instances with metadata
  - Uses Nacos heartbeat for health check
  - Registers retry-eligible methods in separate namespace
  - Extracts method signature from @Retryable annotation

**Service Provider**:
- **`ServiceProvider.java`** - Local service registry
  ```
  - interfaceProvider: Map<interfaceName, serviceImpl>
  - Registers services with ServiceRegister
  - Provides rate limiting via RateLimitProvider
  - Handles method invocation through getService()
  ```

**RPC Server**:
- **`RpcServer.java`** (Interface)
  ```java
  void start(int port);
  void stop();
  ```

- **`NettyRpcServer.java`** - Netty server implementation
  - Boss group: handles accept connections
  - Worker group: handles I/O operations
  - Graceful shutdown with shutdownGracefully()

- **`NettyServerInitializer.java`** - Server pipeline setup
  ```
  Pipeline: MyEncoder -> MyDecoder -> NettyRpcServerHandler
  Injects ServiceProvider for handler
  ```

- **`NettyRpcServerHandler.java`** - Request processing
  ```
  Flow:
  1. Receives RpcRequest
  2. Gets rate limit token
  3. Retrieves service implementation
  4. Uses reflection to invoke method
  5. Wraps result in RpcResponse
  6. Sends response back to client
  ```

**Rate Limiting**:
- **`RateLimit.java`** (Interface)
  ```java
  boolean getToken();
  ```

- **`TokenBucketRateLimitImpl.java`** - Token bucket algorithm
  - Configurable rate (ms per token) and capacity
  - Refills based on elapsed time
  - Synchronized for thread safety

- **`RateLimitProvider.java`** - Per-interface rate limiter factory

#### C. Configuration:

- **`KRpcApplication.java`** - Central configuration manager
  - Singleton pattern (double-checked locking)
  - Lazy initialization on first call
  - Falls back to defaults if config file missing
  - Configuration path: `rpc.properties` or similar

- **`KRpcConfig.java`** - Configuration class
  ```java
  name: "krpc"
  port: 9999
  host: "localhost"
  version: "1.0.0"
  registry: "nacos" (default)
  serializer: "hessian" (type 3, default)
  loadBalance: "round-robin" (default)
  ```

- **`RpcConstant.java`** - Framework constants
  ```java
  CONFIG_FILE_PREFIX = "rpc"
  DEFAULT_VERSION_DEFAULT = "1.0.0"
  ```

---

### 4. **krpc-consumer** (`/Users/ruoke/Documents/java_web/RPC-Java/version5/krpc-consumer/`)
**Purpose**: Example consumer application

**Key File**:
- **`ConsumerTest.java`** - Example usage
  ```java
  // 1. Create proxy
  ClientProxy clientProxy = new ClientProxy();
  
  // 2. Get proxy instance for interface
  UserService proxy = clientProxy.getProxy(UserService.class);
  
  // 3. Call remote methods as if local
  User user = proxy.getUserByUserId(1);
  Integer id = proxy.insertUserId(user);
  
  // 4. Cleanup
  clientProxy.close();
  ```

---

### 5. **krpc-provider** (`/Users/ruoke/Documents/java_web/RPC-Java/version5/krpc-provider/`)
**Purpose**: Example provider application

**Key Files**:
- **`ProviderTest.java`** - Server startup
  ```java
  // 1. Initialize framework config
  KRpcApplication.initialize();
  
  // 2. Create service implementation
  UserService userService = new UserServiceImpl();
  
  // 3. Register with service provider
  ServiceProvider serviceProvider = new ServiceProvider(host, port);
  serviceProvider.provideServiceInterface(userService);
  
  // 4. Start server
  RpcServer rpcServer = new NettyRpcServer(serviceProvider);
  rpcServer.start(port);
  ```

- **`UserServiceImpl.java`** - Service implementation
  ```java
  @Override
  public User getUserByUserId(Integer id) {
    // Simulates database query
    return User.builder()...build();
  }
  ```

---

## Core Interfaces & Implementations

### 1. ServiceRegister Interface

**Location**: `/Users/ruoke/Documents/java_web/RPC-Java/version5/krpc-core/src/main/java/com/kama/server/serviceRegister/ServiceRegister.java`

```java
public interface ServiceRegister {
    void register(Class<?> clazz, InetSocketAddress serviceAddress);
}
```

**Purpose**: Register service interfaces with a registry center

**Implementation Details**:
- Single method: `register()`
- Takes service interface class and server address
- Typically called once per service at startup

**Known Implementations**:
1. **NacosServiceRegister** - Uses Alibaba Nacos
   - Service naming: `{serviceName}`
   - Instance metadata: `{host:port}`
   - Auto-deregister on disconnect via heartbeat
   - Separate namespace for retry whitelist: `/CanRetry/{host:port}/{methodSignature}`
   - Automatic detection of @Retryable methods

---

### 2. ServiceCenter Interface

**Location**: `/Users/ruoke/Documents/java_web/RPC-Java/version5/krpc-core/src/main/java/com/kama/client/servicecenter/ServiceCenter.java`

```java
public interface ServiceCenter {
    InetSocketAddress serviceDiscovery(RpcRequest request);
    boolean checkRetry(InetSocketAddress serviceAddress, String methodSignature);
    void close();
}
```

**Purpose**: Consumer-side service discovery and metadata queries

**Key Methods**:
- `serviceDiscovery()`: 
  - Input: RpcRequest with interfaceName
  - Output: InetSocketAddress of available provider
  - Uses load balancer to select from multiple instances
  - Caches results locally for performance
  
- `checkRetry()`: 
  - Input: Service address and method signature
  - Output: Boolean indicating if retry is allowed
  - Queries retry whitelist from registry
  - Local caching of retry metadata

**Implementation**:
- **ZKServiceCenter**:
  - Maintains ServiceCache for local addresses
  - Watches ZK for real-time updates via watchZK
  - Uses ConsistencyHashBalance for load balancing
  - Lazy initialization of retry cache

---

### 3. Serializer Interface

**Location**: `/Users/ruoke/Documents/java_web/RPC-Java/version5/krpc-common/src/main/java/common/serializer/myserializer/Serializer.java`

```java
public interface Serializer {
    byte[] serialize(Object obj);
    Object deserialize(byte[] bytes, int messageType);
    int getType();
    
    // Static factory method
    static Serializer getSerializerByCode(int code) { ... }
}
```

**Purpose**: Pluggable serialization framework

**Registry** (Static Map):
```
Type 0: ObjectSerializer   (Java native)
Type 1: JsonSerializer     (FastJSON)
Type 2: KryoSerializer     (Kryo)
Type 3: HessianSerializer  (Hessian - DEFAULT)
Type 4: ProtostuffSerializer (Protostuff)
```

**Key Features**:
- Type codes in protocol header for version negotiation
- messageType parameter for context-aware deserialization
- Special handling for RpcRequest and RpcResponse

**Important Notes**:
- JsonSerializer handles complex type conversion for nested objects
- Each serializer returns different type code for identification
- Encoder/Decoder use factory method to instantiate correct serializer

---

### 4. LoadBalance Interface

**Location**: `/Users/ruoke/Documents/java_web/RPC-Java/version5/krpc-core/src/main/java/com/kama/client/servicecenter/balance/LoadBalance.java`

```java
public interface LoadBalance {
    String balance(List<String> addressList);
    void addNode(String node);
    void delNode(String node);
}
```

**Purpose**: Server selection strategy

**Implementation**:
- **ConsistencyHashBalance**:
  - Algorithm: FNV1_32_HASH
  - Virtual nodes: 5 per real server
  - Structure: TreeMap-based ring topology
  - UUID-based request routing (cache-friendly)
  - O(log n) lookup time
  - Handles node additions/removals dynamically

---

### 5. RpcClient Interface

**Location**: `/Users/ruoke/Documents/java_web/RPC-Java/version5/krpc-core/src/main/java/com/kama/client/rpcclient/RpcClient.java`

```java
public interface RpcClient {
    RpcResponse sendRequest(RpcRequest request);
    void close();
}
```

**Purpose**: Actual network communication layer

**Implementation**:
- **NettyRpcClient**:
  - Static Bootstrap and EventLoopGroup (connection pool optimization)
  - Synchronous blocking on channel.closeFuture().sync()
  - Uses AttributeKey for response retrieval
  - Single request-response per connection
  - Handles InterruptedException properly

---

### 6. RpcServer Interface

**Location**: `/Users/ruoke/Documents/java_web/RPC-Java/version5/krpc-core/src/main/java/com/kama/server/server/RpcServer.java`

```java
public interface RpcServer {
    void start(int port);
    void stop();
}
```

**Purpose**: Server lifecycle management

**Implementation**:
- **NettyRpcServer**:
  - Boss group: 1 thread (handles incoming connections)
  - Worker group: CPU cores threads (I/O processing)
  - NioServerSocketChannel for async I/O
  - Graceful shutdown coordination

---

## Key Classes and Their Roles

### 1. ClientProxy

**Location**: `/Users/ruoke/Documents/java_web/RPC-Java/version5/krpc-core/src/main/java/com/kama/client/proxy/ClientProxy.java`

**Role**: Bridge between consumer code and RPC infrastructure

**Architecture**:
- Implements `InvocationHandler` for JDK dynamic proxy
- Intercepts all method calls on proxy objects

**Processing Flow**:

```
Consumer calls proxy.method(args)
    ↓
ClientProxy.invoke() called
    ↓
1. Construct RpcRequest:
   - interfaceName: from method.getDeclaringClass()
   - methodName: from method.getName()
   - params: actual arguments
   - paramsType: method.getParameterTypes()
    ↓
2. Get CircuitBreaker for this method
    ↓
3. Check if circuit breaker allows request
   → If OPEN: reject request
   → If HALF_OPEN/CLOSED: allow
    ↓
4. Service Discovery:
   - Query ServiceCenter for provider address
   - Load balance among multiple instances
    ↓
5. Create RpcClient to target address
    ↓
6. Check if method in retry whitelist
   → If yes: wrap with GuavaRetry (3 attempts, 2s delay)
   → If no: direct send
    ↓
7. Send RpcRequest via RpcClient
    ↓
8. Receive RpcResponse
    ↓
9. Record status with CircuitBreaker:
   - Code 200: recordSuccess()
   - Code 500: recordFailure()
    ↓
10. Return response.getData() to caller
```

**Key Responsibilities**:
- Method interception and request construction
- Service discovery orchestration
- Retry policy enforcement
- Circuit breaker integration
- Response data extraction

**Configuration Dependencies**:
- ServiceCenter instance (shared)
- CircuitBreakerProvider instance (shared)
- RpcClient instances (created per call)

---

### 2. ServiceProvider

**Location**: `/Users/ruoke/Documents/java_web/RPC-Java/version5/krpc-core/src/main/java/com/kama/server/provider/ServiceProvider.java`

**Role**: Local service registry and rate limiter coordinator

**Data Structure**:
```java
interfaceProvider: Map<String, Object>
  Key: interface class name
  Value: implementation instance
```

**Key Methods**:

```java
// Register service implementation
public void provideServiceInterface(Object service) {
    // 1. Get all interfaces implemented by service
    Class<?>[] interfaces = service.getClass().getInterfaces();
    
    // 2. For each interface:
    for (Class<?> interfaceClass : interfaces) {
        // Add to local registry
        interfaceProvider.put(interfaceClass.getName(), service);
        
        // Register with registry center (ZK/Nacos)
        serviceRegister.register(interfaceClass, new InetSocketAddress(host, port));
    }
}

// Retrieve service for reflection-based invocation
public Object getService(String interfaceName) {
    return interfaceProvider.get(interfaceName);
}

// Get rate limiter for interface
public RateLimitProvider getRateLimitProvider() {
    return rateLimitProvider;
}
```

**Flow in Server Handler**:
```
NettyRpcServerHandler receives RpcRequest
    ↓
1. Get service: serviceProvider.getService(interfaceName)
    ↓
2. Get/acquire rate limit token
    → If no token: return RATE_LIMIT error
    ↓
3. Use reflection to invoke method:
   Method method = service.getClass().getMethod(methodName, paramTypes);
   Object result = method.invoke(service, params);
    ↓
4. Wrap in RpcResponse and send back
```

**Initialization Parameters**:
- `host`: Server address to register
- `port`: Server port to register

---

### 3. NettyRpcClient

**Location**: `/Users/ruoke/Documents/java_web/RPC-Java/version5/krpc-core/src/main/java/com/kama/client/rpcclient/impl/NettyRpcClient.java`

**Role**: Netty-based network client for RPC requests

**Architecture**:

```java
public class NettyRpcClient implements RpcClient {
    // Shared across all instances (optimization)
    private static final Bootstrap bootstrap;
    private static final EventLoopGroup eventLoopGroup;
    
    // Instance-specific target address
    private final InetSocketAddress address;
}
```

**Static Initialization**:
```java
static {
    eventLoopGroup = new NioEventLoopGroup();
    bootstrap = new Bootstrap();
    bootstrap.group(eventLoopGroup)
             .channel(NioSocketChannel.class)
             .handler(new NettyClientInitializer());
}
```

**Request Flow**:

```
sendRequest(RpcRequest request)
    ↓
1. Validate address is not null
    ↓
2. Extract host and port
    ↓
3. Connect:
   ChannelFuture channelFuture = bootstrap.connect(host, port).sync();
    ↓
4. Send request:
   channel.writeAndFlush(request);
    ↓
5. Block for response:
   channel.closeFuture().sync();
    ↓
6. Retrieve from channel attributes:
   AttributeKey<RpcResponse> key = AttributeKey.valueOf("RPCResponse");
   RpcResponse response = channel.attr(key).get();
    ↓
7. Return response (or error response if null)
```

**Key Features**:
- Synchronous blocking semantics (easier for RPC model)
- Proper exception handling with thread interruption
- Attribute-based response passing (thread-safe)

---

### 4. NettyRpcServer

**Location**: `/Users/ruoke/Documents/java_web/RPC-Java/version5/krpc-core/src/main/java/com/kama/server/server/impl/NettyRpcServer.java`

**Role**: Netty-based RPC server for receiving requests

**Architecture**:

```
ServerBootstrap (main server)
    ├── Boss Group (thread for accepting connections)
    └── Worker Group (threads for handling I/O)
```

**Startup Process**:

```java
public void start(int port) {
    NioEventLoopGroup bossGroup = new NioEventLoopGroup();      // 1 thread
    NioEventLoopGroup workGroup = new NioEventLoopGroup();      // n threads
    
    ServerBootstrap serverBootstrap = new ServerBootstrap();
    serverBootstrap.group(bossGroup, workGroup)
                   .channel(NioServerSocketChannel.class)
                   .childHandler(new NettyServerInitializer(serviceProvider));
    
    ChannelFuture channelFuture = serverBootstrap.bind(port).sync();
    channelFuture.channel().closeFuture().sync();  // Block until shutdown
    
    // Cleanup
    shutdown(bossGroup, workGroup);
}
```

**Request Handling Pipeline**:
```
Incoming Connection
    ↓
Boss Group accepts
    ↓
Worker Group handles channel
    ↓
NettyServerInitializer initializes pipeline
    ├── MyEncoder (serialize response)
    ├── MyDecoder (deserialize request)
    └── NettyRpcServerHandler (business logic)
        ↓
    NettyRpcServerHandler processes RpcRequest
        ↓
    Returns RpcResponse
        ↓
    MyEncoder serializes and sends
        ↓
    Channel closes
```

---

### 5. RpcRequest & RpcResponse

**Location**: `/Users/ruoke/Documents/java_web/RPC-Java/version5/krpc-common/src/main/java/common/message/`

**RpcRequest** (Client → Server):
```java
public class RpcRequest implements Serializable {
    private String interfaceName;      // e.g., "com.kama.service.UserService"
    private String methodName;         // e.g., "getUserByUserId"
    private Object[] params;           // [1] for getUserByUserId(1)
    private Class<?>[] paramsType;    // [Integer.class]
}
```

**RpcResponse** (Server → Client):
```java
public class RpcResponse implements Serializable {
    private int code;           // 200 (success) or 500 (error)
    private String message;     // Error description
    private Class<?> dataType;  // User.class for return type
    private Object data;        // Actual result or null
}
```

**Protocol Format** (on wire):
```
[0-1]   MessageType: REQUEST(0) or RESPONSE(1)    [2 bytes]
[2-3]   SerializerType: 0-4                       [2 bytes]
[4-7]   PayloadLength                             [4 bytes]
[8-]    Serialized Object (RpcRequest or RpcResponse)
```

**Total Header**: 8 bytes

---

### 6. CircuitBreaker

**Location**: `/Users/ruoke/Documents/java_web/RPC-Java/version5/krpc-core/src/main/java/com/kama/client/circuitbreaker/CircuitBreaker.java`

**Role**: Fault tolerance through request rejection

**State Machine**:

```
        ┌──────────────────────┐
        │      CLOSED          │ (Normal)
        │ Request allowed      │
        └──────────────────────┘
               ↓ (failureCount ≥ threshold)
        ┌──────────────────────┐
        │       OPEN           │ (Circuit broken)
        │ All requests blocked │
        └──────────────────────┘
               ↓ (after retryTimePeriod)
        ┌──────────────────────┐
        │     HALF_OPEN        │ (Test mode)
        │ Limited requests     │
        │ allowed              │
        └──────────────────────┘
               ↓ (successRate ≥ threshold)
        Back to CLOSED
               ↓ (any failure)
        Back to OPEN
```

**Configuration**:
```java
public CircuitBreaker(
    int failureThreshold,        // When to trip (e.g., 5 failures)
    double halfOpenSuccessRate,  // Recovery threshold (e.g., 0.8 = 80%)
    long retryTimePeriod         // Wait before testing (e.g., 60000ms = 1min)
)
```

**Key Methods**:

```java
boolean allowRequest() {
    // Returns true if request can proceed
    // Updates state based on elapsed time and current state
}

void recordSuccess() {
    // Called after successful response
    // Transitions HALF_OPEN -> CLOSED if threshold met
}

void recordFailure() {
    // Called after failed response
    // Increments failure count
    // Transitions CLOSED -> OPEN if threshold exceeded
}
```

**Integration in ClientProxy**:
```
Before send:
    if (!circuitBreaker.allowRequest()) {
        return null;  // Reject request
    }

After response:
    if (response.getCode() == 200) {
        circuitBreaker.recordSuccess();
    } else if (response.getCode() == 500) {
        circuitBreaker.recordFailure();
    }
```

---

### 7. GuavaRetry

**Location**: `/Users/ruoke/Documents/java_web/RPC-Java/version5/krpc-core/src/main/java/com/kama/client/retry/GuavaRetry.java`

**Role**: Implement retry logic for transient failures

**Configuration** (Fixed):
```java
Retryer<RpcResponse> retryer = RetryerBuilder.<RpcResponse>newBuilder()
    .retryIfException()                      // Retry any exception
    .retryIfResult(r -> r.getCode() == 500)  // Retry on server error
    .withWaitStrategy(fixedWait(2, SECONDS)) // 2 second delay
    .withStopStrategy(stopAfterAttempt(3))   // Max 3 attempts
    .withRetryListener(...)                  // Log each attempt
    .build();
```

**Retry Decision Tree**:
```
Request sent
    ↓
Exception thrown? → Yes → Retry
    ↓ No
Response received
    ↓
Code == 500? → Yes → Retry
    ↓ No
Code == 200? → Return result
    ↓ No
Code == ? → Return as-is
```

**Attempt Pattern**:
```
Attempt 1: T=0ms    (send immediately)
Attempt 2: T=2000ms (wait 2s, retry)
Attempt 3: T=4000ms (wait 2s, retry)
Fail: Return error after 4+ seconds
```

---

## Message Flow & Communication

### Complete Request-Response Cycle

```
┌─────────────────────────────────────────────────────────────────┐
│                        CONSUMER SIDE                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  Application Code                                                 │
│      ↓                                                             │
│  proxy.getUserByUserId(1)                                        │
│      ↓                                                             │
│  ClientProxy.invoke()                                            │
│      ├─ Build RpcRequest                                         │
│      │   interfaceName: "com.kama.service.UserService"          │
│      │   methodName: "getUserByUserId"                           │
│      │   params: [1]                                             │
│      │   paramsType: [Integer.class]                             │
│      │                                                             │
│      ├─ Get CircuitBreaker                                       │
│      │   → Check allowRequest()                                  │
│      │   → If OPEN: reject and return null                       │
│      │                                                             │
│      ├─ Service Discovery                                        │
│      │   serviceCenter.serviceDiscovery(request)                 │
│      │   → Query ZK for "com.kama.service.UserService"          │
│      │   → Get list: ["192.168.1.100:9999", ...]                │
│      │   → Load balance with ConsistencyHashBalance             │
│      │   → Return: InetSocketAddress(host, port)                │
│      │                                                             │
│      ├─ Check Retry Eligibility                                  │
│      │   serviceCenter.checkRetry(address, methodSignature)      │
│      │   → methodSignature: "com.kama.service.UserService#..."  │
│      │   → Query retry whitelist from ZK                         │
│      │   → Return: true/false                                    │
│      │                                                             │
│      ├─ Send Request                                             │
│      │   if (retry eligible) {                                   │
│      │       GuavaRetry.sendServiceWithRetry(request, client)    │
│      │       → Attempts: max 3                                   │
│      │       → Delay: 2 seconds between attempts                 │
│      │       → On exception or code=500: retry                   │
│      │   } else {                                                │
│      │       rpcClient.sendRequest(request)  // Direct send      │
│      │   }                                                        │
│      │                                                             │
│      └─ Handle Response                                          │
│          if (code == 200) circuitBreaker.recordSuccess()        │
│          if (code == 500) circuitBreaker.recordFailure()        │
│          return response.getData()                               │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
         Network Transmission (Netty)
┌─────────────────────────────────────────────────────────────────┐
│                        PROVIDER SIDE                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  NettyRpcServer listening on port                                │
│      ↓                                                             │
│  Accept connection                                               │
│      ↓                                                             │
│  MyDecoder deserializes bytes to RpcRequest                      │
│      ↓                                                             │
│  NettyRpcServerHandler.channelRead0(RpcRequest)                  │
│      ├─ Get rate limit token                                     │
│      │   rateLimit.getToken() → true/false                       │
│      │   → If false: return RATE_LIMIT error                     │
│      │                                                             │
│      ├─ Get service implementation                               │
│      │   service = serviceProvider.getService(interfaceName)     │
│      │   → Lookup in local interfaceProvider map                 │
│      │                                                             │
│      ├─ Invoke method using reflection                           │
│      │   method = service.getClass().getMethod(methodName, ...)  │
│      │   result = method.invoke(service, params)                 │
│      │                                                             │
│      └─ Build RpcResponse                                        │
│          RpcResponse.sussess(result)  // Note: typo in code      │
│          code: 200                                               │
│          dataType: result.getClass()                             │
│          data: result                                            │
│                                                                   │
│  MyEncoder serializes RpcResponse to bytes                       │
│      ↓                                                             │
│  Send response back to client                                    │
│      ↓                                                             │
│  Close channel                                                   │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
         Network Transmission (Netty)
┌─────────────────────────────────────────────────────────────────┐
│                     BACK TO CONSUMER                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  NettyClientHandler receives bytes from network                  │
│      ↓                                                             │
│  MyDecoder deserializes to RpcResponse                           │
│      ↓                                                             │
│  NettyClientHandler.channelRead0(RpcResponse)                    │
│      ├─ Store in channel attribute                               │
│      │   channel.attr(RESPONSE_KEY).set(response)                │
│      │                                                             │
│      └─ Close channel                                            │
│          → Causes closeFuture().sync() to complete               │
│                                                                   │
│  NettyRpcClient.sendRequest() resumes                            │
│      ├─ Retrieve response from attributes                        │
│      │   response = channel.attr(key).get()                      │
│      │                                                             │
│      └─ Return RpcResponse                                       │
│                                                                   │
│  ClientProxy receives response                                   │
│      ├─ Record success/failure                                   │
│      └─ Return response.getData() to caller                      │
│                                                                   │
│  Application receives result                                     │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

### Service Registration Flow

```
Provider Startup
    ↓
ProviderTest.main()
    ├─ KRpcApplication.initialize()
    │  └─ Load config from rpc.properties
    │
    ├─ new UserServiceImpl()
    │
    ├─ new ServiceProvider(host, port)
    │  └─ Initialize ServiceRegister (ZKServiceRegister)
    │     └─ Connect to ZK at 127.0.0.1:2181
    │
    ├─ serviceProvider.provideServiceInterface(userService)
    │  └─ For each interface:
    │     ├─ Register in local interfaceProvider map
    │     │  Key: "com.kama.service.UserService"
    │     │  Value: userService instance
    │     │
    │     └─ serviceRegister.register(interfaceClass, address)
    │        ├─ Create persistent node: /MyRPC/com.kama.service.UserService
    │        │
    │        └─ Create ephemeral node: /MyRPC/com.kama.service.UserService/localhost:9999
    │           └─ Auto-removes on disconnect
    │
    ├─ new NettyRpcServer(serviceProvider)
    │
    └─ rpcServer.start(port)
       └─ Listen on 0.0.0.0:9999
```

### Service Discovery Flow

```
Consumer Startup
    ↓
ConsumerTest.main()
    ├─ new ClientProxy()
    │  └─ Initialize ServiceCenter (ZKServiceCenter)
    │     ├─ Connect to ZK
    │     ├─ Create ServiceCache
    │     └─ Register watchZK listener
    │        └─ On ZK changes:
    │           ├─ Add node: cache.addServiceToCache(serviceName, address)
    │           ├─ Delete node: cache.delete(serviceName, address)
    │           └─ Update node: cache.replaceServiceAddress()
    │
    └─ proxy = clientProxy.getProxy(UserService.class)
       └─ Return JDK dynamic proxy instance
```

### Request to Specific Provider

```
Consumer calls: proxy.getUserByUserId(1)
    ↓
ClientProxy.invoke() with address selection
    ├─ Query ServiceCenter
    │  └─ Check local cache first
    │     → Hit: return cached addresses
    │     → Miss: query ZK /MyRPC/com.kama.service.UserService
    │            → ["localhost:9999", "localhost:9998"]
    │            → Add to cache
    │
    ├─ Load balance selection
    │  └─ ConsistencyHashBalance.balance(["localhost:9999", "localhost:9998"])
    │     ├─ Generate UUID for request
    │     ├─ Hash UUID to ring
    │     ├─ Find next node clockwise
    │     └─ Return selected address
    │
    ├─ Create NettyRpcClient for selected address
    │
    └─ Send request via client
```

---

## Configuration System

### Configuration Hierarchy

```
┌─────────────────────────────────────────┐
│  External Properties/Environment        │
│  (rpc.properties, rpc.yaml, env vars)   │
└──────────────┬──────────────────────────┘
               ↓
┌─────────────────────────────────────────┐
│  ConfigUtil.loadConfig(KRpcConfig.class,│
│  "rpc")                                  │
│  (Loads from rpc.properties)             │
└──────────────┬──────────────────────────┘
               ↓
┌─────────────────────────────────────────┐
│  KRpcConfig (POJO with Lombok)          │
│  - All properties with defaults         │
│  - Builder pattern support              │
└──────────────┬──────────────────────────┘
               ↓
┌─────────────────────────────────────────┐
│  KRpcApplication (Singleton)            │
│  - Double-checked locking               │
│  - getRpcConfig() returns instance      │
└─────────────────────────────────────────┘
```

### KRpcConfig Properties

**File**: `/Users/ruoke/Documents/java_web/RPC-Java/version5/krpc-core/src/main/java/com/kama/config/KRpcConfig.java`

```java
@Data
@Builder
public class KRpcConfig {
    // Application identification
    String name = "krpc"              // Framework name
    Integer port = 9999              // Server listen port
    String host = "localhost"         // Server hostname
    String version = "1.0.0"          // Service version
    
    // Infrastructure selection
    String registry = "nacos"         // Service registry (ZK/Nacos/etc)
    String serializer = "hessian"     // Serialization format (Hessian default = Type 3)
    String loadBalance = "round-robin" // Load balancing algorithm
}
```

### Configuration File Example

**File**: `rpc.properties` (loaded by ConfigUtil)

```properties
# Application
name=krpc
port=9999
host=192.168.1.100
version=1.0.0

# Infrastructure
registry=nacos
serializer=hessian
loadBalance=consistency-hash
```

### Programmatic Configuration

```java
// Option 1: Auto-load from properties file
KRpcApplication.initialize();
KRpcConfig config = KRpcApplication.getRpcConfig();

// Option 2: Custom configuration
KRpcConfig customConfig = KRpcConfig.builder()
    .name("my-rpc")
    .port(8888)
    .host("0.0.0.0")
    .registry("nacos")
    .serializer("json")
    .loadBalance("consistency-hash")
    .build();
KRpcApplication.initialize(customConfig);

// Option 3: Access singleton
KRpcConfig config = KRpcApplication.getRpcConfig();
int port = config.getPort();           // 9999
String host = config.getHost();        // "localhost"
String serializer = config.getSerializer(); // "hessian"
```

### Initialization Patterns

**Provider Initialization**:
```java
KRpcApplication.initialize();  // Load config
String host = KRpcApplication.getRpcConfig().getHost();
int port = KRpcApplication.getRpcConfig().getPort();
```

**Consumer Initialization**:
```java
// Implicit: ClientProxy constructor initializes on first access
ClientProxy proxy = new ClientProxy();
// ServiceCenter and CircuitBreakerProvider are created
// Configuration happens internally
```

---

## Advanced Features

### Circuit Breaker Integration

**Per-Method Isolation**:
- Each method gets its own CircuitBreaker instance
- Failures in one method don't affect others
- Provider: CircuitBreakerProvider (per-method factory)

**State Transitions**:
```java
// In CircuitBreaker.recordFailure()
if (failureCount >= threshold) {
    state = OPEN;  // Stop accepting requests
}

// In CircuitBreaker.allowRequest()
if (state == OPEN && elapsed > retryTimePeriod) {
    state = HALF_OPEN;  // Allow test requests
}

// In CircuitBreaker.recordSuccess()
if (state == HALF_OPEN && successRate >= threshold) {
    state = CLOSED;  // Resume normal operation
}
```

### Retry Whitelist Mechanism

**How it Works**:
1. Provider detects @Retryable annotation on methods
2. Registers method signature in ZK: `/CanRetry/{host:port}/{methodSignature}`
3. Consumer queries this path during service discovery
4. Only whitelisted methods use GuavaRetry
5. Non-whitelisted methods send once (fail-fast)

**Method Signature Format**:
```
com.kama.service.UserService#getUserByUserId(java.lang.Integer)
```

### Load Balancing Details

**Consistency Hash Algorithm**:
```
Real Nodes:
  - "localhost:9999"
  - "localhost:9998"

Virtual Nodes (5 each):
  - "localhost:9999&&VN0" → Hash: 1234
  - "localhost:9999&&VN1" → Hash: 5678
  - "localhost:9999&&VN2" → Hash: 9012
  - "localhost:9999&&VN3" → Hash: 3456
  - "localhost:9999&&VN4" → Hash: 7890
  - "localhost:9998&&VN0" → Hash: 2345
  - ...

Request Routing:
  1. Generate UUID: "a1b2c3d4"
  2. Hash: 4567
  3. Find first node hash >= 4567 in ring
  4. If none found, wrap to smallest hash
  5. Extract real node from virtual node
```

### Rate Limiting Implementation

**Token Bucket Algorithm**:
```
Configuration:
  - rate: 1000ms (one token per second)
  - capacity: 100 tokens

State:
  - curCapacity: 50 tokens available
  - lastTimestamp: 1000ms ago

Request 1: 
  → curCapacity > 0 → Grant (49 left)

Request 2 (1500ms later):
  → curCapacity = 0
  → elapsed = 1500ms
  → generated = 1500/1000 = 1 token
  → curCapacity = min(100, 0+1) = 1
  → Grant (0 left)
```

### Service Discovery with Caching

**Cache Structure**:
```
ServiceCache.cache: ConcurrentHashMap<String, List<String>>

Example:
{
  "com.kama.service.UserService" → ["localhost:9999", "localhost:9998"],
  "com.kama.service.OrderService" → ["localhost:9997"]
}
```

**Update Triggers**:
- watchZK listener receives ZK events
- On node create: addServiceToCache()
- On node delete: delete()
- On node update: replaceServiceAddress()

---

## Summary

### Architecture Layers

```
┌─────────────────────────────────────────────────────────┐
│         Application Layer                                │
│  (Consumer/Provider code)                               │
├─────────────────────────────────────────────────────────┤
│         RPC Proxy/Service Provider Layer                │
│  (ClientProxy, ServiceProvider)                          │
├─────────────────────────────────────────────────────────┤
│         RPC Framework Layer                             │
│  (Service Center, Load Balance, Circuit Breaker, Retry) │
├─────────────────────────────────────────────────────────┤
│         Transport Layer                                  │
│  (Netty RPC Client/Server)                              │
├─────────────────────────────────────────────────────────┤
│         Serialization Layer                             │
│  (MyEncoder/MyDecoder, Serializer implementations)      │
├─────────────────────────────────────────────────────────┤
│         Registry Layer                                  │
│  (NacosServiceRegister, NacosServiceCenter)             │
├─────────────────────────────────────────────────────────┤
│         Infrastructure Layer                            │
│  (Nacos, Netty EventLoops)                              │
└─────────────────────────────────────────────────────────┘
```

### Key Design Patterns

1. **Proxy Pattern**: ClientProxy uses JDK dynamic proxy
2. **Factory Pattern**: CircuitBreakerProvider, RateLimitProvider, Serializer factory
3. **Observer Pattern**: watchZK listener for cache updates
4. **Circuit Breaker Pattern**: For fault tolerance
5. **Singleton Pattern**: KRpcApplication, EventLoopGroup
6. **Strategy Pattern**: LoadBalance, Serializer implementations
7. **Decorator Pattern**: GuavaRetry wraps RpcClient
8. **Chain of Responsibility**: Netty pipeline handlers

### Performance Optimizations

1. **Shared EventLoopGroup**: Single pool across all clients
2. **Service Caching**: LocalServiceCache avoids ZK queries
3. **Consistency Hash**: O(log n) server selection
4. **Rate Limiting**: Token bucket prevents overload
5. **Circuit Breaker**: Fail-fast prevents cascading failures
6. **Async I/O**: Netty for non-blocking I/O

---

## File Reference Summary

| Component | File Path |
|-----------|-----------|
| RpcRequest | `/krpc-common/.../RpcRequest.java` |
| RpcResponse | `/krpc-common/.../RpcResponse.java` |
| Serializer | `/krpc-common/.../Serializer.java` |
| MyEncoder | `/krpc-common/.../MyEncoder.java` |
| MyDecoder | `/krpc-common/.../MyDecoder.java` |
| ServiceRegister | `/krpc-core/.../ServiceRegister.java` |
| ZKServiceRegister | `/krpc-core/.../ZKServiceRegister.java` |
| ServiceCenter | `/krpc-core/.../ServiceCenter.java` |
| ZKServiceCenter | `/krpc-core/.../ZKServiceCenter.java` |
| LoadBalance | `/krpc-core/.../LoadBalance.java` |
| ConsistencyHashBalance | `/krpc-core/.../ConsistencyHashBalance.java` |
| RpcClient | `/krpc-core/.../RpcClient.java` |
| NettyRpcClient | `/krpc-core/.../NettyRpcClient.java` |
| RpcServer | `/krpc-core/.../RpcServer.java` |
| NettyRpcServer | `/krpc-core/.../NettyRpcServer.java` |
| ClientProxy | `/krpc-core/.../ClientProxy.java` |
| ServiceProvider | `/krpc-core/.../ServiceProvider.java` |
| CircuitBreaker | `/krpc-core/.../CircuitBreaker.java` |
| GuavaRetry | `/krpc-core/.../GuavaRetry.java` |
| KRpcApplication | `/krpc-core/.../KRpcApplication.java` |
| KRpcConfig | `/krpc-core/.../KRpcConfig.java` |

