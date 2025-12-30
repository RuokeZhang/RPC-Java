package com.kama.server.provider;


import com.kama.server.ratelimit.provider.RateLimitProvider;

import com.kama.server.serviceRegister.ServiceRegister;
import com.kama.server.serviceRegister.impl.NacosServiceRegister;


import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;



public class ServiceProvider {
    private Map<String, Object> interfaceProvider;

    private int port;
    private String host;
    //注册服务类
    private ServiceRegister serviceRegister;
    //限流器
    private RateLimitProvider rateLimitProvider;
    // 是否向注册中心注册，测试场景可关闭
    private boolean enableRegister = true;

    public ServiceProvider(String host, int port) {
        this(host, port, true);
    }

    public ServiceProvider(String host, int port, boolean enableRegister) {
        //需要传入服务端自身的网络地址
        this.host = host;
        this.port = port;
        this.enableRegister = enableRegister;
        this.interfaceProvider = new HashMap<>();
        this.rateLimitProvider = new RateLimitProvider();
        //仅在需要注册时才初始化 Nacos 客户端，避免本地测试强依赖注册中心
        this.serviceRegister = enableRegister ? new NacosServiceRegister() : null;
    }

    public void provideServiceInterface(Object service) {

        Class<?>[] interfaceName = service.getClass().getInterfaces();

        for (Class<?> clazz : interfaceName) {
            //本机的映射表
            interfaceProvider.put(clazz.getName(), service);
            if (enableRegister && serviceRegister != null) {
                //在注册中心注册服务
                serviceRegister.register(clazz, new InetSocketAddress(host, port));
            }
        }
    }

    public Object getService(String interfaceName) {
        return interfaceProvider.get(interfaceName);
    }

    public RateLimitProvider getRateLimitProvider() {
        return rateLimitProvider;
    }
}
