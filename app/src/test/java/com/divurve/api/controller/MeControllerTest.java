package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.divurve.api.config.auth.CurrentUserContext;
import com.divurve.api.dto.me.RiskProfileResponse;
import com.divurve.api.dto.me.RiskProfileUpdateRequest;
import com.divurve.api.dto.me.SettingsResponse;
import com.divurve.api.dto.me.SettingsUpdateRequest;
import com.divurve.common.exception.UnauthorizedException;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.port.AuthPrincipal;
import com.divurve.domain.settings.RiskProfileService;
import com.divurve.domain.settings.RiskProfileService.AnswerCommand;
import com.divurve.domain.settings.RiskProfileView;
import com.divurve.domain.settings.SettingsView;
import com.divurve.domain.settings.UserSettingsService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link MeController} 매핑 검증 — 도메인 뷰 → DTO 변환, data/meta 래핑, 요청 주체 해석(미인증 401).
 */
@ExtendWith(MockitoExtension.class)
class MeControllerTest {

    @Mock
    private RiskProfileService riskProfileService;
    @Mock
    private UserSettingsService userSettingsService;

    private final UUID userId = UUID.randomUUID();

    private MeController controller() {
        return new MeController(riskProfileService, userSettingsService);
    }

    private void authenticate() {
        CurrentUserContext.set(new AuthPrincipal(userId, false));
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void getRiskProfile_은_현재_성향을_data_meta로_래핑한다() {
        authenticate();
        when(riskProfileService.getRiskProfile(userId)).thenReturn(
                new RiskProfileView("balanced", 6, List.of(new RiskProfileView.Answer("Q1", 2))));

        ApiResponse<RiskProfileResponse> response = controller().getRiskProfile();

        assertThat(response.meta()).isNotNull();
        assertThat(response.data().riskType()).isEqualTo("balanced");
        assertThat(response.data().score()).isEqualTo(6);
        assertThat(response.data().answers()).singleElement()
                .satisfies(a -> {
                    assertThat(a.questionCode()).isEqualTo("Q1");
                    assertThat(a.choice()).isEqualTo(2);
                });
    }

    @Test
    void updateRiskProfile_은_응답을_커맨드로_변환해_재진단한다() {
        authenticate();
        when(riskProfileService.reassess(eq(userId), any())).thenReturn(
                new RiskProfileView("flexible", 9, List.of()));

        RiskProfileUpdateRequest request = new RiskProfileUpdateRequest(List.of(
                new RiskProfileUpdateRequest.Answer("Q1", 3),
                new RiskProfileUpdateRequest.Answer("Q2", 3)));
        ApiResponse<RiskProfileResponse> response = controller().updateRiskProfile(request);

        assertThat(response.data().riskType()).isEqualTo("flexible");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AnswerCommand>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(riskProfileService).reassess(eq(userId), captor.capture());
        assertThat(captor.getValue()).extracting(AnswerCommand::choice).containsExactly(3, 3);
    }

    @Test
    void updateRiskProfile_은_응답이_null이면_빈_목록으로_넘긴다() {
        authenticate();
        when(riskProfileService.reassess(eq(userId), any())).thenReturn(
                new RiskProfileView("stable", 0, List.of()));

        controller().updateRiskProfile(new RiskProfileUpdateRequest(null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AnswerCommand>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(riskProfileService).reassess(eq(userId), captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void getSettings_은_설정과_실효스프레드를_래핑한다() {
        authenticate();
        when(userSettingsService.getSettings(userId)).thenReturn(
                new SettingsView("004", 0.8, "simple", 0.0175, 0.0035));

        ApiResponse<SettingsResponse> response = controller().getSettings();

        SettingsResponse body = response.data();
        assertThat(body.defaultBankCode()).isEqualTo("004");
        assertThat(body.fxDiscountRatio()).isEqualTo(0.8);
        assertThat(body.displayMode()).isEqualTo("simple");
        assertThat(body.baseSpreadRatio()).isEqualTo(0.0175);
        assertThat(body.effectiveSpreadRatio()).isEqualTo(0.0035);
    }

    @Test
    void updateSettings_은_요청값을_그대로_전달하고_결과를_래핑한다() {
        authenticate();
        when(userSettingsService.updateSettings(userId, "081", 0.5, "standard")).thenReturn(
                new SettingsView("081", 0.5, "standard", 0.0165, 0.00825));

        ApiResponse<SettingsResponse> response = controller()
                .updateSettings(new SettingsUpdateRequest("081", 0.5, "standard"));

        assertThat(response.data().effectiveSpreadRatio()).isEqualTo(0.00825);
    }

    @Test
    void 인증_컨텍스트가_없으면_401() {
        assertThatThrownBy(() -> controller().getRiskProfile())
                .isInstanceOf(UnauthorizedException.class);
    }
}
