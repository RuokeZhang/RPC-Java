package com.kama.client.proxy;

import com.kama.client.circuitbreaker.CircuitBreaker;
import com.kama.client.circuitbreaker.CircuitBreakerProvider;
import com.kama.client.retry.GuavaRetry;
import com.kama.client.rpcclient.RpcClient;
import com.kama.client.rpcclient.impl.NettyRpcClient;
import com.kama.client.servicecenter.ServiceCenter;
import com.kama.client.servicecenter.NacosServiceCenter;


import common.message.RpcRequest;
import common.message.RpcResponse;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


@Slf4j
public class ClientProxy implements InvocationHandler {
    //传入参数service接口的class对象，反射封装成一个request

    private RpcClient rpcClient;
    private ServiceCenter serviceCenter;
    private CircuitBreakerProvider circuitBreakerProvider;

    public ClientProxy() throws InterruptedException {
        serviceCenter = new NacosServiceCenter();
        circuitBreakerProvider = new CircuitBreakerProvider();
    }

    //jdk动态代理，每一次代理对象调用方法，都会经过此方法增强（反射获取request对象，socket发送到服务端）
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        //构建request
        RpcRequest request = RpcRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .interfaceName(method.getDeclaringClass().getName())
                .methodName(method.getName())
                .params(args).paramsType(method.getParameterTypes()).build();
        //获取熔断器
        CircuitBreaker circuitBreaker = circuitBreakerProvider.getCircuitBreaker(method.getName());
        //判断熔断器是否允许请求经过
        if (!circuitBreaker.allowRequest()) {
            log.warn("熔断器开启，请求被拒绝: {}", request);
            //这里可以针对熔断做特殊处理，返回特殊值
            return null;
        }
        //数据传输
        CompletableFuture<RpcResponse> responseFuture;
        //后续添加逻辑：为保持幂等性，只对白名单上的服务进行重试
        // 如果启用重试机制，先检查是否需要重试
        String methodSignature = getMethodSignature(request.getInterfaceName(), method);
        log.info("方法签名: " + methodSignature);
        InetSocketAddress serviceAddress = serviceCenter.serviceDiscovery(request);
        rpcClient = new NettyRpcClient(serviceAddress);
        if (serviceCenter.checkRetry(serviceAddress, methodSignature)) {
            //调用retry框架进行重试操作
            log.info("尝试重试调用服务: {}", methodSignature);
            responseFuture = CompletableFuture.supplyAsync(() -> new GuavaRetry().sendServiceWithRetry(request, rpcClient));
        } else {
            //只调用一次
            responseFuture = rpcClient.sendRequest(request);
        }
        CompletableFuture<Object> mappedFuture = responseFuture.thenApply(response -> {
            if (response != null && response.getCode() == 200) {
                circuitBreaker.recordSuccess();
                log.info("收到响应: {} 状态码: {}", request.getInterfaceName(), response.getCode());
                return response.getData();
            } else {
                circuitBreaker.recordFailure();
                log.warn("收到失败响应或空响应: {}", response);
                return response != null ? response.getData() : null;
            }
        }).exceptionally(ex -> {
            circuitBreaker.recordFailure();
            log.error("调用过程中发生异常: {}", ex.getMessage(), ex);
            throw new RuntimeException(ex);
        });

        // 如果用户方法返回 CompletableFuture，则直接返回异步结果
        if (CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
            return mappedFuture;
        }
        // 否则保持兼容，阻塞等待结果
        return mappedFuture.join();
    }

    public <T> T getProxy(Class<T> clazz) {
        Object o = Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, this);
        return (T) o;
    }

    // 根据接口名字和方法获取方法签名
    private String getMethodSignature(String interfaceName, Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(interfaceName).append("#").append(method.getName()).append("(");
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            sb.append(parameterTypes[i].getName());
            if (i < parameterTypes.length - 1) {
                sb.append(",");
            } else{
                sb.append(")");
            }
        }
        return sb.toString();
    }

    //关闭创建的资源
    //注：如果在需要C-S保持长连接的场景下无需调用close方法
    public void close(){
        rpcClient.close();
        serviceCenter.close();
    }
}
