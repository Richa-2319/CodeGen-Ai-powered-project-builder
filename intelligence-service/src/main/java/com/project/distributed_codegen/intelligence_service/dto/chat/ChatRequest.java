package com.project.distributed_codegen.intelligence_service.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank @Size(max = 20_000) String message,
        @NotNull @Positive Long projectId) {}
