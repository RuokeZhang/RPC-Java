package com.kama.client.servicecenter.balance.impl;

import com.kama.client.servicecenter.balance.LoadBalance;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 最小连接数负载均衡。
 * 适用于长连接或请求耗时差异大的场景，用“当前在途连接数”近似反映负载。
 *
 * 注意：
 * 1) balance() 选出节点后会先自增连接计数；
 * 2) 调用完成后需显式调用 releaseConnection(address) 归还；
 * 3) 节点列表变更时自动清理失效节点。
 */
@Slf4j
public class LeastConnectionLoadBalance implements LoadBalance {

    private final ConcurrentHashMap<String, AtomicInteger> connectionCounter = new ConcurrentHashMap<>();

    @Override
    public String balance(List<String> addressList) {
        if (addressList == null || addressList.isEmpty()) {
            throw new IllegalArgumentException("Address list cannot be null or empty");
        }

        synchronized (this) {
            cleanupStaleNodes(addressList);

            String selected = null;
            int minConnections = Integer.MAX_VALUE;

            for (String address : addressList) {
                AtomicInteger counter = connectionCounter.computeIfAbsent(address, k -> new AtomicInteger(0));
                int current = counter.get();
                if (selected == null || current < minConnections) {
                    selected = address;
                    minConnections = current;
                }
            }

            if (selected == null) {
                throw new IllegalStateException("No available server for least-connection strategy");
            }

            int afterInc = connectionCounter.get(selected).incrementAndGet();
            log.info("最小连接数选择了服务器: {} (当前连接数: {})", selected, afterInc);
            return selected;
        }
    }

    @Override
    public void addNode(String node) {
        connectionCounter.putIfAbsent(node, new AtomicInteger(0));
        log.info("节点 {} 已加入最小连接数负载均衡", node);
    }

    @Override
    public void delNode(String node) {
        connectionCounter.remove(node);
        log.info("节点 {} 已从最小连接数负载均衡移除", node);
    }

    /**
     * 调用结束后释放连接计数，避免计数泄露。
     */
    public void releaseConnection(String node) {
        AtomicInteger counter = connectionCounter.get(node);
        if (counter != null) {
            int val = counter.decrementAndGet();
            if (val < 0) {
                counter.set(0);
            }
            log.debug("节点 {} 连接计数减 1，当前 {}", node, Math.max(val, 0));
        }
    }

    /**
     * 获取当前连接数（便于监控或测试）。
     */
    public int getConnectionCount(String node) {
        AtomicInteger counter = connectionCounter.get(node);
        return counter == null ? 0 : counter.get();
    }

    private void cleanupStaleNodes(List<String> addresses) {
        Set<String> active = Set.copyOf(addresses);
        connectionCounter.keySet().removeIf(addr -> !active.contains(addr));
    }

    @Override
    public String toString() {
        return "LeastConnection";
    }
}

