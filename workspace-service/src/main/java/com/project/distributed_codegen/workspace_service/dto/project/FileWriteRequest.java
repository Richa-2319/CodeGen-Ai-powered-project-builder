package com.project.distributed_codegen.workspace_service.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FileWriteRequest(@NotBlank @Size(max = 1024) String path,
                               @NotNull @Size(max = 1048576) String content) {}
