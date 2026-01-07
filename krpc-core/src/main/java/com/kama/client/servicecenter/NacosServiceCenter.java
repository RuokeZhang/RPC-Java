package com.kama.client.servicecenter;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.kama.client.servicecenter.balance.LoadBalance;
import com.kama.client.servicecenter.balance.impl.RoundLoadBalance;
import common.message.RpcRequest;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


@Slf4j
public class NacosServiceCenter implements ServiceCenter {
    private NamingService namingService;
    private static final String SERVER_ADDR = "127.0.0.1:8848";
    private static final String RETRY_METADATA_KEY = "retryableMethods";

    // 本地缓存，用于缓存可重试方法
    private final Map<String, Set<String>> retryMethodCache = new ConcurrentHashMap<>();

    // 本地缓存，用于兜底服务地址（避免 Nacos 暂时不可用时完全不可用）
    private final Map<String, List<String>> serviceAddressCache = new ConcurrentHashMap<>();

    // 负载均衡器，默认使用轮询
    private final LoadBalance loadBalance;

    // 负责Nacos客户端的初始化，并与Nacos服务端进行连接
    public NacosServiceCenter() {
        this(new RoundLoadBalance());
    }

    public NacosServiceCenter(LoadBalance loadBalance) {
        this.loadBalance = loadBalance;
        try {
            this.namingService = NamingFactory.createNamingService(SERVER_ADDR);
            log.info("Nacos 连接成功，服务器地址: {}", SERVER_ADDR);
        } catch (NacosException e) {
            log.error("Nacos 连接失败: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to connect to Nacos", e);
        }
    }

    // 根据服务名（接口名）返回地址
    @Override
    public InetSocketAddress serviceDiscovery(RpcRequest request) {
        String serviceName = request.getInterfaceName();
        try {
            // 从Nacos获取健康的服务实例列表
            List<Instance> instances = namingService.selectInstances(serviceName, true);

            if (instances == null || instances.isEmpty()) {
                log.warn("未找到服务：{}", serviceName);
                return null;
            }

            // 将Instance转换为地址字符串列表
            List<String> addressList = new ArrayList<>();
            for (Instance instance : instances) {
                String address = instance.getIp() + ":" + instance.getPort();
                addressList.add(address);
            }

            // 负载均衡得到地址
            String address = loadBalance.balance(addressList);
            log.info("服务发现成功 - 服务名: {}, 选择地址: {}", serviceName, address);
            // 更新本地缓存
            serviceAddressCache.put(serviceName, new CopyOnWriteArrayList<>(addressList));
            return parseAddress(address);
        } catch (NacosException e) {
            log.error("服务发现失败，服务名：{}，尝试使用本地缓存", serviceName, e);
        }

        // Nacos 异常或不可达时，尝试使用本地缓存兜底
        List<String> cachedAddresses = serviceAddressCache.get(serviceName);
        if (cachedAddresses != null && !cachedAddresses.isEmpty()) {
            String cachedAddress = loadBalance.balance(cachedAddresses);
            log.warn("使用本地缓存服务地址 - 服务名: {}, 选择地址: {}", serviceName, cachedAddress);
            return parseAddress(cachedAddress);
        }
        log.warn("服务发现失败且无缓存可用，服务名: {}", serviceName);
        return null;
    }

    // 判断是否可重试
    @Override
    public boolean checkRetry(InetSocketAddress serviceAddress, String methodSignature) {
        String addressKey = getServiceAddress(serviceAddress);

        // 如果缓存中已有该地址的重试方法列表，直接查询
        if (retryMethodCache.containsKey(addressKey)) {
            return retryMethodCache.get(addressKey).contains(methodSignature);
        }

        // 否则从Nacos获取该服务的所有实例，查找对应实例的元数据
        try {
            // 从方法签名中提取服务名（格式: com.example.Service#method(args)）
            String serviceName = methodSignature.substring(0, methodSignature.indexOf("#"));
            List<Instance> instances = namingService.getAllInstances(serviceName);

            for (Instance instance : instances) {
                String instanceAddress = instance.getIp() + ":" + instance.getPort();
                if (instanceAddress.equals(addressKey)) {
                    Map<String, String> metadata = instance.getMetadata();
                    if (metadata != null && metadata.containsKey(RETRY_METADATA_KEY)) {
                        String retryMethods = metadata.get(RETRY_METADATA_KEY);
                        Set<String> methodSet = new HashSet<>(Arrays.asList(retryMethods.split(",")));
                        retryMethodCache.put(addressKey, methodSet);
                        return methodSet.contains(methodSignature);
                    }
                }
            }
        } catch (NacosException e) {
            log.error("检查重试失败，方法签名：{}", methodSignature, e);
        }

        // 如果没有找到元数据，缓存空集合
        retryMethodCache.put(addressKey, new HashSet<>());
        return false;
    }

    @Override
    public void close() {
        try {
            if (namingService != null) {
                namingService.shutDown();
                log.info("Nacos 服务已关闭");
            }
        } catch (NacosException e) {
            log.error("关闭 Nacos 服务失败: {}", e.getMessage(), e);
        }
    }

    // 将InetSocketAddress解析为格式为ip:port的字符串
    private String getServiceAddress(InetSocketAddress serverAddress) {
        return serverAddress.getHostName() + ":" + serverAddress.getPort();
    }

    // 字符串解析为地址
    private InetSocketAddress parseAddress(String address) {
        String[] result = address.split(":");
        return new InetSocketAddress(result[0], Integer.parseInt(result[1]));
    }
}
