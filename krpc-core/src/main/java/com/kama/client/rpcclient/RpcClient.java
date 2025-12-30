package com.kama.client.rpcclient;


import common.message.RpcRequest;
import common.message.RpcResponse;


public interface RpcClient {
    java.util.concurrent.CompletableFuture<RpcResponse> sendRequest(RpcRequest request);
    void close();
}
