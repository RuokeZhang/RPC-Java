package common.serializer.myserializer;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import com.kama.proto.RpcRequestProto;
import com.kama.proto.RpcResponseProto;
import com.kama.proto.UserProto;
import common.exception.SerializeException;
import common.message.ProtobufMessageCode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Demo 级 Protobuf 序列化器：
 * - 仅支持 Protobuf 生成的 MessageLite 对象
 * - 通过 messageType 映射到具体的 Parser 进行反序列化
 * - 自带一个可选的预注册示例（如果存在 com.kama.proto.UserOuterClass$User）
 */
public class ProtobufSerializer implements Serializer {

    private static final Map<Integer, Parser<? extends MessageLite>> PARSER_REGISTRY = new ConcurrentHashMap<>();

    static {
        registerParser(ProtobufMessageCode.USER, UserProto.User.parser());
        registerParser(ProtobufMessageCode.RPC_REQUEST, RpcRequestProto.RpcRequest.parser());
        registerParser(ProtobufMessageCode.RPC_RESPONSE, RpcResponseProto.RpcResponse.parser());
    }

    /**
     * 允许外部在启动时注册更多的 Protobuf 解析器
     */
    public static void registerParser(int messageType, Parser<? extends MessageLite> parser) {
        PARSER_REGISTRY.put(messageType, parser);
    }

    @Override
    public byte[] serialize(Object obj) {
        if (!(obj instanceof MessageLite)) {
            throw new SerializeException("ProtobufSerializer 仅支持 Protobuf MessageLite 对象");
        }
        return ((MessageLite) obj).toByteArray();
    }

    @Override
    public Object deserialize(byte[] bytes, int messageType) {
        Parser<? extends MessageLite> parser = PARSER_REGISTRY.get(messageType);
        if (parser == null) {
            throw new SerializeException("未找到对应 messageType 的 Protobuf Parser: " + messageType);
        }
        try {
            return parser.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new SerializeException("Protobuf 反序列化失败: " + e.getMessage());
        }
    }

    @Override
    public int getType() {
        return 5; // 为 Protobuf 分配新的类型编号
    }

    @Override
    public String toString() {
        return "Protobuf";
    }
}

