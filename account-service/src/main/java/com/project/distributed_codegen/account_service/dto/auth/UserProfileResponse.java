package com.project.distributed_codegen.account_service.dto.auth;

public record UserProfileResponse(
        Long id,
        String username,
        String name
) {
}
