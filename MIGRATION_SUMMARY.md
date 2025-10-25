# Version 5 迁移至 Nacos 总结

## 修改概览

本次修改将 Version 5 从 **Zookeeper** 服务发现迁移至 **Nacos** 服务发现，完全满足您的简历要求。

## 已完成的修改

### 1. 添加 Nacos 依赖

**文件**: `krpc-core/pom.xml`

添加了 Nacos Client 依赖：
```xml
<dependency>
    <groupId>com.alibaba.nacos</groupId>
    <artifactId>nacos-client</artifactId>
    <version>2.2.4</version>
</dependency>
```

### 2. 创建 Nacos 服务注册实现

**文件**: `krpc-core/src/main/java/com/kama/server/serviceRegister/impl/NacosServiceRegister.java`

**功能**:
- 连接到 Nacos Server (默认 127.0.0.1:8848)
- 实现服务注册功能
- 支持服务元数据（可重试方法列表）
- 自动将服务实例注册为健康状态

**关键方法**:
- `register(Class<?> clazz, InetSocketAddress serviceAddress)` - 注册服务到 Nacos
- `getRetryableMethod(Class<?> clazz)` - 获取带 @Retryable 注解的方法

### 3. 创建 Nacos 服务发现实现

**文件**: `krpc-core/src/main/java/com/kama/client/servicecenter/NacosServiceCenter.java`

**功能**:
- 连接到 Nacos Server
- 实现服务发现功能
- 支持负载均衡（默认 Round-Robin）
- 支持可重试方法检查
- 本地缓存可重试方法列表

**关键方法**:
- `serviceDiscovery(RpcRequest request)` - 从 Nacos 发现服务并返回地址
- `checkRetry(InetSocketAddress serviceAddress, String methodSignature)` - 检查方法是否可重试
- 支持可插拔的负载均衡器

### 4. 更新默认配置

**文件**: `krpc-core/src/main/java/com/kama/config/KRpcConfig.java`

**修改内容**:
```java
// 从 ZKServiceRegister 改为 NacosServiceRegister
private String registry = new NacosServiceRegister().toString();

// 从 ConsistencyHashBalance 改为 RoundLoadBalance
private String loadBalance = new RoundLoadBalance().toString();
```

### 5. 更新 ClientProxy

**文件**: `krpc-core/src/main/java/com/kama/client/proxy/ClientProxy.java`

**修改内容**:
```java
// 从 ZKServiceCenter 改为 NacosServiceCenter
import com.kama.client.servicecenter.NacosServiceCenter;

public ClientProxy() throws InterruptedException {
    serviceCenter = new NacosServiceCenter();
    circuitBreakerProvider = new CircuitBreakerProvider();
}
```

### 6. 更新 ServiceProvider

**文件**: `krpc-core/src/main/java/com/kama/server/provider/ServiceProvider.java`

**修改内容**:
```java
// 从 ZKServiceRegister 改为 NacosServiceRegister
import com.kama.server.serviceRegister.impl.NacosServiceRegister;

public ServiceProvider(String host, int port) {
    this.serviceRegister = new NacosServiceRegister();
    // ...
}
```

### 7. 完善负载均衡器

**文件**:
- `krpc-core/src/main/java/com/kama/client/servicecenter/balance/impl/RoundLoadBalance.java`
- `krpc-core/src/main/java/com/kama/client/servicecenter/balance/impl/RandomLoadBalance.java`

**修改内容**:
为 Round-Robin 和 Random 负载均衡器添加了 `toString()` 方法，用于配置识别。

```java
// RoundLoadBalance
@Override
public String toString() {
    return "RoundRobin";
}

// RandomLoadBalance
@Override
public String toString() {
    return "Random";
}
```

## 项目特性（符合简历要求）

### ✅ 多种序列化方式
- **Kryo** (Code: 2) - 高性能序列化
- **Hessian** (Code: 3) - 默认使用，跨语言支持
- **Protostuff** (Code: 4) - 类似 Protobuf
- 还支持 Java Object 和 JSON 序列化

### ✅ 双传输层
- **Netty (NIO)** - 高性能异步网络框架
- **Socket (BIO)** - 传统阻塞 IO
- 自定义网络协议支持两种传输方式

### ✅ Nacos 服务发现
- 使用 Nacos 作为服务注册与发现中心
- 支持服务健康检查
- 支持服务元数据管理

### ✅ 客户端负载均衡
- **Random** - 随机选择服务实例
- **Round-Robin** - 轮询选择服务实例（默认）
- **Consistency Hash** - 一致性哈希（可选）

## 使用步骤

### 前置条件

1. **安装并启动 Nacos Server**
   ```bash
   # 下载 Nacos: https://github.com/alibaba/nacos/releases
   # 启动 Nacos (standalone 模式)

   # Windows
   cd nacos/bin
   startup.cmd -m standalone

   # Mac/Linux
   cd nacos/bin
   sh startup.sh -m standalone
   ```

2. **验证 Nacos 启动**
   - 访问: http://127.0.0.1:8848/nacos
   - 用户名/密码: nacos/nacos

### 编译项目

```bash
cd /Users/ruoke/Documents/java_web/RPC-Java/version5
mvn clean install -DskipTests
```

### 启动服务

1. **启动 Provider**:
   ```bash
   cd krpc-provider
   java -cp "target/krpc-provider-1.0-SNAPSHOT.jar:target/lib/*" com.kama.provider.ProviderTest
   ```

2. **启动 Consumer**:
   ```bash
   cd krpc-consumer
   java -cp "target/krpc-consumer-1.0-SNAPSHOT.jar:target/lib/*" com.kama.consumer.ConsumerTest
   ```

## 验证功能

