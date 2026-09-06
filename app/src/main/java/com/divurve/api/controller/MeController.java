package com.divurve.api.controller;

import com.divurve.api.config.auth.CurrentUserContext;
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
import com.divurve.common.exception.UnauthorizedException;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.port.AuthPrincipal;
import com.divurve.domain.settings.RiskProfileService;
import com.divurve.domain.settings.RiskProfileView;
import com.divurve.domain.settings.SettingsView;
import com.divurve.domain.settings.UserSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이페이지 엔드포인트 (명세 2장, 이슈 #10). 투자성향(risk-profile)·설정(settings)은 실구현이고,
 * 프로필·알림 설정은 아직 501 을 던진다. 요청 주체는 {@link CurrentUserContext} 에서 해석한다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "MyPage", description = "프로필·투자성향·설정·알림")
public class MeController {

    private final RiskProfileService riskProfileService;
    private final UserSettingsService userSettingsService;

    public MeController(RiskProfileService riskProfileService, UserSettingsService userSettingsService) {
        this.riskProfileService = riskProfileService;
        this.userSettingsService = userSettingsService;
    }

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
        return ApiResponse.of(toRiskProfileResponse(riskProfileService.getRiskProfile(currentUserId())));
    }

    @Operation(summary = "성향 재진단", description = "5문항 이내 응답으로 안정·균형·유연 등급을 산출해 저장한다.")
    @PutMapping("/risk-profile")
    public ApiResponse<RiskProfileResponse> updateRiskProfile(@RequestBody RiskProfileUpdateRequest request) {
        List<RiskProfileService.AnswerCommand> answers = request.answers() == null
                ? List.of()
                : request.answers().stream()
                        .map(a -> new RiskProfileService.AnswerCommand(a.questionCode(), a.choice()))
                        .toList();
        return ApiResponse.of(toRiskProfileResponse(riskProfileService.reassess(currentUserId(), answers)));
    }

    @Operation(summary = "설정 조회", description = "표시 모드·주거래 은행·환전 우대율과 실효 스프레드를 반환한다.")
    @GetMapping("/settings")
    public ApiResponse<SettingsResponse> getSettings() {
        return ApiResponse.of(toSettingsResponse(userSettingsService.getSettings(currentUserId())));
    }

    @Operation(summary = "설정 수정", description = "지정하지 않은(null) 필드는 기존값을 유지한다. 실효 스프레드를 재계산해 반환한다.")
    @PutMapping("/settings")
    public ApiResponse<SettingsResponse> updateSettings(@RequestBody SettingsUpdateRequest request) {
        SettingsView view = userSettingsService.updateSettings(
                currentUserId(), request.defaultBankCode(), request.fxDiscountRatio(),
                request.explainLevel(), request.explainDomain());
        return ApiResponse.of(toSettingsResponse(view));
    }

    @Operation(summary = "알림 설정 수정")
    @PutMapping("/notifications")
    public ApiResponse<NotificationSettingsResponse> updateNotifications(
            @RequestBody NotificationSettingsRequest request) {
        throw new NotImplementedException();
    }

    /** 현재 요청 주체의 사용자 id. 인증 컨텍스트가 없으면 401. */
    private UUID currentUserId() {
        return CurrentUserContext.get()
                .map(AuthPrincipal::userId)
                .orElseThrow(UnauthorizedException::new);
    }

    private RiskProfileResponse toRiskProfileResponse(RiskProfileView view) {
        List<RiskProfileResponse.Answer> answers = view.answers().stream()
                .map(a -> new RiskProfileResponse.Answer(a.questionCode(), a.choice()))
                .toList();
        return new RiskProfileResponse(view.riskType(), view.score(), answers);
    }

    private SettingsResponse toSettingsResponse(SettingsView view) {
        return new SettingsResponse(
                view.defaultBankCode(),
                view.fxDiscountRatio(),
                view.explainLevel(),
                view.explainDomain(),
                view.baseSpreadRatio(),
                view.effectiveSpreadRatio());
    }
}
