package com.divurve.api.controller;

import com.divurve.api.config.auth.CurrentUser;
import com.divurve.api.dto.me.ProfileResponse;
import com.divurve.api.dto.me.ProfileUpdateRequest;
import com.divurve.api.dto.me.RiskProfileDetailRequest;
import com.divurve.api.dto.me.RiskProfileDetailResponse;
import com.divurve.api.dto.me.RiskProfileResponse;
import com.divurve.api.dto.me.RiskProfileSimpleRequest;
import com.divurve.api.dto.me.SettingsResponse;
import com.divurve.api.dto.me.SettingsUpdateRequest;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.settings.NotificationSwitches;
import com.divurve.domain.settings.RiskProfileService;
import com.divurve.domain.settings.RiskProfileService.DetailSubmission;
import com.divurve.domain.settings.RiskProfileView;
import com.divurve.domain.settings.SettingsView;
import com.divurve.domain.settings.UserSettingsService;
import com.divurve.domain.user.UserProfileService;
import com.divurve.domain.user.UserProfileService.ProfileView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이페이지·진단·초기 설정 엔드포인트 (API 명세 v2 §3·§5.1·§5.2, 요구사항 v2 §4.2·§4.3·§4.10).
 *
 * <p>v1 대비 바뀐 계약 세 가지.
 * <ol>
 *   <li>{@code PUT /me/risk-profile} 삭제 → {@code POST /me/risk-profile/simple}(Q1~Q3)와
 *       {@code POST /me/risk-profile/detail}(Q4~Q6)로 분리했다. 상세는 점수·유형을 바꾸지 않는다(FR-DG-05).</li>
 *   <li>{@code GET /me/risk-profile} 은 미진단이어도 <b>404 가 아니라 200 + {@code not_measured}</b> 다.</li>
 *   <li>{@code PUT /me/notifications} 삭제 → {@code PUT /me/settings} 가 설명 프로필과 알림 스위치를 함께 다룬다.</li>
 * </ol>
 *
 * <p>요청 주체는 {@link CurrentUser} 파라미터로 주입받는다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "MyPage", description = "계정·진단·설정·초기 설정")
public class MeController {

    private final UserProfileService userProfileService;
    private final RiskProfileService riskProfileService;
    private final UserSettingsService userSettingsService;

    public MeController(
            UserProfileService userProfileService,
            RiskProfileService riskProfileService,
            UserSettingsService userSettingsService) {
        this.userProfileService = userProfileService;
        this.riskProfileService = riskProfileService;
        this.userSettingsService = userSettingsService;
    }

