package org.dialectics.ai.agent.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.dialectics.ai.agent.domain.pojo.ZAssistantMessage;
import org.dialectics.ai.agent.domain.pojo.ZMessage;
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
        ZMessage msg = BeanUtil.copyProperties(message, ZMessage.class);

        // 设置消息内容
        msg.setTextContent(message.getText());

        if (message instanceof AssistantMessage assistantMessage) {
            msg.setToolCalls(assistantMessage.getToolCalls());

            // Tool调用结果处理
            String id = Convert.toStr(message.getMetadata().get("id"));
            if (StrUtil.isNotEmpty(id)) {
                // 获取<metaData.id>.id.<requestId>
                String requestId = Convert.toStr(ToolResultManager.get(id, ToolConstant.Context.REQUEST_ID));
                // 如果存在tool调用，requestId一定不为空
                if (StrUtil.isNotEmpty(requestId)) {
                    // 获取并设置tool调用的结果
                    Map<String, Object> toolParams = ToolResultManager.get(requestId);
                    msg.setParams(toolParams);

                    ToolResultManager.remove(requestId);
                }
            }
        }

        if (message instanceof ToolResponseMessage toolResponseMessage) {
            msg.setToolResponses(toolResponseMessage.getResponses());
        }

        return JSONUtil.toJsonStr(msg);
    }

    /**
     * 将JSON字符串反序列化为Message对象
     *
     * @param json Redis存储的JSON格式消息数据
     * @return 对应类型的Message对象
     * @throws RuntimeException 当无法识别的消息类型时抛出异常
     */
    public static Message toMessage(String json) {
        ZMessage msg = JSONUtil.toBean(json, ZMessage.class);
        MessageType messageType = MessageType.valueOf(msg.getMessageType());

        return switch (messageType) {
            case SYSTEM -> new SystemMessage(msg.getTextContent());
            case USER -> UserMessage.builder().text(msg.getTextContent()).metadata(msg.getMetadata()).media(msg.getMedia()).build();
            case ASSISTANT -> new ZAssistantMessage(
                    msg.getTextContent(),
                    msg.getMetadata(),
                    msg.getToolCalls(),
                    msg.getMedia(),
                    msg.getParams(),
                    msg.getSteps(),
                    msg.getStepCount()
            );
            case TOOL -> new ToolResponseMessage(msg.getToolResponses(), msg.getMetadata());
        };

    }

}
