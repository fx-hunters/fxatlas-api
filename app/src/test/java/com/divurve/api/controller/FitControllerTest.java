package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

import com.divurve.api.dto.fit.FitPreviewRequest;
import com.divurve.api.dto.fit.FitPreviewResponse;
import com.divurve.api.dto.fit.FitResponse;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.fit.FitService;
import com.divurve.domain.settings.RiskProfileView;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link FitController} — 명세 §5.5 · §5.6 응답 모양. 추천 문구·목표 제안이 없어야 한다(FR-FT-04·06).
 */
@ExtendWith(MockitoExtension.class)
class FitControllerTest {

    @Mock
    private FitService fitService;

    private final UUID userId = UUID.randomUUID();

    private FitController controller() {
        return new FitController(fitService);
    }

    @Test
    @DisplayName("명세 §5.5 — risk_profile · concentration · relation · basis_note")
    void getFit_는_명세_5_5_모양으로_감싼다() {
        when(fitService.getFit(userId)).thenReturn(new FitService.FitDiagnosis(
                new RiskProfileView("simple_done", "balanced", "균형항로형", 4,
                        LocalDate.of(2026, 9, 1), 0.60, null, null, null),
                new FitService.ConcentrationView("USD", 0.6388, 0.60, "above_threshold", 0.0388),
                FitService.RELATION_ABOVE));

        ApiResponse<FitResponse> response = controller().getFit(userId);
        FitResponse data = response.data();

        assertThat(response.meta()).isNotNull();
        assertThat(data.riskProfile()).isEqualTo(new FitResponse.RiskProfile(
                "simple_done", "balanced", "균형항로형", LocalDate.of(2026, 9, 1)));
        assertThat(data.concentration()).isEqualTo(new FitResponse.Concentration(
                "USD", 0.6388, 0.60, "above_threshold"));
        assertThat(data.relation()).isEqualTo(new FitResponse.Relation(
                "concentration_above_profile",
                new FitResponse.Facts(0.6388, 0.60, 0.0388)));
        assertThat(data.basisNote()).isEqualTo(FitService.BASIS_NOTE);
    }

    @Test
    @DisplayName("성향 미측정이면 등급·기준선·사실값이 전부 null 이다")
    void 성향_미측정이면_전부_null_이다() {
        when(fitService.getFit(userId)).thenReturn(new FitService.FitDiagnosis(
                new RiskProfileView("not_measured", null, null, null, null, null, null, null, null),
                new FitService.ConcentrationView(null, null, null, "unknown", null),
                FitService.RELATION_UNKNOWN));

        FitResponse data = controller().getFit(userId).data();

        assertThat(data.riskProfile().grade()).isNull();
        assertThat(data.riskProfile().diagnosedOn()).isNull();
        assertThat(data.concentration().threshold()).isNull();
        assertThat(data.relation().code()).isEqualTo("risk_profile_not_measured");
        assertThat(data.relation().facts())
                .isEqualTo(new FitResponse.Facts(null, null, null));
    }

    @Test
    @DisplayName("명세 §5.6 — assumption 문구와 전후 변화값만 내려간다")
    void preview_는_명세_5_6_모양으로_감싼다() {
        when(fitService.preview(userId, "JPY", 0.10)).thenReturn(fixturePreview(0.10));

        ApiResponse<FitPreviewResponse> response =
                controller().preview(userId, new FitPreviewRequest("JPY", 0.10));
        FitPreviewResponse data = response.data();

        assertThat(data.assumption())
                .isEqualTo("외화자산 총액 24,720,000원을 고정한 채 JPY 비중만 10%p 높인 가정입니다.");
        assertThat(data.exposure().before())
                .containsExactly(entry("USD", 0.6388), entry("JPY", 0.2213), entry("EUR", 0.1400));
        assertThat(data.exposure().after())
                .containsExactly(entry("USD", 0.5567), entry("JPY", 0.3213), entry("EUR", 0.1220));

        assertThat(data.concentration().before())
                .isEqualTo(new FitPreviewResponse.Snapshot("USD", 0.6388, "above_threshold"));
        assertThat(data.concentration().after())
                .isEqualTo(new FitPreviewResponse.Snapshot("USD", 0.5567, "within_threshold"));
        assertThat(data.concentration().threshold()).isEqualTo(0.60);

        // 명세 §5.6 예시대로 통화 키와 total_krw 를 한 객체에 담는다. 합계는 전후가 같다.
        assertThat(data.sensitivity1pct().before()).containsExactly(
                entry("USD", 157_900L), entry("JPY", 54_700L), entry("EUR", 34_600L),
                entry("total_krw", 247_200L));
        assertThat(data.sensitivity1pct().after()).containsExactly(
                entry("USD", 137_623L), entry("JPY", 79_420L), entry("EUR", 30_157L),
                entry("total_krw", 247_200L));
    }

    @Test
    @DisplayName("비중을 낮추는 가정이면 문구도 '낮춘'으로 바뀐다")
    void 비중을_낮추는_가정() {
        when(fitService.preview(userId, "JPY", -0.05)).thenReturn(fixturePreview(-0.05));

        FitPreviewResponse data =
                controller().preview(userId, new FitPreviewRequest("JPY", -0.05)).data();

        assertThat(data.assumption())
                .isEqualTo("외화자산 총액 24,720,000원을 고정한 채 JPY 비중만 5%p 낮춘 가정입니다.");
    }

    private FitService.FitPreview fixturePreview(double deltaShare) {
        Map<String, Double> before = new LinkedHashMap<>();
        before.put("USD", 0.6388);
        before.put("JPY", 0.2213);
        before.put("EUR", 0.1400);
        Map<String, Double> after = new LinkedHashMap<>();
        after.put("USD", 0.5567);
        after.put("JPY", 0.3213);
        after.put("EUR", 0.1220);

        Map<String, Long> sensitivityBefore = new LinkedHashMap<>();
        sensitivityBefore.put("USD", 157_900L);
        sensitivityBefore.put("JPY", 54_700L);
        sensitivityBefore.put("EUR", 34_600L);
        Map<String, Long> sensitivityAfter = new LinkedHashMap<>();
        sensitivityAfter.put("USD", 137_623L);
        sensitivityAfter.put("JPY", 79_420L);
        sensitivityAfter.put("EUR", 30_157L);

        return new FitService.FitPreview(
                "JPY",
                deltaShare,
                24_720_000L,
                before,
                after,
                new FitService.ConcentrationView("USD", 0.6388, 0.60, "above_threshold", 0.0388),
                new FitService.ConcentrationView("USD", 0.5567, 0.60, "within_threshold", -0.0433),
                0.60,
                new FitService.SensitivityView(247_200L, sensitivityBefore),
                new FitService.SensitivityView(247_200L, sensitivityAfter));
    }
}
