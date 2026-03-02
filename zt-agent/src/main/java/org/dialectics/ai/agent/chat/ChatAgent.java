package org.dialectics.ai.agent.chat;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.dialectics.ai.agent.AgentExecutionContext;
import org.dialectics.ai.agent.domain.vo.ChatEventVo;
import org.dialectics.ai.agent.manager.ToolResultManager;
import org.dialectics.ai.agent.memory.ZChatMemory;
import org.dialectics.ai.agent.memory.ZChatMemoryRepository;
import org.dialectics.ai.agent.service.SessionService;
import org.dialectics.ai.agent.utils.ChatSessionVisitor;
import org.dialectics.ai.common.constants.RedisConstant;
import org.dialectics.ai.common.constants.ToolConstant;
import org.dialectics.ai.common.utils.RedisRetryUtils;
import org.dialectics.ai.common.enums.ChatSessionParamEnum;
import org.dialectics.ai.common.enums.GenerateTypeEnum;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ChatAgent extends AbstractChatAgent {
    @Resource(name = "dashScopeChatClient")
    private ChatClient chatClient;
    @Autowired
    private ZChatMemory chatMemory;
    @Autowired
    private ZChatMemoryRepository chatMemoryRepository;
    @Resource
    protected MessageChatMemoryAdvisor messageChatMemoryAdvisor;
    @Autowired
    private SessionService sessionService;
    // redis存储大模型的生成状态，用于中断输出
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public ChatClient chatClient() {
        return chatClient;
    }

    @Override
    public List<Advisor> advisors() {
        return List.of(messageChatMemoryAdvisor);
    }

    @Override
    public Map<String, Object> advisorParams(AgentExecutionContext context) {
        // CONVERSATION_ID是MessageChatMemoryAdvisor的必需参数
        return Map.of(ChatMemory.CONVERSATION_ID, ChatSessionVisitor.conversationId(context));
    }

    @Override
    public void stop(String sessionId) {
        try {
            RedisRetryUtils.safeHashDelete(redisTemplate, RedisConstant.GENERATE_STATUS_KEY, sessionId);
        } catch (Exception e) {
            log.warn("停止会话失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    @Override
    public Flux<ChatEventVo> process(String question, AgentExecutionContext context) {
        // 1.读取会话的关键上下文信息
        GenerateTypeEnum type = ChatSessionVisitor.generateType(context);
        Long userId = ChatSessionVisitor.userId(context);
        String sessionId = ChatSessionVisitor.sessionId(context);
        String conversationId = ChatSessionVisitor.conversationId(context);

        // 2.如果是“重新生成”类型的对话，删除最后两条问答记忆
        if (GenerateTypeEnum.REGENERATE.equals(type)) {
            chatMemoryRepository.deleteLastNByConversationId(conversationId, 2);
        }
        // 3.生成requestId——tool结果容器中的key (数据隔离，区分同次对话的不同次问答)
        String requestId = IdUtil.fastSimpleUUID();

        // 4.补充完整agent会话上下文
        context.set(Map.of(
                ChatSessionParamEnum.QUESTION, question,
                ChatSessionParamEnum.REQUEST_ID, requestId
        ));

        // 5.构建并背压对话请求
        StringBuilder assistantMsgBuilder = new StringBuilder();
        return buildChatRequestSpec(context)
                .stream()
                .chatResponse()
                // 订阅建立时保存会话状态标记（带重试机制）
                .doFirst(() -> {
                    try {
                        RedisRetryUtils.safeHashPut(redisTemplate, RedisConstant.GENERATE_STATUS_KEY, sessionId, "1");
                    } catch (Exception e) {
                        log.warn("设置会话状态失败: sessionId={}, error={}", sessionId, e.getMessage());
                    }
                })
                .doOnComplete(() -> stop(sessionId))
                .doOnError(err -> stop(sessionId))
                // 订阅被takeWhile条件在下游截断时，保存未回答完整的assistantMessage
                .doOnCancel(() -> chatMemory.add(conversationId, new AssistantMessage(assistantMsgBuilder.toString())))
                // 对话最后刷新对话，首次则异步生成标题
                .doFinally(signalType -> sessionService.flushChat(sessionId, userId, question, assistantMsgBuilder.toString()))
                // 检查会话状态（带重试机制）
                .takeWhile(response -> {
                    try {
                        return RedisRetryUtils.safeHashGet(redisTemplate, RedisConstant.GENERATE_STATUS_KEY, sessionId) != null;
                    } catch (Exception e) {
                        log.warn("检查会话状态失败: sessionId={}, error={}", sessionId, e.getMessage());
                        return false;
                    }
                })
                .map(chatResponse -> {
                    // 大模型输出完成(metaData.finishReason=STOP)时，把requestId存入容器，
                    // 以便存储会话记忆时取出tool调用的结果并存储
                    String finishReason = chatResponse.getResult().getMetadata().getFinishReason();
                    if (StrUtil.equals("STOP", finishReason)) {
                        String id = chatResponse.getMetadata().getId();
                        ToolResultManager.put(id, ToolConstant.Context.REQUEST_ID, requestId);
                    }
                    // 拼接当前对话内容
                    String text = chatResponse.getResult().getOutput().getText();
                    assistantMsgBuilder.append(text);
                    return ChatEventVo.newDataEvent(text);
                })
                // Flux.defer延迟到订阅时才包装流，每次订阅都会触发一次新的数据源执行
                .concatWith(Flux.defer(() -> {
                    Map<String, Object> toolResultMap = ToolResultManager.get(requestId);
                    // 无参数，直接拼接结束流
                    if (MapUtil.isEmpty(toolResultMap)) {
                        return Flux.just(ChatEventVo.stopEvent());
                    }
                    // 释放tool结果，避免内存泄漏
                    ToolResultManager.remove(requestId);
                    // 拼接参数事件和结束流
                    return Flux.just(
                            ChatEventVo.newParamEvent(toolResultMap),
                            ChatEventVo.stopEvent()
                    );
                }));
    }
}
