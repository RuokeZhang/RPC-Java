package com.kama.client.netty;


import common.serializer.mycoder.MyDecoder;
import common.serializer.mycoder.MyEncoder;
import common.serializer.myserializer.Serializer;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;


@Slf4j
public class NettyClientInitializer extends ChannelInitializer<SocketChannel> {

    // 客户端读空闲超时时间（秒），超过此时间没有收到服务端消息则发送心跳
    private static final int READER_IDLE_TIME = 15;

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        // 使用自定义的编码器和解码器
        try {
            // 空闲检测处理器：15秒内没有读到数据则触发读空闲事件
            pipeline.addLast(new IdleStateHandler(READER_IDLE_TIME, 0, 0, TimeUnit.SECONDS));
            // 根据传入的序列化器类型初始化编码器
            pipeline.addLast(new MyEncoder(Serializer.getSerializerByCode(3)));
            pipeline.addLast(new MyDecoder());
            pipeline.addLast(new NettyClientHandler());

            log.info("Netty client pipeline initialized with serializer type: {} and idle timeout: {}s",
                    Serializer.getSerializerByCode(3).toString(), READER_IDLE_TIME);
        } catch (Exception e) {
            log.error("Error initializing Netty client pipeline", e);
            throw e;  // 重新抛出异常，确保管道初始化失败时处理正确
        }
    }
}
