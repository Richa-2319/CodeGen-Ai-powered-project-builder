package com.project.distributed_codegen.workspace_service.dto.project;
import com.project.distributed_codegen.common_lib.enums.ProjectRole;

import java.time.Instant;

public record ProjectSummaryResponse(
        Long id,
        String name,
        Instant createdAt,
        Instant updatedAt,
        ProjectRole role
) {
}
