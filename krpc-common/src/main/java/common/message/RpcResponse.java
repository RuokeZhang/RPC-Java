package common.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;


@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class RpcResponse implements Serializable {
    //状态信息
    private String requestId;
    private int code;
    private String message;
    //更新：加入传输数据的类型，以便在自定义序列化器中解析
    private Class<?> dataType;
    //具体数据
    private Object data;

    public static RpcResponse sussess(Object data, String requestId) {
        Class<?> type = data == null ? Object.class : data.getClass();
        return RpcResponse.builder()
                .requestId(requestId)
                .code(200)
                .dataType(type)
                .data(data)
                .build();
    }

    public static RpcResponse sussess(Object data) {
        return sussess(data, null);
    }

    public static RpcResponse fail(String msg, String requestId) {
        return RpcResponse.builder().requestId(requestId).code(500).message(msg).build();
    }

    public static RpcResponse fail(String msg) {
        return fail(msg, null);
    }
}
