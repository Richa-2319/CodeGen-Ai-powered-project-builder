package com.project.distributed_codegen.account_service.dto.auth;

public record AuthResponse(
        String token,
        UserProfileResponse user
) {

}
