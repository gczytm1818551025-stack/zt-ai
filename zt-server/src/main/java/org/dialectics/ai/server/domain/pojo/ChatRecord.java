package org.dialectics.ai.server.domain.pojo;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dialectics.ai.common.constants.SessionConstant;
import org.dialectics.ai.common.domain.BasePojo;
import org.dialectics.ai.common.enums.SessionTypeEnum;

import java.io.Serial;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_record")
public class ChatRecord extends BasePojo {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;
    private String sessionId;
    private Long userId;
    private String title;
    private SessionTypeEnum sessionType;

    public static String defaultTitle(SessionTypeEnum sessionType) {
        return switch (sessionType) {
            case AGENT -> SessionConstant.DEFAULT_TASK;
            case CHAT -> SessionConstant.DEFALUT_TITLE;
            default -> throw new IllegalArgumentException("Invalid session type: " + sessionType);
        };
    }
}
