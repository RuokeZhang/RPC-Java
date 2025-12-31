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
public class RpcRequest implements Serializable {
    //接口名、方法名、参数列表参数类型
    private String requestId;
    private String interfaceName;

    private String methodName;

    private Object[] params;

    private Class<?>[] paramsType;

    // 心跳检测标识
    @Builder.Default
    private boolean heartBeat = false;
}
