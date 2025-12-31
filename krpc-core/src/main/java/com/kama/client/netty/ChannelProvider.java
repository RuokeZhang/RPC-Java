package com.kama.client.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * 连接池管理类，复用 Netty Channel
 */
@Slf4j
public class ChannelProvider {

    // 连接池：key 为 "host:port"，value 为对应的 Channel
    private static final Map<String, Channel> channelPool = new ConcurrentHashMap<>();

    /**
     * 获取或创建到指定地址的 Channel
     *
     * @param address   目标服务地址
     * @param bootstrap Netty Bootstrap
     * @return 可用的 Channel
     */
    public static Channel getChannel(InetSocketAddress address, Bootstrap bootstrap) {
        String key = address.getHostName() + ":" + address.getPort();

        // 检查是否已有可用连接
        Channel channel = channelPool.get(key);
        if (channel != null && channel.isActive()) {
            log.debug("复用已有连接: {}", key);
            return channel;
        }

        // 连接不可用，移除旧连接
        if (channel != null) {
            log.info("连接已失效，移除: {}", key);
            channelPool.remove(key);
        }

        // 创建新连接
        try {
            ChannelFuture future = bootstrap.connect(address).sync();
            channel = future.channel();
            channelPool.put(key, channel);
            log.info("创建新连接: {}", key);

            // 监听连接关闭事件，自动从池中移除
            channel.closeFuture().addListener(closeFuture -> {
                log.info("连接关闭，从连接池移除: {}", key);
                channelPool.remove(key);
            });

            return channel;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("创建连接被中断: {}", key, e);
            return null;
        } catch (Exception e) {
            log.error("创建连接失败: {}", key, e);
            return null;
        }
    }

    /**
     * 从连接池移除指定地址的连接
     */
    public static void removeChannel(InetSocketAddress address) {
        String key = address.getHostName() + ":" + address.getPort();
        Channel channel = channelPool.remove(key);
        if (channel != null && channel.isActive()) {
            channel.close();
        }
    }

    /**
     * 关闭所有连接
     */
    public static void closeAll() {
        for (Map.Entry<String, Channel> entry : channelPool.entrySet()) {
            Channel channel = entry.getValue();
            if (channel != null && channel.isActive()) {
                channel.close();
            }
        }
        channelPool.clear();
        log.info("已关闭所有连接");
    }
}
