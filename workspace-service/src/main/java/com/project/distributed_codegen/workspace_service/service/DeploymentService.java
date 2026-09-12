package com.project.distributed_codegen.workspace_service.service;


import com.project.distributed_codegen.workspace_service.dto.project.DeployResponse;
import org.jspecify.annotations.Nullable;

public interface DeploymentService {
    @Nullable DeployResponse deploy(Long projectId);
}
