# Version 5 - Nacos 版本使用说明

## 项目特性

本版本实现了以下功能（符合你的简历要求）：

✅ **多种序列化方式**：支持 Kryo、Protobuf (Protostuff)、Hessian 的可插拔对象序列化
✅ **双传输层**：使用 Netty (NIO) 和 Socket (BIO) 的自定义网络协议
✅ **Nacos 服务发现**：使用 Nacos 作为服务注册与发现中心
✅ **客户端负载均衡**：实现了 Random（随机）和 Round-Robin（轮询）负载均衡算法

## 前置要求

### 1. JDK 17
```bash
java -version
```
确保输出显示 JDK 17 或更高版本。

### 2. Maven 3.6+
```bash
mvn -v
```

### 3. Nacos Server

#### 下载和安装 Nacos
1. 访问 [Nacos GitHub Releases](https://github.com/alibaba/nacos/releases)
2. 下载最新的 Nacos Server 版本（推荐 2.2.4 或更高）
3. 解压到本地目录

#### 启动 Nacos Server

**Windows 环境：**
```bash
cd <nacos-解压目录>/bin
startup.cmd -m standalone
```

**Mac/Linux 环境：**
```bash
cd <nacos-解压目录>/bin
sh startup.sh -m standalone
```

#### 验证 Nacos 启动成功
打开浏览器访问：http://127.0.0.1:8848/nacos

默认用户名和密码都是：`nacos`

## 项目编译

### 1. 清理并编译项目

在 version5 目录下执行：

**Windows 环境：**
```bash
cd D:\java_study\RPC-Java\version5
mvn clean install -DskipTests
```

**Mac/Linux 环境：**
```bash
cd /Users/ruoke/Documents/java_web/RPC-Java/version5
mvn clean install -DskipTests
```

### 2. 检查编译结果

确保以下目录中存在对应的 jar 包：
- `krpc-api/target/krpc-api-1.0-SNAPSHOT.jar`
- `krpc-common/target/krpc-common-1.0-SNAPSHOT.jar`
- `krpc-core/target/krpc-core-1.0-SNAPSHOT.jar`
- `krpc-provider/target/krpc-provider-1.0-SNAPSHOT.jar`
- `krpc-consumer/target/krpc-consumer-1.0-SNAPSHOT.jar`

## 启动服务

### 1. 启动服务提供者（Provider）

**Windows 环境：**
```bash
cd D:\java_study\RPC-Java\version5\krpc-provider
D:\software\JDK17\bin\java -cp "target\krpc-provider-1.0-SNAPSHOT.jar;target\lib\*" com.kama.provider.ProviderTest
```

**Mac/Linux 环境：**
```bash
cd /Users/ruoke/Documents/java_web/RPC-Java/version5/krpc-provider
java -cp "target/krpc-provider-1.0-SNAPSHOT.jar:target/lib/*" com.kama.provider.ProviderTest
```

启动成功后，你会看到类似的日志：
```
Nacos 连接成功，服务器地址: 127.0.0.1:8848
服务注册成功 - 服务名: com.kama.service.UserService, 地址: localhost:9999
RPC 服务端启动，监听端口9999
```

### 2. 启动服务消费者（Consumer）

**Windows 环境：**
```bash
cd D:\java_study\RPC-Java\version5\krpc-consumer
D:\software\JDK17\bin\java -cp "target\krpc-consumer-1.0-SNAPSHOT.jar;target\lib\*" com.kama.consumer.ConsumerTest
```

**Mac/Linux 环境：**
```bash
cd /Users/ruoke/Documents/java_web/RPC-Java/version5/krpc-consumer
java -cp "target/krpc-consumer-1.0-SNAPSHOT.jar:target/lib/*" com.kama.consumer.ConsumerTest
```

启动成功后，你会看到 RPC 调用的日志：
```
Nacos 连接成功，服务器地址: 127.0.0.1:8848
服务发现成功 - 服务名: com.kama.service.UserService, 选择地址: localhost:9999
负载均衡选择了服务器: localhost:9999
从服务端得到的user=User(id=0, userName=User0, gender=true)
```

## 核心配置说明

### 默认配置（KRpcConfig.java）

```java
// 注册中心：Nacos
private String registry = new NacosServiceRegister().toString();

// 序列化器：Hessian (Code: 3)
private String serializer = Serializer.getSerializerByCode(3).toString();

// 负载均衡：Round-Robin (轮询)
private String loadBalance = new RoundLoadBalance().toString();
```

### 可用的序列化器

| 编码 | 序列化方式 | 特点 |
|-----|----------|------|
| 0 | Java Object | Java 原生序列化 |
| 1 | JSON | FastJSON，跨语言 |
| 2 | Kryo | 高性能，小体积 |
| 3 | Hessian | 跨语言，稳定 |
| 4 | Protostuff | 类似 Protobuf |

### 可用的负载均衡算法

| 算法 | 类名 | 说明 |
|-----|------|------|
| Random | RandomLoadBalance | 随机选择服务实例 |
| Round-Robin | RoundLoadBalance | 轮询选择服务实例 |
| Consistency Hash | ConsistencyHashBalance | 一致性哈希 |

### 修改配置

如果要使用不同的序列化方式或负载均衡算法，可以修改 `KRpcConfig.java`：

```java
// 使用 Kryo 序列化
private String serializer = Serializer.getSerializerByCode(2).toString();

// 使用随机负载均衡
private String loadBalance = new RandomLoadBalance().toString();
```

## 在 Nacos 控制台查看服务

1. 访问 http://127.0.0.1:8848/nacos
2. 登录（用户名/密码：nacos/nacos）
3. 点击左侧菜单 "服务管理" -> "服务列表"
4. 你应该能看到注册的服务：`com.kama.service.UserService`
5. 点击服务名称可以查看实例详情，包括 IP、端口、健康状态等

## 测试多实例负载均衡

要测试负载均衡功能，你可以启动多个 Provider 实例：

### 1. 修改 Provider 端口

复制 `ProviderTest.java` 或通过参数修改端口号，启动多个 Provider（例如 9999, 10000, 10001）

### 2. 观察负载均衡效果

启动 Consumer 后，观察日志中 "负载均衡选择了服务器" 的输出，验证：
- **Round-Robin**: 服务器地址按顺序轮换
- **Random**: 服务器地址随机选择

## 常见问题

### 1. Nacos 连接失败
**错误信息**: `Failed to connect to Nacos`

**解决方案**:
- 确认 Nacos Server 已启动（访问 http://127.0.0.1:8848/nacos）
- 检查防火墙是否阻止了 8848 端口
- 确认 NacosServiceCenter 和 NacosServiceRegister 中的地址配置正确

### 2. 服务未注册到 Nacos
**解决方案**:
- 检查 Provider 日志，确认 "服务注册成功" 消息
- 在 Nacos 控制台检查服务列表
- 确认 Nacos Server 是否正常运行

### 3. Consumer 找不到服务
**错误信息**: `未找到服务: com.kama.service.UserService`

**解决方案**:
- 确认 Provider 已启动并成功注册
- 在 Nacos 控制台检查服务是否存在
- 检查 Consumer 和 Provider 的服务接口名称是否一致

### 4. 序列化错误
**解决方案**:
- 确保 Provider 和 Consumer 使用相同的序列化方式
- 检查 Maven 依赖是否正确导入（Kryo, Hessian, Protostuff）

## 技术架构

### 服务注册流程
1. Provider 启动时创建 `NacosServiceRegister`
2. 通过 Nacos Client API 将服务实例注册到 Nacos Server
3. 注册信息包括：服务名、IP、端口、元数据（可重试方法列表）

### 服务发现流程
1. Consumer 启动时创建 `NacosServiceCenter`
2. 根据服务名从 Nacos 获取健康的实例列表
3. 使用负载均衡算法选择一个实例
4. 建立连接并发起 RPC 调用

### 负载均衡流程
1. 从 Nacos 获取服务实例列表
2. LoadBalance 接口根据算法选择实例：
   - **RandomLoadBalance**: `random.nextInt(addressList.size())`
   - **RoundLoadBalance**: `AtomicInteger.getAndUpdate(i -> (i + 1) % size)`
3. 返回选中的服务地址

## 项目结构

```
version5/
├── krpc-api/          # API 接口定义
├── krpc-common/       # 公共组件（序列化、消息）
├── krpc-core/         # 核心框架
│   ├── client/        # 客户端（服务发现、负载均衡）
│   │   └── servicecenter/
│   │       ├── NacosServiceCenter.java    # Nacos 服务发现
│   │       └── balance/
│   │           ├── RandomLoadBalance.java  # 随机负载均衡
│   │           └── RoundLoadBalance.java   # 轮询负载均衡
│   └── server/        # 服务端（服务注册）
│       └── serviceRegister/
│           └── NacosServiceRegister.java  # Nacos 服务注册
├── krpc-provider/     # 服务提供者
└── krpc-consumer/     # 服务消费者
```

## 简历描述

**Java-RPC Aug 2024**
- Developed a Java RPC framework supporting Kryo, Protobuf, and Hessian for pluggable object serialization.
- Built a custom network protocol with dual transport layers using both Netty (NIO) and Sockets (BIO).
- Utilized Nacos for service discovery and implemented Random/Round-Robin client-side load balancing algorithms.

## 后续优化方向

- [ ] 支持配置文件动态修改序列化方式和负载均衡算法
- [ ] 添加更多负载均衡算法（加权轮询、最少连接数等）
- [ ] 集成 Nacos 配置中心功能
- [ ] 添加服务优雅下线功能
- [ ] 支持 Nacos 命名空间和分组
