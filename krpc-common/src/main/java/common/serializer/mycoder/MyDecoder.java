package common.serializer.mycoder;


import common.exception.SerializeException;
import common.message.MessageType;
import common.serializer.myserializer.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;


@Slf4j
public class MyDecoder extends ByteToMessageDecoder {

    // 消息头长度: messageType(2) + serializerType(2) + length(4) = 8 bytes
    private static final int HEADER_LENGTH = 8;

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf in, List<Object> out) throws Exception {
        // 检查可读字节数是否满足消息头长度
        if (in.readableBytes() < HEADER_LENGTH) {
            return;
        }

        // 标记当前读取位置，以便数据不完整时回退
        in.markReaderIndex();

        // 1.读取消息类型
        short messageType = in.readShort();
        // 现在还只支持request与response请求
        if (messageType != MessageType.REQUEST.getCode() &&
                messageType != MessageType.RESPONSE.getCode()) {
            log.warn("暂不支持此种数据, messageType: {}", messageType);
            // 不支持的消息类型，重置位置并跳过
            in.resetReaderIndex();
            in.skipBytes(in.readableBytes());
            return;
        }

        // 2.读取序列化的方式&类型
        short serializerType = in.readShort();
        Serializer serializer = Serializer.getSerializerByCode(serializerType);
        if (serializer == null) {
            log.error("不存在对应的序列化器, serializerType: {}", serializerType);
            throw new SerializeException("不存在对应的序列化器, serializerType: " + serializerType);
        }

        // 3.读取序列化数组长度
        int length = in.readInt();
        if (in.readableBytes() < length) {
            // 数据不完整，重置读取位置，等待更多数据
            in.resetReaderIndex();
            return;
        }

        // 4.读取序列化数组
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        log.debug("Received bytes: {}", Arrays.toString(bytes));
        Object deserialize = serializer.deserialize(bytes, messageType);

        out.add(deserialize);
    }
}
