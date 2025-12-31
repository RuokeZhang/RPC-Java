package com.kama.client.rpcclient.impl;

import com.kama.client.netty.ChannelProvider;
import com.kama.client.netty.NettyClientInitializer;
import com.kama.client.rpcclient.RpcClient;
import common.message.RpcRequest;
import common.message.RpcResponse;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;


import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
public class NettyRpcClient implements RpcClient {

    private static final Bootstrap bootstrap;
    private static final EventLoopGroup eventLoopGroup;
    private static final Map<String, CompletableFuture<RpcResponse>> PENDING_FUTURES = new ConcurrentHashMap<>();

    private final InetSocketAddress address;

    public NettyRpcClient(InetSocketAddress serviceAddress) {
        this.address = serviceAddress;
    }

    // Netty客户端初始化
    static {
        eventLoopGroup = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup).channel(NioSocketChannel.class)
                .handler(new NettyClientInitializer());
    }

    /**
     * 获取 Bootstrap 实例，供 ChannelProvider 使用
     */
    public static Bootstrap getBootstrap() {
        return bootstrap;
    }

    @Override
    public CompletableFuture<RpcResponse> sendRequest(RpcRequest request) {
        if (request.getRequestId() == null || request.getRequestId().isEmpty()) {
            request.setRequestId(UUID.randomUUID().toString());
        }
        CompletableFuture<RpcResponse> responseFuture = new CompletableFuture<>();
        PENDING_FUTURES.put(request.getRequestId(), responseFuture);

        if (address == null) {
            log.error("服务发现失败，返回的地址为 null");
            completeExceptionally(request.getRequestId(), new IllegalStateException("服务发现失败，地址为 null"));
            return responseFuture;
        }

        try {
            // 使用连接池获取复用的 Channel
            Channel channel = ChannelProvider.getChannel(address, bootstrap);
            if (channel == null || !channel.isActive()) {
                log.error("获取连接失败: {}", address);
                completeExceptionally(request.getRequestId(), new IllegalStateException("获取连接失败"));
                return responseFuture;
            }

            // 发送请求
            channel.writeAndFlush(request).addListener(writeFuture -> {
                if (!writeFuture.isSuccess()) {
                    log.error("发送请求失败: {}", writeFuture.cause().getMessage(), writeFuture.cause());
                    completeExceptionally(request.getRequestId(), writeFuture.cause());
                    // 发送失败时移除该连接，下次重新建立
                    ChannelProvider.removeChannel(address);
                }
            });
        } catch (Exception e) {
            log.error("发送请求时发生异常: {}", e.getMessage(), e);
            completeExceptionally(request.getRequestId(), e);
        }
        return responseFuture;
    }

    // 优雅关闭 Netty 资源
    public void close() {
        try {
            // 关闭所有连接池中的连接
            ChannelProvider.closeAll();
            if (eventLoopGroup != null) {
                eventLoopGroup.shutdownGracefully().sync();
            }
        } catch (InterruptedException e) {
            log.error("关闭 Netty 资源时发生异常: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }

    public static void completeResponse(RpcResponse response) {
        if (response == null || response.getRequestId() == null) {
            log.warn("收到空响应或无请求ID的响应，无法路由回调");
            return;
        }
        CompletableFuture<RpcResponse> future = PENDING_FUTURES.remove(response.getRequestId());
        if (future != null && !future.isDone()) {
            future.complete(response);
        }
    }

    private void completeExceptionally(String requestId, Throwable throwable) {
        CompletableFuture<RpcResponse> future = PENDING_FUTURES.remove(requestId);
        if (future != null && !future.isDone()) {
            future.completeExceptionally(throwable);
        }
    }
}
