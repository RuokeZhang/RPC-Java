package com.kama.server.netty;


import common.message.RpcRequest;
import common.message.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.kama.server.provider.ServiceProvider;
import com.kama.server.ratelimit.RateLimit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


@AllArgsConstructor  // 使用 Lombok 自动生成构造器
@Slf4j
public class NettyRpcServerHandler extends SimpleChannelInboundHandler<RpcRequest> {

    private final ServiceProvider serviceProvider;  // 确保通过构造器注入 ServiceProvider

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcRequest request) throws Exception {
        if (request == null) {
            log.error("接收到非法请求，RpcRequest 为空");
            return;
        }

        // 处理心跳请求
        if (request.isHeartBeat()) {
            log.debug("收到心跳请求，来自: {}", ctx.channel().remoteAddress());
            RpcResponse heartbeatResponse = RpcResponse.builder()
                    .requestId(request.getRequestId())
                    .code(200)
                    .message("HEARTBEAT")
                    .build();
            ctx.writeAndFlush(heartbeatResponse);
            return;
        }

        // 处理正常业务请求
        RpcResponse response = getResponse(request);
        // 长连接模式：不关闭通道，复用连接
        ctx.writeAndFlush(response);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                // 读空闲超时，说明客户端可能已断开，关闭连接
                log.info("客户端读空闲超时，关闭连接: {}", ctx.channel().remoteAddress());
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("处理请求时发生异常: ", cause);
        ctx.close();
    }

    private RpcResponse getResponse(RpcRequest rpcRequest) {
        // 得到服务名
        String interfaceName = rpcRequest.getInterfaceName();

        // 接口限流降级
        RateLimit rateLimit = serviceProvider.getRateLimitProvider().getRateLimit(interfaceName);
        if (!rateLimit.getToken()) {
            // 如果获取令牌失败，进行限流降级，快速返回结果
            log.warn("服务限流，接口: {}", interfaceName);
            return RpcResponse.fail("服务限流，接口 " + interfaceName + " 当前无法处理请求。请稍后再试。", rpcRequest.getRequestId());
        }

        // 得到服务端相应服务实现类
        Object service = serviceProvider.getService(interfaceName);
        // 反射调用方法
        Method method;
        try {
            method = service.getClass().getMethod(rpcRequest.getMethodName(), rpcRequest.getParamsType());
            Object invoke = method.invoke(service, rpcRequest.getParams());
            return RpcResponse.sussess(invoke, rpcRequest.getRequestId());
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            log.error("方法执行错误，接口: {}, 方法: {}", interfaceName, rpcRequest.getMethodName(), e);
            return RpcResponse.fail("方法执行错误", rpcRequest.getRequestId());
        }
    }
}
