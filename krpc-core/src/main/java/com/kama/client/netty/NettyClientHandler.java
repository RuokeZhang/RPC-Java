package com.kama.client.netty;

import common.message.RpcRequest;
import common.message.RpcResponse;
import com.kama.client.rpcclient.impl.NettyRpcClient;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;


@Slf4j
public class NettyClientHandler extends SimpleChannelInboundHandler<RpcResponse> {


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcResponse response) throws Exception {
        // 忽略心跳响应
        if (response.getCode() == 200 && "HEARTBEAT".equals(response.getMessage())) {
            log.debug("收到心跳响应");
            return;
        }
        // 收到响应后，根据 requestId 回填对应的 future
        NettyRpcClient.completeResponse(response);
        // 长连接模式：不关闭通道，复用连接
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                // 读空闲超时，发送心跳请求
                log.debug("发送心跳请求到: {}", ctx.channel().remoteAddress());
                RpcRequest heartbeatRequest = RpcRequest.builder()
                        .requestId(UUID.randomUUID().toString())
                        .heartBeat(true)
                        .build();
                ctx.writeAndFlush(heartbeatRequest);
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Channel exception occurred", cause);
        ctx.close();
    }
}
