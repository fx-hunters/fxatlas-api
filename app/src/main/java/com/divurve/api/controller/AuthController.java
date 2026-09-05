package com.divurve.api.controller;

import com.divurve.api.dto.auth.LoginRequest;
import com.divurve.api.dto.auth.RefreshRequest;
import com.divurve.api.dto.auth.SignupRequest;
import com.divurve.api.dto.auth.TokenResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.exception.NotImplementedException;
import com.divurve.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 엔드포인트 스텁 (명세 2장). 로직 미구현 — 모든 메서드가 501 을 던진다.
 * 인증 방식(JWT + refresh token(Redis) + HttpOnly Cookie)은 이후 이슈에서 구현한다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "인증 (회원가입·로그인·토큰 갱신·데모)")
public class AuthController {

    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    public ApiResponse<TokenResponse> signup(@RequestBody SignupRequest request) {
        throw new NotImplementedException();
    }

    @Operation(summary = "로그인")
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@RequestBody LoginRequest request) {
        throw new NotImplementedException();
    }

    @Operation(summary = "토큰 갱신")
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@RequestBody RefreshRequest request) {
        throw new NotImplementedException();
    }

    @Operation(summary = "샘플(데모) 계정 발급")
    @PostMapping("/demo")
    public ApiResponse<TokenResponse> demo() {
        throw new NotImplementedException();
    }
}
