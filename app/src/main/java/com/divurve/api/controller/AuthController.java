package com.divurve.api.controller;

import com.divurve.api.dto.auth.LoginRequest;
import com.divurve.api.dto.auth.RefreshRequest;
import com.divurve.api.dto.auth.SignupRequest;
import com.divurve.api.dto.auth.TokenResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.auth.AuthDemoService;
import com.divurve.domain.auth.AuthService;
import com.divurve.domain.port.AuthTokens;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 엔드포인트 (명세 2장, 이슈 #22). signup/login/refresh 본구현 + 데모 계정 발급.
 * 인증 방식은 JWT(액세스 30분·리프레시 14일), Spring Security 미사용.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "인증 (회원가입·로그인·토큰 갱신·데모)")
public class AuthController {

    private final AuthService authService;
    private final AuthDemoService authDemoService;

    public AuthController(AuthService authService, AuthDemoService authDemoService) {
        this.authService = authService;
        this.authDemoService = authDemoService;
    }

    @Operation(summary = "회원가입", description = "이메일, 비밀번호, 이름, 온보딩 목적으로 계정을 생성한다. 이메일은 고유해야 한다.")
    @PostMapping("/signup")
    public ApiResponse<TokenResponse> signup(@RequestBody SignupRequest request) {
        AuthTokens tokens = authService.signup(
                request.email(),
                request.password(),
                request.name(),
                request.onboardingPurpose());
        return ApiResponse.of(new TokenResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                tokens.accessTokenTtlSeconds(),
                false));
    }

    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인한다.")
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@RequestBody LoginRequest request) {
        AuthTokens tokens = authService.login(request.email(), request.password());
        return ApiResponse.of(new TokenResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                tokens.accessTokenTtlSeconds(),
                false));
    }

    @Operation(summary = "토큰 갱신", description = "리프레시 토큰으로 새로운 액세스 토큰을 받는다. 리프레시 토큰은 재사용 가능하다.")
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@RequestBody RefreshRequest request) {
        AuthTokens tokens = authService.refreshAccessToken(request.refreshToken());
        return ApiResponse.of(new TokenResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                tokens.accessTokenTtlSeconds(),
                false));
    }

    @Operation(summary = "샘플(데모) 계정 발급",
            description = "회원가입 없이 샘플 데이터가 채워진 데모 계정을 만들고 토큰을 발급한다. 토큰에 is_demo=true 가 실린다.")
    @PostMapping("/demo")
    public ApiResponse<TokenResponse> demo() {
        AuthTokens tokens = authDemoService.createDemoSession();
        return ApiResponse.of(new TokenResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                tokens.accessTokenTtlSeconds(),
                true));
    }
}
