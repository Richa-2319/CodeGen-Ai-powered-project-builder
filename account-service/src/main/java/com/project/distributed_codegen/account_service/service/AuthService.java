package com.project.distributed_codegen.account_service.service;


import com.project.distributed_codegen.account_service.dto.auth.AuthResponse;
import com.project.distributed_codegen.account_service.dto.auth.LoginRequest;
import com.project.distributed_codegen.account_service.dto.auth.SignupRequest;

public interface AuthService {
    AuthResponse signup(SignupRequest request);

    AuthResponse login(LoginRequest request);
}
