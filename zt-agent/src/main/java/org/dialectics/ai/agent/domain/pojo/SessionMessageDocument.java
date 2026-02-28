package org.dialectics.ai.agent.domain.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 会话记忆文档
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document("chat_session")
public class SessionMessageDocument implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private ObjectId id;
    @Indexed
    private String conversationId;
    /// 所有对话记忆
    private List<String> messages;

}
