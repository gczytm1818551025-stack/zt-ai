package org.dialectics.ai.agent.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.dialectics.ai.agent.domain.pojo.AssistantMessage2;
import org.dialectics.ai.agent.domain.pojo.Message2;
import org.dialectics.ai.agent.manager.ToolResultManager;
import org.dialectics.ai.common.constants.ToolConstant;
import org.springframework.ai.chat.messages.*;

import java.util.Map;

/**
 * 大模型消息转换工具
 */
public class ChatMessageConverter {
    /**
     * 将Message对象序列化为JSON字符串
     *
     * @param message 需要转换的原始消息对象
     * @return 符合存储规范的JSON字符串
     */
    public static String toJson(Message message) {
        Message2 message2 = BeanUtil.toBean(message, Message2.class);

        // 设置消息内容
        message2.setTextContent(message.getText());

        if (message instanceof AssistantMessage assistantMessage) {
            message2.setToolCalls(assistantMessage.getToolCalls());

            // 获取并设置tool调用的结果到params
            String id = Convert.toStr(message.getMetadata().get("id"));
            if (StrUtil.isNotEmpty(id)) {
                // 获取<metaData.id>.id.<requestId>
                String requestId = Convert.toStr(ToolResultManager.get(id, ToolConstant.Context.REQUEST_ID));
                // 如果存在tool调用，requestId一定不为空
                if (StrUtil.isNotEmpty(requestId)) {
                    // 获取并设置tool调用的结果
                    Map<String, Object> params = ToolResultManager.get(requestId);
                    message2.setParams(params);

                    ToolResultManager.remove(requestId);
                }
            }
        }

        if (message instanceof ToolResponseMessage toolResponseMessage) {
            message2.setToolResponses(toolResponseMessage.getResponses());
        }

        return JSONUtil.toJsonStr(message2);
    }

    /**
     * 将JSON字符串反序列化为Message对象
     *
     * @param json Redis存储的JSON格式消息数据
     * @return 对应类型的Message对象
     * @throws RuntimeException 当无法识别的消息类型时抛出异常
     */
    public static Message toMessage(String json) {
        Message2 message2 = JSONUtil.toBean(json, Message2.class);
        MessageType messageType = MessageType.valueOf(message2.getMessageType());

        return switch (messageType) {
            case SYSTEM -> new SystemMessage(message2.getTextContent());
            case USER -> UserMessage.builder().text(message2.getTextContent()).metadata(message2.getMetadata()).media(message2.getMedia()).build();
            case ASSISTANT -> new AssistantMessage2(message2.getTextContent(), message2.getMetadata(), message2.getToolCalls(), message2.getMedia(), message2.getParams());
            case TOOL -> new ToolResponseMessage(message2.getToolResponses(), message2.getMetadata());
        };

    }

}
