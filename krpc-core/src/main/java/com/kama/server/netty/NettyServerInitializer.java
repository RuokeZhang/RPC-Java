package com.kama.server.netty;


import common.serializer.mycoder.MyDecoder;
import common.serializer.mycoder.MyEncoder;
import common.serializer.myserializer.Serializer;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.AllArgsConstructor;
import com.kama.server.provider.ServiceProvider;

import java.util.concurrent.TimeUnit;


@AllArgsConstructor
public class NettyServerInitializer extends ChannelInitializer<SocketChannel> {
    private ServiceProvider serviceProvider;

    // 服务端读空闲超时时间（秒），超过此时间没有收到客户端消息则关闭连接
    private static final int READER_IDLE_TIME = 30;

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        // 空闲检测处理器：30秒内没有读到数据则触发读空闲事件
        pipeline.addLast(new IdleStateHandler(READER_IDLE_TIME, 0, 0, TimeUnit.SECONDS));
        // 使用自定义的编/解码器
        pipeline.addLast(new MyEncoder(Serializer.getSerializerByCode(3)));
        pipeline.addLast(new MyDecoder());
        pipeline.addLast(new NettyRpcServerHandler(serviceProvider));
    }
}
