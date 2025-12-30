package common.serializer.myserializer;

import common.message.ProtobufMessageCode;
import com.kama.proto.UserProto;
import common.exception.SerializeException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ProtobufSerializerTest {

    @Test
    void serializeAndDeserializeWithRegisteredParser() {
        Serializer serializer = Serializer.getSerializerByCode(5); // Protobuf
        UserProto.User user = UserProto.User.newBuilder()
                .setName("Alice")
                .setAge(25)
                .setEmail("alice@example.com")
                .build();

        byte[] bytes = serializer.serialize(user);
        Object result = serializer.deserialize(bytes, ProtobufMessageCode.USER);

        Assertions.assertTrue(result instanceof UserProto.User);
        Assertions.assertEquals(user, result);
    }

    @Test
    void unknownMessageTypeThrows() {
        Serializer serializer = Serializer.getSerializerByCode(5);
        UserProto.User user = UserProto.User.newBuilder()
                .setName("Bob")
                .setAge(18)
                .setEmail("bob@example.com")
                .build();

        byte[] bytes = serializer.serialize(user);
        Assertions.assertThrows(SerializeException.class, () -> serializer.deserialize(bytes, 999));
    }
}

