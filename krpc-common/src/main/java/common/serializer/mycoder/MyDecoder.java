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
        // LengthFieldBasedFrameDecoder 已确保此处拿到的是完整帧，这里只做协议解析
        if (in.readableBytes() < HEADER_LENGTH) {
            return;
        }

        short messageType = in.readShort();
        if (messageType != MessageType.REQUEST.getCode() &&
                messageType != MessageType.RESPONSE.getCode()) {
            log.warn("暂不支持此种数据, messageType: {}", messageType);
            in.skipBytes(in.readableBytes());
            return;
        }

        short serializerType = in.readShort();
        Serializer serializer = Serializer.getSerializerByCode(serializerType);
        if (serializer == null) {
            log.error("不存在对应的序列化器, serializerType: {}", serializerType);
            throw new SerializeException("不存在对应的序列化器, serializerType: " + serializerType);
        }

        int length = in.readInt();
        if (length < 0 || in.readableBytes() < length) {
            // 防御性判断，理论上不会发生
            return;
        }

        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        log.debug("Received bytes: {}", Arrays.toString(bytes));
        Object deserialize = serializer.deserialize(bytes, messageType);

        out.add(deserialize);
    }
}
