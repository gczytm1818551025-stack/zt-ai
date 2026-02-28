package org.dialectics.ai.server.controller;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.dialectics.ai.agent.manager.ReActStreamManager;
import org.dialectics.ai.common.domain.EventVo;
import org.dialectics.ai.server.domain.dto.ChatDto;
import org.dialectics.ai.agent.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/public/agent")
@Slf4j
public class ChatController {
    @Resource
    private ChatService commonChatService;
    @Resource
    private ChatService reActChatService;
    @Resource
    private ReActStreamManager streamManager;

    /**
     * 普通问答
     */
    @PostMapping(path = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<? extends EventVo> chat(@RequestBody ChatDto chatDto) {
        return commonChatService.chat(chatDto.getQuestion(), chatDto.getSessionId(), chatDto.getType());
    }

    /**
     * ReAct任务完成
     */
    @PostMapping(path = "/task", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<? extends EventVo> reActChat(@RequestBody ChatDto chatDto) {
        return reActChatService.chat(chatDto.getQuestion(), chatDto.getSessionId(), chatDto.getType());
    }

    /**
     * 终止对话
     *
     * @param sessionId 会话id
     */
    @PostMapping("/stop")
    public void stop(@RequestParam("sessionId") String sessionId) {
        commonChatService.stop(sessionId);
    }

    /**
     * 终止ReAct对话
     *
     * @param sessionId 会话id
     */
    @PostMapping("/react/stop")
    public void stopReAct(@RequestParam("sessionId") String sessionId) {
        reActChatService.stop(sessionId);
    }

    /**
     * 查询会话是否正在进行（ReAct 模式）
     *
     * @param sessionId 会话id
     * @return 会话状态：isActive-是否正在进行，activeSubscribers-活跃订阅者数
     */
    @GetMapping("/status/{sessionId}")
    public Map<String, Object> getReActStatus(@PathVariable String sessionId) {
        boolean isActive = streamManager.isActive(sessionId);
        long activeSubscribers = streamManager.countActiveSubscriber(sessionId);
        return Map.of(
                "isActive", isActive,
                "activeSubscribers", activeSubscribers
        );
    }
}
