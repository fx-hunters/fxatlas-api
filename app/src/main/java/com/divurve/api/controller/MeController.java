package com.divurve.api.controller;

import com.divurve.api.dto.me.NotificationSettingsRequest;
import com.divurve.api.dto.me.NotificationSettingsResponse;
import com.divurve.api.dto.me.ProfileResponse;
import com.divurve.api.dto.me.ProfileUpdateRequest;
import com.divurve.api.dto.me.RiskProfileResponse;
import com.divurve.api.dto.me.RiskProfileUpdateRequest;
import com.divurve.api.dto.me.SettingsResponse;
import com.divurve.api.dto.me.SettingsUpdateRequest;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.exception.NotImplementedException;
import com.divurve.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이페이지 엔드포인트 스텁 (명세 2장). 로직 미구현 — 모든 메서드가 501 을 던진다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "MyPage", description = "프로필·투자성향·설정·알림")
public class MeController {

    @Operation(summary = "프로필 조회")
    @GetMapping
    public ApiResponse<ProfileResponse> getProfile() {
        throw new NotImplementedException();
    }

    @Operation(summary = "프로필 수정")
    @PutMapping
    public ApiResponse<ProfileResponse> updateProfile(@RequestBody ProfileUpdateRequest request) {
        throw new NotImplementedException();
    }

    @Operation(summary = "현재 투자성향과 응답 내역 조회")
    @GetMapping("/risk-profile")
    public ApiResponse<RiskProfileResponse> getRiskProfile() {
        throw new NotImplementedException();
    }

    @Operation(summary = "성향 재진단")
    @PutMapping("/risk-profile")
    public ApiResponse<RiskProfileResponse> updateRiskProfile(@RequestBody RiskProfileUpdateRequest request) {
        throw new NotImplementedException();
    }

    @Operation(summary = "설정 조회")
    @GetMapping("/settings")
    public ApiResponse<SettingsResponse> getSettings() {
        throw new NotImplementedException();
    }

    @Operation(summary = "설정 수정")
    @PutMapping("/settings")
    public ApiResponse<SettingsResponse> updateSettings(@RequestBody SettingsUpdateRequest request) {
        throw new NotImplementedException();
    }

    @Operation(summary = "알림 설정 수정")
    @PutMapping("/notifications")
    public ApiResponse<NotificationSettingsResponse> updateNotifications(
            @RequestBody NotificationSettingsRequest request) {
        throw new NotImplementedException();
    }
}
