package com.demand.system.module.assistant.service;

import com.demand.system.module.assistant.dto.AssistantChatRequest;
import com.demand.system.module.assistant.dto.AssistantMessageVO;
import com.demand.system.module.assistant.dto.AssistantRegenerateRequest;
import com.demand.system.module.assistant.dto.AssistantSessionCreateDTO;
import com.demand.system.module.assistant.dto.AssistantSessionVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface AssistantService {

    List<AssistantSessionVO> listSessions();

    AssistantSessionVO createSession(AssistantSessionCreateDTO createDTO);

    List<AssistantMessageVO> listMessages(Long sessionId);

    void deleteSession(Long sessionId);

    SseEmitter streamMessage(Long sessionId, AssistantChatRequest request);

    SseEmitter regenerateMessage(Long sessionId, AssistantRegenerateRequest request);

    /**
     * 用户对写操作工具确认卡片的回填决议。
     *
     * @return 是否命中有效待确认请求（超时/不存在/非本人会话均返回 false）
     */
    boolean confirmToolCall(String requestId, boolean approved);
}
