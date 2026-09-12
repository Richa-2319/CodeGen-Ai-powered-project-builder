package com.project.distributed_codegen.workspace_service.dto.member;




import com.project.distributed_codegen.common_lib.enums.ProjectRole;

import java.time.Instant;

public record MemberResponse(
        Long userId,
        String username,
        String name,
        ProjectRole projectRole,
        Instant invitedAt
) {
}
