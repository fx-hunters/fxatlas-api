package com.divurve.engine.attribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.divurve.engine.attribution.AttributionCalculator.AttributionResult;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AttributionCalculator} — 손익 4분해 (FR-XR-04, 요구사항 v2 §4.6).
 *
 * <p>핵심 불변식은 <b>네 항의 합 = {@code current_krw − cost_basis_krw}</b> 다. v1 의 로그수익률
 * 분해는 이 항등식이 깨졌고({@code interaction ≈ −6e−17}, 합 760,863 ≠ 총손익 780,425),
 * 그것을 잡아낼 테스트도 없었다. 아래 테스트가 그 항등식을 고정한다.
 */
class AttributionCalculatorTest {

    private final AttributionCalculator calculator = new AttributionCalculator();

    // --- API 명세 v2 §4 Mock fixture 재현 ---

    /**
     * 명세 §4 fixture 의 USD 포지션. 매입원가 15,050,000원 → 현재 15,790,000원(= §4 USD 노출).
     *
     * <p>입력은 fixture 가 고정한 두 금액에서 역산했다: 매입 10,000 USD @ 1,505.00원,
     * 현재 환율 1,462.86원(= −2.80%), 거래비용 0.30%. 현재 평가액은 15,835,150원이므로
     * 수량·주가 상승분은 {@code 15,835,150 ÷ 1,462.86} 달러다.
     */
    @Test
    @DisplayName("명세 §5.4 fixture — 네 항의 합이 총손익 740,000원과 정확히 일치한다")
    void fixture_네항의_합이_총손익과_일치한다() {
        AttributionResult result = fixtureUsdPosition();

        assertThat(result.costBasisKrw()).isEqualTo(15_050_000L);
        assertThat(result.currentKrw()).isEqualTo(15_790_000L);
        assertThat(result.totalReturn()).isEqualTo(0.0492);

        assertThat(result.asset().krw()
                + result.fx().krw()
                + result.interaction().krw()
                + result.cost().krw())
                .isEqualTo(740_000L)
                .isEqualTo(result.currentKrw() - result.costBasisKrw());
    }

    @Test
    @DisplayName("명세 §5.4 fixture — asset·fx·interaction·cost 네 항의 금액과 기여도")
    void fixture_네항의_금액과_기여도() {
        AttributionResult result = fixtureUsdPosition();

        assertThat(result.asset().key()).isEqualTo(AttributionCalculator.KEY_ASSET);
        assertThat(result.asset().krw()).isEqualTo(1_241_307L);
        assertThat(result.asset().contributionPp()).isEqualTo(0.0825);

        assertThat(result.fx().key()).isEqualTo(AttributionCalculator.KEY_FX);
        assertThat(result.fx().krw()).isEqualTo(-421_400L);
        assertThat(result.fx().contributionPp()).isEqualTo(-0.0280);

        // 상호작용은 정의상 R_asset × R_fx 다. 명세 예시의 −30,000원(pp −0.0020)은 이 곱과 맞지 않는다.
        assertThat(result.interaction().key()).isEqualTo(AttributionCalculator.KEY_INTERACTION);
        assertThat(result.interaction().krw()).isEqualTo(-34_757L);
        assertThat(result.interaction().contributionPp()).isEqualTo(-0.0023);

        assertThat(result.cost().key()).isEqualTo(AttributionCalculator.KEY_COST);
        assertThat(result.cost().krw()).isEqualTo(-45_150L);
        assertThat(result.cost().contributionPp()).isEqualTo(-0.0030);
    }

    @Test
    @DisplayName("명세 §5.4 by_holding — (1+0.091)(1−0.030)−1 = 0.0583")
    void fixture_종목별_원화수익률() {
        assertThat(calculator.krwReturn(0.091, -0.030)).isEqualTo(0.0583);
    }

    private AttributionResult fixtureUsdPosition() {
        return calculator.decompose(
                10_000.0,
                15_835_150.0 / 1462.86,
                new BigDecimal("1505.00"),
                new BigDecimal("1462.86"),
                0.0030);
    }

    // --- 항등식·부호 ---

