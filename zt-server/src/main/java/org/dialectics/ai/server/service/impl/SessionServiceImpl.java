package org.dialectics.ai.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dialectics.ai.agent.domain.dto.SessionCreateDto;
import org.dialectics.ai.agent.domain.pojo.AssistantMessage2;
import org.dialectics.ai.agent.manager.PromptManager;
import org.dialectics.ai.common.constants.PromptNameConstant;
import org.dialectics.ai.common.enums.MessageTypeEnum;
import org.dialectics.ai.common.threadlocal.UserContext;
import org.dialectics.ai.server.domain.pojo.ChatRecord;
import org.dialectics.ai.agent.domain.vo.ChatRecordVo;
import org.dialectics.ai.agent.domain.vo.MessageVo;
import org.dialectics.ai.agent.domain.vo.SessionVo;
import org.dialectics.ai.server.mapper.ChatRecordMapper;
import org.dialectics.ai.agent.service.ChatService;
import org.dialectics.ai.agent.service.SessionService;
import org.dialectics.ai.common.enums.ReActStepTypeEnum;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SessionServiceImpl extends ServiceImpl<ChatRecordMapper, ChatRecord> implements SessionService {
    @Autowired
    private ChatMemory chatMemory;
    @Resource
    private ChatClient summaryClient;

    @Override
    public SessionVo createSession(SessionCreateDto dto) {
        // 1.生成sessionId
        String sessionId = IdUtil.fastSimpleUUID();
        // 2.入库
        String defaultTitle = ChatRecord.defaultTitle(dto.getType());
        ChatRecord chatRecord = ChatRecord.builder()
                .title(defaultTitle)
                .sessionId(sessionId)
                .sessionType(dto.getType())
                .userId(UserContext.get())
                .build();
        this.save(chatRecord);

        return SessionVo.builder()
                .sessionId(sessionId)
                .sessionType(dto.getType())
                .title(defaultTitle)
                .build();
    }

    @Override
    public SessionVo currentSession(String sessionId) {
        ChatRecord chatRecord = this.lambdaQuery().eq(ChatRecord::getSessionId, sessionId).one();
        return BeanUtil.copyProperties(chatRecord, SessionVo.class);
    }

    @Override
    public List<MessageVo> queryMemoryBySessionId(String sessionId) {
        String conversationId = ChatService.getConversationId(sessionId, String.valueOf(UserContext.get()));
        List<Message> messages = chatMemory.get(conversationId);

        List<MessageVo> result = new ArrayList<>();
        List<Map<String, Object>> reActSteps = new ArrayList<>();
        String finalContent = null;
        int stepCount = 0;

        for (Message message : messages) {
            if (message instanceof UserMessage) {
                // 用户消息
                result.add(MessageVo.builder()
                        .type(MessageTypeEnum.USER)
                        .content(message.getText())
                        .build());
            } else if (message instanceof AssistantMessage) {
                // 普通 Assistant 消息（无 params）
                result.add(MessageVo.builder()
                        .type(MessageTypeEnum.ASSISTANT)
                        .content(message.getText())
                        .build());
            } else if (message instanceof AssistantMessage2) {
                // AssistantMessage2（包含 params）
                AssistantMessage2 msg2 = (AssistantMessage2) message;

                if (msg2.getParams() == null) {
                    // 无 params，按普通消息处理
                    result.add(MessageVo.builder()
                            .type(MessageTypeEnum.ASSISTANT)
                            .content(msg2.getText())
                            .build());
                } else {
                    // 有 params，解析为 ReAct 步骤
                    Map<String, Object> params = msg2.getParams();
                    Integer typeCode = (Integer) params.get("type");

                    ReActStepTypeEnum stepType = ReActStepTypeEnum.fromCode(typeCode);

                    if (stepType != null) {
                        // 根据 type 分类处理
                        switch (stepType) {
                            case PLAN:
                                // PLAN: index, taskContent, previousEvaluation, memory, thinking
                                Map<String, Object> planData = Map.of(
                                        "type", typeCode,
                                        "index", params.get("index"),
                                        "taskContent", params.get("taskContent"),
                                        "previousEvaluation", params.get("previousEvaluation"),
                                        "memory", params.get("memory"),
                                        "thinking", params.get("thinking")
                                );
                                reActSteps.add(planData);
                                break;
                            case THINKING:
                                // THINKING: thinkContent
                                Map<String, Object> thinkingData = Map.of(
                                        "type", typeCode,
                                        "thinkContent", params.get("thinkContent")
                                );
                                reActSteps.add(thinkingData);
                                break;
                            case ACTION:
                                // ACTION: success, result, resultType
                                stepCount++;
                                Map<String, Object> actionData = Map.of(
                                        "type", typeCode,
                                        "success", params.get("success"),
                                        "result", params.get("result"),
                                        "resultType", params.get("resultType")
                                );
                                reActSteps.add(actionData);
                                break;
                            case FINAL:
                                // FINAL: content 存储在消息的 text 中
                                finalContent = msg2.getText();
                                break;
                        }
                    }
                }
            }
        }

        // 如果有 ReAct 步骤，聚合到最后的 Assistant 消息中
        if (!reActSteps.isEmpty()) {
            result.add(MessageVo.builder()
                    .type(MessageTypeEnum.ASSISTANT)
                    .content(finalContent != null ? finalContent : "")
                    .params(Map.of("steps", new ArrayList<>(reActSteps), "stepCount", stepCount))
                    .build());
        }

        return result;
    }

    @Async
    @Override
    public void flushChat(String sessionId, Long userId, String question, String response) {
        log.info("更新会话信息,userId:{}, sessionId:{}, title:{}", userId, sessionId, question);

        // 1.查询会话信息
        ChatRecord session = this.getOne(new LambdaQueryWrapper<ChatRecord>()
                .eq(ChatRecord::getSessionId, sessionId)
                .eq(ChatRecord::getUserId, userId));
        if (null == session) {
            throw new RuntimeException("对话不存在");
        }

        // 2.如果会话标题为默认则生成会话标题
        if (StrUtil.equals(ChatRecord.defaultTitle(session.getSessionType()), session.getTitle())
                && StrUtil.isNotEmpty(question) && StrUtil.isNotEmpty(response)) {
            String t = summaryClient.prompt()
                    .system(PromptManager.renderFrom(PromptNameConstant.TITLE_SUMMARY))
                    .user(getSummaryUserPrompt(question, response))
                    .call()
                    .content();
            session.setTitle(StrUtil.sub(t, 0, 50));
        }
        // 3.更新数据库
        this.updateById(session);
    }

    @Override
    public List<ChatRecordVo> queryHistorySessions() {
        List<ChatRecord> sessions = this.lambdaQuery()
                .isNotNull(ChatRecord::getTitle)
                .eq(ChatRecord::getUserId, UserContext.get())
                .orderByDesc(ChatRecord::getUpdateTime)
                .last("LIMIT 30")
                .list();
        if (CollUtil.isEmpty(sessions)) {
            return List.of();
        }
        return CollStreamUtil.toList(sessions, s -> ChatRecordVo.builder()
                .title(s.getTitle())
                .sessionType(s.getSessionType())
                .sessionId(s.getSessionId())
                .updateTime(s.getUpdateTime())
                .build());
    }

    @Override
    public void updateTitle(String sessionId, String title) {
        this.lambdaUpdate()
                .eq(ChatRecord::getUserId, UserContext.get())
                .eq(ChatRecord::getSessionId, sessionId)
                .set(ChatRecord::getTitle, title)
                .update();
    }

    @Override
    public void delete(String sessionId) {
        // 1.删除对话记录
        this.remove(new LambdaQueryWrapper<ChatRecord>().eq(ChatRecord::getUserId, UserContext.get()).eq(ChatRecord::getSessionId, sessionId));

        // 2.删除对话记忆
        String conversationId = ChatService.getConversationId(sessionId, String.valueOf(UserContext.get()));
        chatMemory.clear(conversationId);
    }

    /**
     * 删除对话记忆（按会话类型）
     * 注意：此方法提供给前端使用，用于清空指定类型的会话记忆
     * 例如：清空 Agent 模式的 ReAct 步骤记忆，但保留普通聊天历史
     *
     * @param sessionId 会话 ID
     * @param modeType 会话类型（CHAT/AGENT），可选
     * @param clearAll 是否清空所有记忆，默认 true
     */
    public void clearConversationMemory(String sessionId, String modeType, Boolean clearAll) {
        String conversationId = ChatService.getConversationId(sessionId, String.valueOf(UserContext.get()));
        chatMemory.clear(conversationId);
    }

    /**
     * 根据一问一答生成标题总结的用户提示词
     */
    private static String getSummaryUserPrompt(String question, String response) {
        return StrUtil.format("用户提问:{}，大模型回答:{}", question, response);
    }
}
