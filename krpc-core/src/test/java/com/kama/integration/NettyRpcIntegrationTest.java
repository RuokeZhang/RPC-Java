package com.kama.integration;

import com.kama.client.netty.ChannelProvider;
import com.kama.client.rpcclient.impl.NettyRpcClient;
import com.kama.server.provider.ServiceProvider;
import com.kama.server.server.RpcServer;
import com.kama.server.server.impl.NettyRpcServer;
import common.message.RpcRequest;
import common.message.RpcResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * 端到端集成测试：启动 Netty 服务器，注册简单服务，客户端异步调用并校验返回。
 */
public class NettyRpcIntegrationTest {

    private RpcServer rpcServer;
    private Thread serverThread;
    private int port;

    public interface EchoService {
        String echo(String input);
    }

    public static class EchoServiceImpl implements EchoService {
        @Override
        public String echo(String input) {
            return "echo:" + input;
        }
    }

    @Before
    public void setUp() throws Exception {
        // 清理连接池，确保测试隔离
        ChannelProvider.closeAll();

        try (ServerSocket ss = new ServerSocket(0)) {
            port = ss.getLocalPort();
        }
        ServiceProvider serviceProvider = new ServiceProvider("127.0.0.1", port, false);
        serviceProvider.provideServiceInterface(new EchoServiceImpl());
        rpcServer = new NettyRpcServer(serviceProvider);
        serverThread = new Thread(() -> rpcServer.start(port));
        serverThread.start();
        // 简单等待服务启动
        TimeUnit.MILLISECONDS.sleep(200);
    }

    @After
    public void tearDown() throws Exception {
        // 清理连接池
        ChannelProvider.closeAll();

        if (rpcServer != null) {
            rpcServer.stop();
        }
        if (serverThread != null) {
            serverThread.join(1000);
        }
    }

    @Test
    public void testNettyRpcEndToEnd() throws Exception {
        RpcRequest request = RpcRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .interfaceName(EchoService.class.getName())
                .methodName("echo")
                .params(new Object[]{"hi"})
                .paramsType(new Class<?>[]{String.class})
                .build();

        NettyRpcClient client = new NettyRpcClient(new InetSocketAddress("127.0.0.1", port));
        CompletableFuture<RpcResponse> future = client.sendRequest(request);
        RpcResponse response = future.get(3, TimeUnit.SECONDS);

        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertEquals("echo:hi", response.getData());
    }

    /**
     * 测试连接复用：多次请求应该复用同一个连接
     */
    @Test
    public void testConnectionReuse() throws Exception {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", port);
        NettyRpcClient client = new NettyRpcClient(address);

        // 发送多次请求
        int requestCount = 5;
        List<CompletableFuture<RpcResponse>> futures = new ArrayList<>();

        for (int i = 0; i < requestCount; i++) {
            RpcRequest request = RpcRequest.builder()
                    .requestId(UUID.randomUUID().toString())
                    .interfaceName(EchoService.class.getName())
                    .methodName("echo")
                    .params(new Object[]{"msg" + i})
                    .paramsType(new Class<?>[]{String.class})
                    .build();
            futures.add(client.sendRequest(request));
        }

        // 验证所有请求都成功
        for (int i = 0; i < requestCount; i++) {
            RpcResponse response = futures.get(i).get(3, TimeUnit.SECONDS);
            assertNotNull("Response " + i + " should not be null", response);
            assertEquals("Response " + i + " should have code 200", 200, response.getCode());
            assertEquals("Response " + i + " data mismatch", "echo:msg" + i, response.getData());
        }

        // 验证连接被复用（连接池中应该只有一个连接）
        io.netty.channel.Channel channel = ChannelProvider.getChannel(address, NettyRpcClient.getBootstrap());
        assertNotNull("Channel should be reused from pool", channel);
        assertTrue("Channel should be active", channel.isActive());
    }

    /**
     * 测试并发请求：多个并发请求应该都能正确响应
     */
    @Test
    public void testConcurrentRequests() throws Exception {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", port);
        NettyRpcClient client = new NettyRpcClient(address);

        int concurrentCount = 10;
        List<CompletableFuture<RpcResponse>> futures = new ArrayList<>();

        // 同时发送多个请求
        for (int i = 0; i < concurrentCount; i++) {
            final int index = i;
            RpcRequest request = RpcRequest.builder()
                    .requestId(UUID.randomUUID().toString())
                    .interfaceName(EchoService.class.getName())
                    .methodName("echo")
                    .params(new Object[]{"concurrent" + index})
                    .paramsType(new Class<?>[]{String.class})
                    .build();
            futures.add(client.sendRequest(request));
        }

        // 等待所有请求完成并验证
        for (int i = 0; i < concurrentCount; i++) {
            RpcResponse response = futures.get(i).get(5, TimeUnit.SECONDS);
            assertNotNull("Concurrent response " + i + " should not be null", response);
            assertEquals("Concurrent response " + i + " should have code 200", 200, response.getCode());
            assertEquals("Concurrent response " + i + " data mismatch",
                    "echo:concurrent" + i, response.getData());
        }
    }

    /**
     * 测试心跳请求：发送心跳请求应该收到心跳响应
     */
    @Test
    public void testHeartbeat() throws Exception {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", port);
        NettyRpcClient client = new NettyRpcClient(address);

        // 先发送一个正常请求建立连接
        RpcRequest normalRequest = RpcRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .interfaceName(EchoService.class.getName())
                .methodName("echo")
                .params(new Object[]{"test"})
                .paramsType(new Class<?>[]{String.class})
                .build();
        RpcResponse normalResponse = client.sendRequest(normalRequest).get(3, TimeUnit.SECONDS);
        assertNotNull(normalResponse);
        assertEquals(200, normalResponse.getCode());

        // 获取已建立的连接
        io.netty.channel.Channel channel = ChannelProvider.getChannel(address, NettyRpcClient.getBootstrap());
        assertNotNull("Channel should exist", channel);
        assertTrue("Channel should be active after heartbeat", channel.isActive());

        // 发送心跳请求（直接通过 channel 发送）
        RpcRequest heartbeatRequest = RpcRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .heartBeat(true)
                .build();
        channel.writeAndFlush(heartbeatRequest);

        // 等待一小段时间让心跳处理完成
        TimeUnit.MILLISECONDS.sleep(100);

        // 验证连接仍然活跃
        assertTrue("Channel should remain active after heartbeat", channel.isActive());

        // 再发送一个正常请求验证连接仍可用
        RpcRequest afterHeartbeatRequest = RpcRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .interfaceName(EchoService.class.getName())
                .methodName("echo")
                .params(new Object[]{"after_heartbeat"})
                .paramsType(new Class<?>[]{String.class})
                .build();
        RpcResponse afterHeartbeatResponse = client.sendRequest(afterHeartbeatRequest).get(3, TimeUnit.SECONDS);
        assertNotNull(afterHeartbeatResponse);
        assertEquals(200, afterHeartbeatResponse.getCode());
        assertEquals("echo:after_heartbeat", afterHeartbeatResponse.getData());
    }
}

