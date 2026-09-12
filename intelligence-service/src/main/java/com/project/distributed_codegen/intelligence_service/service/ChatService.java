package com.project.distributed_codegen.intelligence_service.service;


import com.project.distributed_codegen.intelligence_service.dto.chat.ChatResponse;

import java.util.List;

public interface ChatService {

    List<ChatResponse> getProjectChatHistory(Long projectId);
}
