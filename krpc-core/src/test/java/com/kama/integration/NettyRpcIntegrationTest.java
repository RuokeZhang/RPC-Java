package com.kama.integration;

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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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

        client.close();
    }
}

