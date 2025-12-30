package common.serializer.myserializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import com.kama.pojo.User;
import common.exception.SerializeException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;




public class KryoSerializer implements Serializer {
    /**
     * Kryo 不是线程安全的，使用 ThreadLocal 隔离实例。
     */
    private static final ThreadLocal<Kryo> KRYO_THREAD_LOCAL = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        // 允许未注册类序列化，增强通用性（如需极致性能可改为 true 并手动注册）
        kryo.setRegistrationRequired(false);
        return kryo;
    });

    @Override
    public byte[] serialize(Object obj) {
        if (obj == null) {
            throw new IllegalArgumentException("Cannot serialize null object");
        }

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             Output output = new Output(byteArrayOutputStream)) {

            Kryo kryo = KRYO_THREAD_LOCAL.get();
            kryo.writeObject(output, obj); // 使用 Kryo 写入对象
            return output.toBytes(); // 返回字节数组

        } catch (Exception e) {
            throw new SerializeException("Serialization failed");
        }
    }

    @Override
    public Object deserialize(byte[] bytes, int messageType) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Cannot deserialize null or empty byte array");
        }

        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
             Input input = new Input(byteArrayInputStream)) {

            // 根据 messageType 来反序列化不同的类
            Class<?> clazz = getClassForMessageType(messageType);
            Kryo kryo = KRYO_THREAD_LOCAL.get();
            return kryo.readObject(input, clazz); // 使用 Kryo 反序列化对象

        } catch (Exception e) {
            throw new SerializeException("Deserialization failed");
        }
    }

    @Override
    public int getType() {
        return 2;
    }

    private Class<?> getClassForMessageType(int messageType) {
        if (messageType == 1) {
            return User.class;  // 假设我们在此反序列化成 User 类
        } else {
            throw new SerializeException("Unknown message type: " + messageType);
        }
    }

    @Override
    public String toString() {
        return "Kryo";
    }
}