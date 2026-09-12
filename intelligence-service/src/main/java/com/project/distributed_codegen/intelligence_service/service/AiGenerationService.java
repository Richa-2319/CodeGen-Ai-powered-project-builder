package com.project.distributed_codegen.intelligence_service.service;


import com.project.distributed_codegen.intelligence_service.dto.chat.StreamResponse;
import reactor.core.publisher.Flux;

public interface AiGenerationService {
    Flux<StreamResponse> streamResponse(String message, Long projectId);
}
