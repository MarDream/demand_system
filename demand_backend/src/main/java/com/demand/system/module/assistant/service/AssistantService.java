package com.demand.system.module.assistant.service;

import com.demand.system.module.assistant.dto.AssistantChatRequest;
import com.demand.system.module.assistant.dto.AssistantMessageVO;
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
}
