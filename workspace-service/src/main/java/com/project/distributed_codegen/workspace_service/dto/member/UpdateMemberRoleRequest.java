package com.project.distributed_codegen.workspace_service.dto.member;


import com.project.distributed_codegen.common_lib.enums.ProjectRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(
        @NotNull ProjectRole role) {
}
