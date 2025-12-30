package com.kama.client.netty;

import common.message.RpcResponse;
import com.kama.client.rpcclient.impl.NettyRpcClient;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class NettyClientHandler extends SimpleChannelInboundHandler<RpcResponse> {


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcResponse response) throws Exception {
        // 收到响应后，根据 requestId 回填对应的 future
        NettyRpcClient.completeResponse(response);
        // 当前实现使用短连接，收到结果后关闭通道
        ctx.channel().close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Channel exception occurred", cause);
        ctx.close();
    }
}