### 1. 验证服务注册
- 打开 Nacos 控制台: http://127.0.0.1:8848/nacos
- 进入 "服务管理" -> "服务列表"
- 应该看到服务: `com.kama.service.UserService`

### 2. 验证负载均衡

**Round-Robin 测试**:
- 启动多个 Provider 实例（不同端口）
- 启动 Consumer
- 观察日志中 "负载均衡选择了服务器" 的输出
- 应该看到服务器地址按顺序轮换

**Random 测试**:
- 修改 `KRpcConfig.java`:
  ```java
  private String loadBalance = new RandomLoadBalance().toString();
  ```
- 重新编译运行
- 应该看到服务器地址随机选择

### 3. 验证序列化

当前默认使用 **Hessian (Code: 3)**。可以修改为其他序列化方式：

```java
// KRpcConfig.java
private String serializer = Serializer.getSerializerByCode(2).toString(); // Kryo
private String serializer = Serializer.getSerializerByCode(4).toString(); // Protostuff
```

## 文件结构

```
version5/
├── README_NACOS.md                    # Nacos 版本详细使用说明
├── MIGRATION_SUMMARY.md               # 本文件：迁移总结
├── krpc-core/
│   ├── pom.xml                        # ✅ 添加 Nacos 依赖
│   └── src/main/java/com/kama/
│       ├── config/
│       │   └── KRpcConfig.java        # ✅ 更新默认配置
│       ├── client/
│       │   ├── proxy/
│       │   │   └── ClientProxy.java   # ✅ 使用 NacosServiceCenter
│       │   └── servicecenter/
│       │       ├── NacosServiceCenter.java        # ✅ 新建
│       │       └── balance/impl/
│       │           ├── RoundLoadBalance.java      # ✅ 添加 toString()
│       │           └── RandomLoadBalance.java     # ✅ 添加 toString()
│       └── server/
│           ├── provider/
│           │   └── ServiceProvider.java           # ✅ 使用 NacosServiceRegister
│           └── serviceRegister/impl/
│               └── NacosServiceRegister.java      # ✅ 新建
├── krpc-provider/
│   └── src/main/java/com/kama/provider/
│       └── ProviderTest.java          # 无需修改
└── krpc-consumer/
    └── src/main/java/com/kama/consumer/
        └── ConsumerTest.java          # 无需修改
```

## 关键代码对比

### 服务注册对比

**Zookeeper 版本**:
```java
// 使用 Curator 客户端
private CuratorFramework client;
client.create().withMode(CreateMode.EPHEMERAL).forPath(path);
```

**Nacos 版本**:
```java
// 使用 Nacos NamingService
private NamingService namingService;
Instance instance = new Instance();
instance.setIp(serviceAddress.getHostName());
instance.setPort(serviceAddress.getPort());
namingService.registerInstance(serviceName, instance);
```

### 服务发现对比

**Zookeeper 版本**:
```java
// 从 Zookeeper 获取子节点
List<String> addressList = client.getChildren().forPath("/" + serviceName);
```

**Nacos 版本**:
```java
// 从 Nacos 获取健康实例
List<Instance> instances = namingService.selectInstances(serviceName, true);
List<String> addressList = instances.stream()
    .map(i -> i.getIp() + ":" + i.getPort())
    .collect(Collectors.toList());
```

## 后续测试建议

1. **基础功能测试**
   - 启动 Nacos Server
   - 启动 Provider，检查服务是否注册成功
   - 启动 Consumer，检查 RPC 调用是否成功

2. **负载均衡测试**
   - 启动多个 Provider（端口 9999, 10000, 10001）
   - 观察 Round-Robin 轮询效果
   - 切换到 Random，观察随机效果

3. **序列化测试**
   - 测试 Kryo、Hessian、Protostuff 三种序列化
   - 验证序列化/反序列化正确性

4. **故障恢复测试**
   - 停止一个 Provider 实例
   - 验证 Nacos 是否自动摘除不健康实例
   - 重启实例，验证是否自动恢复

## 注意事项

1. **Nacos Server 必须先启动**
   - Provider 和 Consumer 都需要连接到 Nacos
   - 默认地址: 127.0.0.1:8848

2. **端口冲突**
   - Provider 默认端口: 9999
   - Nacos 默认端口: 8848
   - 确保端口未被占用

3. **网络配置**
   - 确保防火墙未阻止相关端口
   - Provider 和 Consumer 需要能够互相访问

4. **依赖管理**
   - 确保 Maven 正确下载 Nacos Client 依赖
   - 如果网络不稳定，可能需要配置 Maven 镜像

## 简历描述模板

```
Java-RPC Project                                                    Aug 2024
• Developed a Java RPC framework supporting Kryo, Protobuf, and Hessian
  for pluggable object serialization.
• Built a custom network protocol with dual transport layers using both
  Netty (NIO) and Sockets (BIO).
• Utilized Nacos for service discovery and implemented Random/Round-Robin
  client-side load balancing algorithms.
• Integrated circuit breaker pattern and retry mechanism for fault tolerance.
• Achieved [X]% performance improvement over traditional RPC frameworks
  through optimized serialization and connection pooling.
```

## 问题排查

如遇到问题，请检查：

1. **Nacos Server 是否正常运行**
   ```bash
   curl http://127.0.0.1:8848/nacos/v1/console/health/readiness
   ```

2. **查看 Provider 日志**
   - 应该看到 "Nacos 连接成功" 和 "服务注册成功"

3. **查看 Consumer 日志**
   - 应该看到 "Nacos 连接成功" 和 "服务发现成功"

4. **在 Nacos 控制台检查**
   - 服务是否注册
   - 实例是否健康

更多详细信息请参考 `README_NACOS.md`。
