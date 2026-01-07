## Netty
### 如何处理TCP粘包和拆包（Packet Splicing/Splitting）
1. TCP is a stream-oriented protocol
2. Protocol Design: a fixed-length field representing the body_length
3. I use Netty's LengthFieldBasedFrameDecoder
4. The decoder first accumulates bytes until it has enough to read the Length Header. Once it knows the body length, it waits until enough bytes arrive to construct a full message.
5. Result: Only complete, assembled frames are passed to the next ChannelHandler
### What is Reactor Pattern
1. Serverboostrap: Boss and Worker Thread group
    - epoll
    - 每个worker对应一个单独的NioEventLoop线程
2. Pipeline and Handlers

### 在 RPC 框架中，如何实现服务的注册与发现，以及服务的版本管理
