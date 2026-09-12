package com.project.distributed_codegen.intelligence_service.dto.chat;


import com.project.distributed_codegen.common_lib.enums.ChatEventType;

public record ChatEventResponse(
        Long id,
        ChatEventType type,
        Integer sequenceOrder,
        String content,
        String filePath,
        String metadata
) {
}
