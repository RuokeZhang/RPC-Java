package com.kama.consumer;


import com.kama.config.KRpcConfig;
import common.util.ConfigUtil;


public class ConsumerTestConfig {
    public static void main(String[] args) {
        KRpcConfig rpc = ConfigUtil.loadConfig(KRpcConfig.class, "rpc");
        System.out.println(rpc);
    }

}
