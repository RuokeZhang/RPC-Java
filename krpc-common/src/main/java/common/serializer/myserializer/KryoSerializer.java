package common.serializer.myserializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Kryo.DefaultInstantiatorStrategy;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.kama.pojo.User;
import common.exception.SerializeException;
import org.objenesis.strategy.StdInstantiatorStrategy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;



public class KryoSerializer implements Serializer {
    /**
     * Kryo 不是线程安全的，这里用 ThreadLocal 隔离实例
     */
    private static final ThreadLocal<Kryo> KRYO_THREAD_LOCAL = ThreadLocal.withInitial(KryoSerializer::buildKryo);

    @Override
    public byte[] serialize(Object obj) {
        if (obj == null) {
            throw new IllegalArgumentException("Cannot serialize null object");
        }

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             Output output = new Output(byteArrayOutputStream)) {

            Kryo kryo = KRYO_THREAD_LOCAL.get();
            kryo.writeClassAndObject(output, obj); // 写入类 ID + 对象，使用注册表避免反射
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

            // 依赖已注册的类 ID -> Class 映射，无需反射；messageType 可忽略
            return KRYO_THREAD_LOCAL.get().readClassAndObject(input);

        } catch (Exception e) {
            throw new SerializeException("Deserialization failed");
        }
    }

    @Override
    public int getType() {
        return 2;
    }

    private static Kryo buildKryo() {
        Kryo kryo = new Kryo();
        // 强制要求注册，序列化仅写入类 ID，体积更小
        kryo.setRegistrationRequired(true);
        // 使用 Objenesis 跳过构造方法，避免副作用；DefaultInstantiatorStrategy 内置 ASM 生成专用序列化器
        kryo.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
        // 预注册类并分配稳定的 ID，启用 ASM 生成专用序列化器
        kryo.register(User.class, 1);
        return kryo;
    }

    @Override
    public String toString() {
        return "Kryo";
    }
}