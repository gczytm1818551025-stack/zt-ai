package org.dialectics.ai.server.domain.pojo;

import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.NoArgsConstructor;
import org.dialectics.ai.common.domain.BasePojo;

import java.io.Serial;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user")
public class User extends BasePojo {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;
    private String nickName;
    private String phone;

}
