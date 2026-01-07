package com.kama.client.servicecenter.balance.impl;

import com.kama.client.servicecenter.balance.LoadBalance;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 平滑加权轮询 (类似 nginx)。
 * 说明：
 * 1) 默认权重为 1，可通过 setWeight 动态调整；
 * 2) 只对当前可用 addressList 内的节点参与调度，自动剔除失效节点；
 * 3) currentWeight 变化需串行，使用 synchronized 保护，保持实现简洁且足够快（节点数通常很小）。
 */
@Slf4j
public class WeightedRoundLoadBalance implements LoadBalance {

    private static final int DEFAULT_WEIGHT = 1;

    private final Map<String, WeightedNode> nodeState = new ConcurrentHashMap<>();

    @Override
    public String balance(List<String> addressList) {
        if (addressList == null || addressList.isEmpty()) {
            throw new IllegalArgumentException("Address list cannot be null or empty");
        }

        synchronized (this) {
            // 清理失效节点，避免缓存膨胀
            cleanupStaleNodes(addressList);

            int totalWeight = 0;
            WeightedNode selected = null;

            for (String address : addressList) {
                WeightedNode node = nodeState.computeIfAbsent(address,
                        key -> new WeightedNode(DEFAULT_WEIGHT));

                // 保护性处理：权重不能为非正
                if (node.weight <= 0) {
                    node.weight = DEFAULT_WEIGHT;
                }

                node.currentWeight += node.weight;
                totalWeight += node.weight;

                if (selected == null || node.currentWeight > selected.currentWeight) {
                    selected = node;
                    selected.address = address;
                }
            }

            if (selected == null) {
                throw new IllegalStateException("No available server for weighted round robin");
            }

            selected.currentWeight -= totalWeight;
            log.info("平滑加权轮询选择了服务器: {} (权重: {})", selected.address, selected.weight);
            return selected.address;
        }
    }

    @Override
    public void addNode(String node) {
        nodeState.putIfAbsent(node, new WeightedNode(DEFAULT_WEIGHT));
        log.info("节点 {} 已加入加权轮询，默认权重 {}", node, DEFAULT_WEIGHT);
    }

    @Override
    public void delNode(String node) {
        nodeState.remove(node);
        log.info("节点 {} 已从加权轮询移除", node);
    }

    /**
     * 设置节点权重，需大于 0。
     */
    public void setWeight(String node, int weight) {
        if (weight <= 0) {
            throw new IllegalArgumentException("Weight must be positive");
        }
        nodeState.compute(node, (k, v) -> {
            WeightedNode newNode = v == null ? new WeightedNode(weight) : v;
            newNode.weight = weight;
            return newNode;
        });
        log.info("节点 {} 权重设置为 {}", node, weight);
    }

    /**
     * 批量设置权重，便于一次性配置。
     */
    public void setWeights(Map<String, Integer> weights) {
        if (weights == null || weights.isEmpty()) {
            return;
        }
        weights.forEach((node, weight) -> {
            int safeWeight = weight == null ? DEFAULT_WEIGHT : weight;
            setWeight(node, safeWeight);
        });
    }

    private void cleanupStaleNodes(List<String> addresses) {
        Set<String> active = Set.copyOf(addresses);
        nodeState.keySet().removeIf(addr -> !active.contains(addr));
    }

    private static class WeightedNode {
        String address;
        int weight;
        int currentWeight;

        WeightedNode(int weight) {
            this.weight = weight;
            this.currentWeight = 0;
        }
    }

    @Override
    public String toString() {
        return "WeightedRoundRobin";
    }
}

