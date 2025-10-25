package com.kama.config;

import com.kama.client.servicecenter.balance.impl.RoundLoadBalance;
import com.kama.server.serviceRegister.impl.NacosServiceRegister;
import common.serializer.myserializer.Serializer;
import lombok.*;


@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString
public class KRpcConfig {
    //名称
    private String name = "krpc";
    //端口
    private Integer port = 9999;
    //主机名
    private String host = "localhost";
    //版本号
    private String version = "1.0.0";
    //注册中心 (默认使用 Nacos)
    private String registry = new NacosServiceRegister().toString();
    //序列化器 (默认使用 Hessian)
    private String serializer = Serializer.getSerializerByCode(3).toString();
    //负载均衡 (默认使用 Round-Robin)
    private String loadBalance = new RoundLoadBalance().toString();

}
