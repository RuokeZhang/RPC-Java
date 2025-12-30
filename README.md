## KRPC (Version 5)

A minimal Java RPC framework demo.

### Modules
- krpc-api: public interfaces and models
- krpc-common: message schema, serializers, codec
- krpc-core: client/server, load-balancer, retry, breaker
- krpc-provider: service implementation (provider demo)
- krpc-consumer: client caller (consumer demo)

### How it works
- Provider registers and exposes services
- Consumer calls via dynamic proxy
- Netty transport with pluggable serialization
- Service discovery and load balancing

### Quick start
1. Run provider: `ProviderTest`
2. Run consumer: `ConsumerTest`

That's it.


## Netty

- 基于 NIO 的多路复用框架，用 Pipeline 组织编码 / 解码 / 业务处理

- 服务端（ServerBootstrap）
  - 线程模型：boss 负责新连接，worker 负责 IO
  - Channel：`NioServerSocketChannel`
  - Pipeline：
    - Encoder / Decoder：将 `RpcRequest`、`RpcResponse` 与字节流互转
    - Handler：拿到 `RpcRequest` 的 `interfaceName` 与 `requestId`，从本地映射表取得 service 实例，反射调用后返回带相同 `requestId` 的 `RpcResponse`
  - 关键代码：
    ```java
    pipeline.addLast(new MyEncoder(Serializer.getSerializerByCode(3)));
    pipeline.addLast(new MyDecoder());
    pipeline.addLast(new NettyRpcServerHandler(serviceProvider));
    ```

- 客户端（ClientBootstrap）
  - 服务发现：
    ```java
    InetSocketAddress serviceAddress = serviceCenter.serviceDiscovery(request);
    rpcClient = new NettyRpcClient(serviceAddress);
    ```
  - 发送请求（异步 Future）：
    ```java
    CompletableFuture<RpcResponse> future = rpcClient.sendRequest(request);
    // 如果业务方法返回 Future，可直接返回；否则 join 同步拿结果
    ```
  - 短连接流程：
    1) `bootstrap.connect(host, port)` 建立连接  
    2) `writeAndFlush(request)` 发送  
    3) `NettyClientHandler` 收到响应后，按 `requestId` 完成对应 Future，并关闭通道
  - 业务侧处理：
    ```java
    CompletableFuture<Object> mapped = future.thenApply(resp -> resp.getData());
    // 返回 CompletableFuture 或 mapped.join()
    ```

- 请求 / 响应结构
  - `RpcRequest` 增加 `requestId`，用于匹配响应
  - `RpcResponse` 同样携带 `requestId`
  - 客户端维护 `PENDING_FUTURES<requestId, CompletableFuture<RpcResponse>>`，用于路由响应


