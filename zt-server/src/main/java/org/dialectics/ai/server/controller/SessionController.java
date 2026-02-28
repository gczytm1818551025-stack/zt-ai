package org.dialectics.ai.server.controller;

import lombok.extern.slf4j.Slf4j;
import org.dialectics.ai.common.domain.R;
import org.dialectics.ai.agent.domain.vo.ChatRecordVo;
import org.dialectics.ai.agent.domain.vo.MessageVo;
import org.dialectics.ai.agent.domain.vo.SessionVo;
import org.dialectics.ai.agent.service.SessionService;
import org.dialectics.ai.agent.domain.dto.SessionCreateDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/public/session")
public class SessionController {
    @Autowired
    private SessionService sessionService;

    /**
     * 新建会话
     */
    @PostMapping
    public R<SessionVo> createSession(@RequestBody SessionCreateDto dto) {
        SessionVo vo = sessionService.createSession(dto);
        return R.ok(vo);
    }

    @GetMapping("/current/{sessionId}")
    public R<SessionVo> currentSession(@PathVariable("sessionId") String sessionId) {
        SessionVo vo = sessionService.currentSession(sessionId);
        return R.ok(vo);
    }

    /**
     * 查询单个对话的对话记忆详情
     *
     * @return 对话记录列表
     */
    @GetMapping("/{sessionId}")
    public R<List<MessageVo>> queryMemoryBySessionId(@PathVariable("sessionId") String sessionId) {
        List<MessageVo> vos = sessionService.queryMemoryBySessionId(sessionId);
        return R.ok(vos);
    }

    /**
     * 查询历史对话列表
     */
    @GetMapping("/history")
    public R<List<ChatRecordVo>> queryHistorySession() {
        List<ChatRecordVo> vos = sessionService.queryHistorySessions();
        return R.ok(vos);
    }

    /**
     * 更新历史会话标题
     */
    @PutMapping("/history")
    public void updateTitle(@RequestParam String sessionId, @RequestParam String title) {
        sessionService.updateTitle(sessionId, title);
    }

    /**
     * 删除历史会话
     */
    @DeleteMapping("/history")
    public void deleteSession(@RequestParam String sessionId) {
        sessionService.delete(sessionId);
    }
}
