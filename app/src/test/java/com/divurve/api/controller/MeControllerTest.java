package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.divurve.api.dto.me.ProfileResponse;
import com.divurve.api.dto.me.ProfileUpdateRequest;
import com.divurve.api.dto.me.RiskProfileDetailRequest;
import com.divurve.api.dto.me.RiskProfileDetailResponse;
import com.divurve.api.dto.me.RiskProfileResponse;
import com.divurve.api.dto.me.RiskProfileSimpleRequest;
import com.divurve.api.dto.me.SettingsResponse;
import com.divurve.api.dto.me.SettingsUpdateRequest;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.settings.NotificationSwitches;
import com.divurve.domain.settings.RiskProfileService;
import com.divurve.domain.settings.RiskProfileService.DetailSubmission;
import com.divurve.domain.settings.RiskProfileView;
import com.divurve.domain.settings.SettingsView;
import com.divurve.domain.settings.UserSettingsService;
import com.divurve.domain.user.UserProfileService;
import com.divurve.domain.user.UserProfileService.ProfileView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link MeController} 매핑 검증 — 도메인 뷰 → DTO 변환과 data/meta 래핑.
 * v2 계약(진단 2분할·미측정 200·알림 흡수)을 컨트롤러 층에서 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class MeControllerTest {

    @Mock
    private UserProfileService userProfileService;
    @Mock
    private RiskProfileService riskProfileService;
    @Mock
    private UserSettingsService userSettingsService;

    private final UUID userId = UUID.randomUUID();

    private MeController controller() {
        return new MeController(userProfileService, riskProfileService, userSettingsService);
    }

    private RiskProfileView measuredView() {
        return new RiskProfileView(
                RiskProfileService.STATUS_SIMPLE_DONE,
                "balanced",
                "균형항로형",
                4,
                LocalDate.of(2026, 9, 1),
                0.60,
                new RiskProfileView.Simple(
                        Map.of("q1", "B", "q2", "C", "q3", "B"),
                        List.of(new RiskProfileView.Rationale("q1", "B", 1, "작은 손실은 받아들입니다.")),
                        null),
                new RiskProfileView.Detail(false, Map.of("q4", "B"), "q5", "지출 균형을 함께 고려하는"),
                RiskProfileService.LIMITATION_NOTE);
    }

    private RiskProfileView notMeasuredView() {
        return new RiskProfileView(
                RiskProfileService.STATUS_NOT_MEASURED, null, null, null, null, null,
                new RiskProfileView.Simple(Map.of(), List.of(), null),
                new RiskProfileView.Detail(false, Map.of(), "q4", null),
                RiskProfileService.LIMITATION_NOTE);
    }

    private SettingsView settingsView() {
        return new SettingsView(
                "081", 0.5, "standard", "finance", 0.0165, 0.00825,
                false, true, true, true, false);
    }

    @Test
    void 진단_조회는_명세_필드를_그대로_내려보낸다() {
        when(riskProfileService.getRiskProfile(userId)).thenReturn(measuredView());

        ApiResponse<RiskProfileResponse> response = controller().getRiskProfile(userId);

        assertThat(response.meta()).isNotNull();
        RiskProfileResponse body = response.data();
        assertThat(body.status()).isEqualTo("simple_done");
        assertThat(body.grade()).isEqualTo("balanced");
        assertThat(body.gradeLabel()).isEqualTo("균형항로형");
        assertThat(body.score()).isEqualTo(4);
        assertThat(body.diagnosedOn()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(body.concentrationThreshold()).isEqualTo(0.60);
        assertThat(body.simple().answers()).containsEntry("q2", "C");
        assertThat(body.simple().rationale()).singleElement().satisfies(r -> {
            assertThat(r.question()).isEqualTo("q1");
            assertThat(r.choice()).isEqualTo("B");
            assertThat(r.points()).isEqualTo(1);
        });
        assertThat(body.simple().mixedResponseNote()).isNull();
        assertThat(body.detail().nextQuestion()).isEqualTo("q5");
        assertThat(body.detail().titleModifier()).isEqualTo("지출 균형을 함께 고려하는");
        assertThat(body.limitationNote()).isEqualTo(RiskProfileService.LIMITATION_NOTE);
    }

    @Test
    void 미진단이면_404가_아니라_not_measured_를_200으로_내려보낸다() {
        when(riskProfileService.getRiskProfile(userId)).thenReturn(notMeasuredView());

        RiskProfileResponse body = controller().getRiskProfile(userId).data();

        assertThat(body.status()).isEqualTo("not_measured");
        assertThat(body.grade()).isNull();
        assertThat(body.gradeLabel()).isNull();
        assertThat(body.score()).isNull();
        assertThat(body.detail().nextQuestion()).isEqualTo("q4");
    }

    @Test
    void 간편_진단_제출은_응답_맵을_그대로_넘긴다() {
        when(riskProfileService.submitSimple(eq(userId), any())).thenReturn(measuredView());

        ApiResponse<RiskProfileResponse> response = controller()
                .submitSimple(userId, new RiskProfileSimpleRequest(Map.of("q1", "B", "q2", "C", "q3", "B")));

        assertThat(response.data().grade()).isEqualTo("balanced");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(riskProfileService).submitSimple(eq(userId), captor.capture());
        assertThat(captor.getValue()).containsEntry("q3", "B");
    }

    @Test
    void 상세_진단_제출은_applied_블록을_함께_내려보낸다() {
        when(riskProfileService.submitDetail(eq(userId), any()))
                .thenReturn(new DetailSubmission(measuredView(), "standard", "finance"));

        ApiResponse<RiskProfileDetailResponse> response = controller()
                .submitDetail(userId, new RiskProfileDetailRequest(Map.of("q4", "B", "q5", "standard")));

        RiskProfileDetailResponse body = response.data();
        // 점수·유형은 상세 진단으로 바뀌지 않는다.
        assertThat(body.grade()).isEqualTo("balanced");
        assertThat(body.score()).isEqualTo(4);
        assertThat(body.detail().nextQuestion()).isEqualTo("q5");
        assertThat(body.applied().explainLevel()).isEqualTo("standard");
        assertThat(body.applied().explainDomain()).isEqualTo("finance");
        assertThat(body.applied().titleModifier()).isEqualTo("지출 균형을 함께 고려하는");
        assertThat(body.limitationNote()).isEqualTo(RiskProfileService.LIMITATION_NOTE);
    }

    @Test
    void 설정_조회는_알림_스위치까지_함께_내려보낸다() {
        when(userSettingsService.getSettings(userId)).thenReturn(settingsView());

        SettingsResponse body = controller().getSettings(userId).data();

        assertThat(body.defaultBankCode()).isEqualTo("081");
        assertThat(body.fxDiscountRatio()).isEqualTo(0.5);
        assertThat(body.explainLevel()).isEqualTo("standard");
        assertThat(body.explainDomain()).isEqualTo("finance");
        assertThat(body.baseSpreadRatio()).isEqualTo(0.0165);
        assertThat(body.effectiveSpreadRatio()).isEqualTo(0.00825);
        assertThat(body.notifyStepDue()).isFalse();
        assertThat(body.notifyRegimeShift()).isTrue();
        assertThat(body.notifyDeadlineNear()).isTrue();
        assertThat(body.notifyTargetZone()).isTrue();
        assertThat(body.notifyConcentration()).isFalse();
    }

    @Test
    void 설정_수정은_알림_스위치를_도메인_커맨드로_넘긴다() {
        when(userSettingsService.updateSettings(
                eq(userId), eq("081"), eq(0.5), eq("standard"), eq("finance"), any(NotificationSwitches.class)))
                .thenReturn(settingsView());

        ApiResponse<SettingsResponse> response = controller().updateSettings(
                userId,
                new SettingsUpdateRequest("081", 0.5, "standard", "finance", false, null, null, true, false));

        assertThat(response.data().effectiveSpreadRatio()).isEqualTo(0.00825);

        ArgumentCaptor<NotificationSwitches> captor = ArgumentCaptor.forClass(NotificationSwitches.class);
        verify(userSettingsService).updateSettings(
                eq(userId), eq("081"), eq(0.5), eq("standard"), eq("finance"), captor.capture());
        NotificationSwitches sent = captor.getValue();
        assertThat(sent.notifyStepDue()).isFalse();
        assertThat(sent.notifyRegimeShift()).isNull();   // 미변경은 null 로 전달된다
        assertThat(sent.notifyDeadlineNear()).isNull();
        assertThat(sent.notifyTargetZone()).isTrue();
        assertThat(sent.notifyConcentration()).isFalse();
    }

    @Test
    void 계정_조회는_초기_설정_완료_여부를_함께_내려보낸다() {
        Instant onboardedAt = Instant.parse("2026-09-01T15:30:00Z");
        when(userProfileService.getProfile(userId)).thenReturn(
                new ProfileView(userId, "test@example.com", "테스트사용자", false, true, onboardedAt));

        ProfileResponse body = controller().getProfile(userId).data();

        assertThat(body.userId()).isEqualTo(userId.toString());
        assertThat(body.email()).isEqualTo("test@example.com");
        assertThat(body.name()).isEqualTo("테스트사용자");
        assertThat(body.isDemo()).isFalse();
        assertThat(body.onboarded()).isTrue();
        assertThat(body.onboardedAt()).isEqualTo(onboardedAt);
    }

    @Test
    void 계정_수정은_이름을_바꾸고_결과를_래핑한다() {
        when(userProfileService.updateProfile(userId, "새이름")).thenReturn(
                new ProfileView(userId, "test@example.com", "새이름", false, false, null));

        ProfileResponse body = controller().updateProfile(userId, new ProfileUpdateRequest("새이름")).data();

        assertThat(body.name()).isEqualTo("새이름");
        assertThat(body.onboarded()).isFalse();
        assertThat(body.onboardedAt()).isNull();
    }

    @Test
    void 초기_설정_종료는_onboarded_를_true_로_바꿔_돌려준다() {
        Instant now = Instant.parse("2026-09-07T00:00:00Z");
        when(userProfileService.completeOnboarding(userId)).thenReturn(
                new ProfileView(userId, "test@example.com", "테스트사용자", false, true, now));

        ProfileResponse body = controller().completeOnboarding(userId).data();

        assertThat(body.onboarded()).isTrue();
        assertThat(body.onboardedAt()).isEqualTo(now);
    }
}
