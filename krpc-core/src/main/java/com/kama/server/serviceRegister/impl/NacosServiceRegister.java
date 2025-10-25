package com.kama.server.serviceRegister.impl;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.kama.annotation.Retryable;
import com.kama.server.serviceRegister.ServiceRegister;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
public class NacosServiceRegister implements ServiceRegister {
    private NamingService namingService;
    private static final String SERVER_ADDR = "127.0.0.1:8848";
    private static final String RETRY_METADATA_KEY = "retryableMethods";

    public NacosServiceRegister() {
        try {
            this.namingService = NamingFactory.createNamingService(SERVER_ADDR);
            log.info("Nacos 连接成功，服务器地址: {}", SERVER_ADDR);
        } catch (NacosException e) {
            log.error("Nacos 连接失败: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to connect to Nacos", e);
        }
    }

    @Override
    public void register(Class<?> clazz, InetSocketAddress serviceAddress) {
        String serviceName = clazz.getName();
        try {
            Instance instance = new Instance();
            instance.setIp(serviceAddress.getHostName());
            instance.setPort(serviceAddress.getPort());
            instance.setHealthy(true);
            instance.setWeight(1.0);

            // 添加元数据：可重试方法列表
            Map<String, String> metadata = new HashMap<>();
            List<String> retryableMethods = getRetryableMethod(clazz);
            if (!retryableMethods.isEmpty()) {
                metadata.put(RETRY_METADATA_KEY, String.join(",", retryableMethods));
                log.info("可重试的方法: {}", retryableMethods);
            }
            instance.setMetadata(metadata);

            namingService.registerInstance(serviceName, instance);
            log.info("服务注册成功 - 服务名: {}, 地址: {}:{}",
                    serviceName, serviceAddress.getHostName(), serviceAddress.getPort());
        } catch (NacosException e) {
            log.error("服务注册失败，服务名：{}，错误信息：{}", serviceName, e.getMessage(), e);
            throw new RuntimeException("Failed to register service to Nacos", e);
        }
    }

    @Override
    public String toString() {
        return "nacos";
    }

    // 判断一个方法是否加了Retryable注解
    private List<String> getRetryableMethod(Class<?> clazz) {
        List<String> retryableMethods = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Retryable.class)) {
                String methodSignature = getMethodSignature(clazz, method);
                retryableMethods.add(methodSignature);
            }
        }
        return retryableMethods;
    }

    private String getMethodSignature(Class<?> clazz, Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(clazz.getName()).append("#").append(method.getName()).append("(");
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            sb.append(parameterTypes[i].getName());
            if (i < parameterTypes.length - 1) {
                sb.append(",");
            } else {
                sb.append(")");
            }
        }
        return sb.toString();
    }
}