    @Test
    @DisplayName("자산만 오르고 환율이 그대로면 fx·interaction 은 0이고 asset 이 전부다")
    void 환율_불변이면_자산항만_남는다() {
        AttributionResult result = calculator.decompose(
                1_000.0, 1_100.0, new BigDecimal("1000"), new BigDecimal("1000"), 0.0);

        assertThat(result.costBasisKrw()).isEqualTo(1_000_000L);
        assertThat(result.asset().krw()).isEqualTo(100_000L);
        assertThat(result.fx().krw()).isZero();
        assertThat(result.interaction().krw()).isZero();
        assertThat(result.cost().krw()).isZero();
        assertThat(result.currentKrw()).isEqualTo(1_100_000L);
        assertThat(result.totalReturn()).isEqualTo(0.1);
        assertThat(result.assetReturn()).isEqualTo(0.1);
        assertThat(result.fxReturn()).isZero();
    }

    @Test
    @DisplayName("환율만 오르고 자산이 그대로면 asset·interaction 은 0이고 fx 가 전부다")
    void 자산_불변이면_환율항만_남는다() {
        AttributionResult result = calculator.decompose(
                1_000.0, 1_000.0, new BigDecimal("1000"), new BigDecimal("1100"), 0.0);

        assertThat(result.asset().krw()).isZero();
        assertThat(result.fx().krw()).isEqualTo(100_000L);
        assertThat(result.interaction().krw()).isZero();
        assertThat(result.fxReturn()).isEqualTo(0.1);
    }

    @Test
    @DisplayName("자산·환율이 함께 움직이면 interaction 이 0이 아니다 (로그분해에서는 항상 0이었다)")
    void 상호작용항이_0이_아니다() {
        AttributionResult result = calculator.decompose(
                1_000.0, 1_100.0, new BigDecimal("1000"), new BigDecimal("1100"), 0.0);

        assertThat(result.interaction().krw()).isEqualTo(10_000L);
        assertThat(result.asset().krw() + result.fx().krw() + result.interaction().krw())
                .isEqualTo(result.currentKrw() - result.costBasisKrw())
                .isEqualTo(210_000L);
    }

    @Test
    @DisplayName("전량 손실은 −100%로 나온다 (v1 은 safeLog 때문에 0%로 표시했다)")
    void 전량_손실은_마이너스_100퍼센트다() {
        AttributionResult result = calculator.decompose(
                1_000.0, 0.0, new BigDecimal("1000"), new BigDecimal("1000"), 0.0);

        assertThat(result.assetReturn()).isEqualTo(-1.0);
        assertThat(result.asset().krw()).isEqualTo(-1_000_000L);
        assertThat(result.currentKrw()).isZero();
        assertThat(result.totalReturn()).isEqualTo(-1.0);
    }

    @Test
    @DisplayName("비용은 항상 음수 기여이며 매입원가 대비 비율만큼 깎는다")
    void 비용항은_매입원가_대비다() {
        AttributionResult result = calculator.decompose(
                1_000.0, 1_000.0, new BigDecimal("1000"), new BigDecimal("1000"), 0.01);

        assertThat(result.cost().krw()).isEqualTo(-10_000L);
        assertThat(result.currentKrw()).isEqualTo(990_000L);
    }

    // --- 합산 ---

    @Test
    @DisplayName("종목별 결과를 합치면 금액은 더해지고 기여도는 합산 매입원가 대비로 다시 계산된다")
    void 합산은_매입원가_가중이다() {
        AttributionResult first = calculator.decompose(
                1_000.0, 1_100.0, new BigDecimal("1000"), new BigDecimal("1000"), 0.0);
        AttributionResult second = calculator.decompose(
                3_000.0, 3_000.0, new BigDecimal("1000"), new BigDecimal("1000"), 0.0);

        AttributionResult total = calculator.aggregate(List.of(first, second));

        assertThat(total.costBasisKrw()).isEqualTo(4_000_000L);
        assertThat(total.asset().krw()).isEqualTo(100_000L);
        assertThat(total.fx().krw()).isZero();
        assertThat(total.interaction().krw()).isZero();
        assertThat(total.cost().krw()).isZero();
        assertThat(total.currentKrw()).isEqualTo(4_100_000L);
        // 100,000 / 4,000,000 = 0.025 — 매입원가 가중평균 수익률
        assertThat(total.totalReturn()).isEqualTo(0.025);
        assertThat(total.assetReturn()).isEqualTo(0.025);
        assertThat(total.fxReturn()).isZero();
        assertThat(total.asset().contributionPp()).isEqualTo(0.025);
    }

