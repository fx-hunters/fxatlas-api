package com.divurve.api.controller;

import com.divurve.api.dto.auth.LoginRequest;
import com.divurve.api.dto.auth.RefreshRequest;
import com.divurve.api.dto.auth.SignupRequest;
import com.divurve.api.dto.auth.TokenResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.exception.NotImplementedException;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.auth.AuthDemoService;
import com.divurve.domain.port.AuthTokens;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 엔드포인트 (명세 2장). 데모 계정 발급({@code /demo})만 실구현되어 있고,
 * signup/login/refresh 본구현은 M3-14 에서 다룬다 — 현재는 501 을 던진다.
 * 인증 방식은 JWT(액세스 30분·리프레시 14일), Spring Security 미사용.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "인증 (회원가입·로그인·토큰 갱신·데모)")
public class AuthController {

    private final AuthDemoService authDemoService;

    public AuthController(AuthDemoService authDemoService) {
        this.authDemoService = authDemoService;
    }

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