    @Operation(summary = "계정 정보 조회", description = "이름·이메일과 초기 설정 완료 여부(onboarded)를 반환한다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "UNAUTHORIZED — 토큰 없음·만료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "NOT_FOUND — 사용자 없음")
    })
    @GetMapping
    public ApiResponse<ProfileResponse> getProfile(@CurrentUser UUID userId) {
        return ApiResponse.of(toProfileResponse(userProfileService.getProfile(userId)));
    }

    @Operation(summary = "계정 정보 수정", description = "이름을 수정한다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "VALIDATION_FAILED — 이름 누락"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "UNAUTHORIZED — 토큰 없음·만료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "NOT_FOUND — 사용자 없음")
    })
    @PutMapping
    public ApiResponse<ProfileResponse> updateProfile(
            @CurrentUser UUID userId,
            @RequestBody ProfileUpdateRequest request) {
        return ApiResponse.of(toProfileResponse(userProfileService.updateProfile(userId, request.name())));
    }

    @Operation(summary = "초기 설정 종료",
            description = "users.onboarded_at 을 기록한다. 진단·자산을 전부 건너뛴 상태에서도 호출할 수 있고, "
                    + "건너뛴 항목에 임의 기본값을 채우지 않는다(FR-IS-05·FR-IS-06). 재호출해도 최초 완료 시각을 유지한다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "종료 처리 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "UNAUTHORIZED — 토큰 없음·만료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "NOT_FOUND — 사용자 없음")
    })
    @PostMapping("/onboarding/complete")
    public ApiResponse<ProfileResponse> completeOnboarding(@CurrentUser UUID userId) {
        return ApiResponse.of(toProfileResponse(userProfileService.completeOnboarding(userId)));
    }

    @Operation(summary = "진단 상태 조회",
            description = "대표 유형·상태·문항별 근거·미응답 문항을 반환한다. "
                    + "진단하지 않았어도 404 가 아니라 200 + status=not_measured 이며 grade 는 null 이다(FR-DG-02).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공 (미진단이면 status=not_measured)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "UNAUTHORIZED — 토큰 없음·만료")
    })
    @GetMapping("/risk-profile")
    public ApiResponse<RiskProfileResponse> getRiskProfile(@CurrentUser UUID userId) {
        return ApiResponse.of(toRiskProfileResponse(riskProfileService.getRiskProfile(userId)));
    }

    @Operation(summary = "간편 진단 제출 (Q1~Q3)",
            description = "답한 문항만 보내면 되고(부분 제출) 기존 응답 위에 병합된다(FR-DG-01·FR-DG-04). "
                    + "Q1~Q3 이 모두 채워졌을 때만 점수·유형이 산출된다 — 하나라도 비면 not_measured 다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "제출 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "VALIDATION_FAILED — 응답이 비었거나 문항/선택지 코드가 계약 밖"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "UNAUTHORIZED — 토큰 없음·만료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "NOT_FOUND — 사용자 없음")
    })
    @PostMapping("/risk-profile/simple")
    public ApiResponse<RiskProfileResponse> submitSimple(
            @CurrentUser UUID userId,
            @RequestBody RiskProfileSimpleRequest request) {
        return ApiResponse.of(toRiskProfileResponse(
                riskProfileService.submitSimple(userId, request.answers())));
    }

    @Operation(summary = "상세 진단 제출 (Q4~Q6)",
            description = "Q1~Q3 을 다시 묻지 않으며 중단(부분 제출)이 허용된다(FR-DG-03·FR-DG-04). "
                    + "점수·유형은 어떤 경우에도 변하지 않는다(FR-DG-05). "
                    + "Q5 는 explain_level, Q6 는 explain_domain 에 즉시 반영된다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "제출 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "VALIDATION_FAILED — 응답이 비었거나 문항/응답값이 계약 밖"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "UNAUTHORIZED — 토큰 없음·만료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "NOT_FOUND — 사용자 없음")
    })
    @PostMapping("/risk-profile/detail")
    public ApiResponse<RiskProfileDetailResponse> submitDetail(
            @CurrentUser UUID userId,
            @RequestBody RiskProfileDetailRequest request) {
        DetailSubmission submission = riskProfileService.submitDetail(userId, request.answers());
        RiskProfileResponse profile = toRiskProfileResponse(submission.profile());
        return ApiResponse.of(RiskProfileDetailResponse.of(
                profile,
                new RiskProfileDetailResponse.Applied(
                        submission.explainLevel(),
                        submission.explainDomain(),
                        profile.detail().titleModifier())));
    }

    @Operation(summary = "설정 조회",
            description = "설명 프로필(explain_level·explain_domain)·주거래 은행·환전 우대율·알림 스위치 5종과 "
                    + "실효 스프레드를 반환한다. 설정한 적 없으면 기본값으로 응답한다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "UNAUTHORIZED — 토큰 없음·만료")
    })
    @GetMapping("/settings")
    public ApiResponse<SettingsResponse> getSettings(@CurrentUser UUID userId) {
        return ApiResponse.of(toSettingsResponse(userSettingsService.getSettings(userId)));
    }

    @Operation(summary = "설정 수정",
            description = "설명 프로필과 알림 스위치를 함께 수정한다(v1 PUT /me/notifications 흡수). "
                    + "지정하지 않은(null) 필드는 기존값을 유지하며, 실효 스프레드를 재계산해 반환한다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "VALIDATION_FAILED — 우대율 범위 밖 또는 허용값 아닌 설명 프로필"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "UNAUTHORIZED — 토큰 없음·만료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "NOT_FOUND — 사용자 없음")
    })
    @PutMapping("/settings")
    public ApiResponse<SettingsResponse> updateSettings(
            @CurrentUser UUID userId,
            @RequestBody SettingsUpdateRequest request) {
        SettingsView view = userSettingsService.updateSettings(
                userId,
                request.defaultBankCode(),
                request.fxDiscountRatio(),
                request.explainLevel(),
                request.explainDomain(),
                new NotificationSwitches(
                        request.notifyStepDue(),
                        request.notifyRegimeShift(),
                        request.notifyDeadlineNear(),
                        request.notifyTargetZone(),
                        request.notifyConcentration()));
        return ApiResponse.of(toSettingsResponse(view));
    }

    private RiskProfileResponse toRiskProfileResponse(RiskProfileView view) {
        RiskProfileView.Simple simple = view.simple();
        RiskProfileView.Detail detail = view.detail();
        return new RiskProfileResponse(
                view.status(),
                view.riskType(),
                view.gradeLabel(),
                view.score(),
                view.diagnosedOn(),
                view.concentrationThreshold(),
                new RiskProfileResponse.Simple(
                        Map.copyOf(simple.answers()),
                        simple.rationale().stream()
                                .map(r -> new RiskProfileResponse.Rationale(
                                        r.question(), r.choice(), r.points(), r.reading()))
                                .toList(),
                        simple.mixedResponseNote()),
                new RiskProfileResponse.Detail(
                        detail.completed(),
                        Map.copyOf(detail.answered()),
                        detail.nextQuestion(),
                        detail.titleModifier()),
                view.limitationNote());
    }

    private SettingsResponse toSettingsResponse(SettingsView view) {
        return new SettingsResponse(
                view.defaultBankCode(),
                view.fxDiscountRatio(),
                view.explainLevel(),
                view.explainDomain(),
                view.baseSpreadRatio(),
                view.effectiveSpreadRatio(),
                view.notifyStepDue(),
                view.notifyRegimeShift(),
                view.notifyDeadlineNear(),
                view.notifyTargetZone(),
                view.notifyConcentration());
    }

    private ProfileResponse toProfileResponse(ProfileView view) {
        return new ProfileResponse(
                view.userId().toString(),
                view.email(),
                view.name(),
                view.isDemo(),
                view.onboarded(),
                view.onboardedAt());
    }
}
