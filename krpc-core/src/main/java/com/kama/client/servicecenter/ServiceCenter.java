package com.kama.client.servicecenter;


import common.message.RpcRequest;

import java.net.InetSocketAddress;



public interface ServiceCenter {
    //  查询：根据服务名查找地址
    InetSocketAddress serviceDiscovery(RpcRequest request);

    //判断是否可重试
    boolean checkRetry(InetSocketAddress serviceAddress, String methodSignature);

    //关闭客户端
    void close();
}