    @Test
    @DisplayName("합산 결과도 네 항의 합 = 총손익 항등식을 지킨다")
    void 합산도_항등식을_지킨다() {
        AttributionResult first = calculator.decompose(
                1_000.0, 1_100.0, new BigDecimal("1000"), new BigDecimal("1100"), 0.01);
        AttributionResult second = calculator.decompose(
                777.0, 700.0, new BigDecimal("9.3913"), new BigDecimal("9.5"), 0.003);

        AttributionResult total = calculator.aggregate(List.of(first, second));

        assertThat(total.asset().krw()
                + total.fx().krw()
                + total.interaction().krw()
                + total.cost().krw())
                .isEqualTo(total.currentKrw() - total.costBasisKrw());
    }

    @Test
    @DisplayName("합산 입력이 비어 있으면 예외")
    void 합산_입력이_비면_예외() {
        assertThatThrownBy(() -> calculator.aggregate(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("합산할 분해 결과가 없습니다");
    }

    @Test
    @DisplayName("합산 입력이 null 이면 예외")
    void 합산_입력이_null_이면_예외() {
        assertThatThrownBy(() -> calculator.aggregate(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("매입원가가 원 단위로 0이면 기여도는 0으로 떨어진다 (0 나누기 방지)")
    void 매입원가_0이면_기여도는_0이다() {
        AttributionResult result = calculator.decompose(
                1e-9, 2e-9, new BigDecimal("0.0001"), new BigDecimal("0.0002"), 0.0);

        assertThat(result.costBasisKrw()).isZero();
        assertThat(result.totalReturn()).isZero();
        assertThat(result.asset().contributionPp()).isZero();

        AttributionResult total = calculator.aggregate(List.of(result));
        assertThat(total.costBasisKrw()).isZero();
        assertThat(total.totalReturn()).isZero();
        assertThat(total.asset().contributionPp()).isZero();
    }

    // --- 입력 검증 ---

    @Test
    @DisplayName("매입 시점 자산 평가액이 0 이하면 예외")
    void 매입_평가액_0이하_예외() {
        assertThatThrownBy(() -> calculator.decompose(
                0.0, 1_000.0, new BigDecimal("1000"), new BigDecimal("1000"), 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("매입 시점 자산 평가액");
    }

    @Test
    @DisplayName("현재 자산 평가액이 음수면 예외")
    void 현재_평가액_음수_예외() {
        assertThatThrownBy(() -> calculator.decompose(
                1_000.0, -1.0, new BigDecimal("1000"), new BigDecimal("1000"), 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("현재 자산 평가액");
    }

    @Test
    @DisplayName("매입 환율이 0 이하면 예외")
    void 매입_환율_0이하_예외() {
        assertThatThrownBy(() -> calculator.decompose(
                1_000.0, 1_000.0, BigDecimal.ZERO, new BigDecimal("1000"), 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("매입 시점 환율");
    }

    @Test
    @DisplayName("현재 환율이 0 이하면 예외")
    void 현재_환율_0이하_예외() {
        assertThatThrownBy(() -> calculator.decompose(
                1_000.0, 1_000.0, new BigDecimal("1000"), BigDecimal.ZERO, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("현재 환율");
    }

    @Test
    @DisplayName("비용 비율이 0~1 범위를 벗어나면 예외")
    void 비용_비율_범위_예외() {
        assertThatThrownBy(() -> calculator.decompose(
                1_000.0, 1_000.0, new BigDecimal("1000"), new BigDecimal("1000"), -0.01))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비용 비율");
        assertThatThrownBy(() -> calculator.decompose(
                1_000.0, 1_000.0, new BigDecimal("1000"), new BigDecimal("1000"), 1.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비용 비율");
    }

    @Test
    @DisplayName("환율이 null 이면 예외")
    void 환율_null_예외() {
        assertThatThrownBy(() -> calculator.decompose(
                1_000.0, 1_000.0, null, new BigDecimal("1000"), 0.0))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("매입 시점 환율");
        assertThatThrownBy(() -> calculator.decompose(
                1_000.0, 1_000.0, new BigDecimal("1000"), null, 0.0))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("현재 환율");
    }
}
