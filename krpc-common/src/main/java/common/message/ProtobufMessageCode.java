package common.message;

/**
 * Prototype message type codes used by the ProtobufSerializer.
 */
public final class ProtobufMessageCode {
    private ProtobufMessageCode() {
    }

    public static final int USER = 1;
    public static final int RPC_REQUEST = 2;
    public static final int RPC_RESPONSE = 3;
}

