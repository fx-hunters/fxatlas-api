package com.divurve.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.divurve.domain.auth.DemoSampleData.DepositSample;
import com.divurve.domain.auth.DemoSampleData.GoalSample;
import com.divurve.domain.auth.DemoSampleData.HoldingSample;
import com.divurve.domain.auth.DemoSampleData.KrwAssetSample;
import com.divurve.engine.riskprofile.RiskAssessment;
import com.divurve.engine.riskprofile.RiskProfileScorer;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link DemoSampleData} 단위 테스트 — <b>샘플 값이 시연 시나리오를 만족하는지</b> 검증한다.
 *
 * <p>여기서 지키는 것은 개별 숫자가 아니라 그 숫자들이 만들어내는 상황이다(이슈 #78).
 * 시연 데이터를 손볼 때 값이 바뀌는 건 자유지만, 아래 성질이 깨지면 데모 화면이 의미를 잃는다.
 */
class DemoSampleDataTest {

    /** {@code krw_assets.kind} CHECK 제약 허용값 (V10 마이그레이션). */
    private static final Set<String> ALLOWED_KRW_ASSET_KINDS =
            Set.of("cash", "deposit", "domestic_equity", "other");

    private final RiskProfileScorer riskProfileScorer = new RiskProfileScorer();

    @Test
    void 보유_외화가_목표_금액에_못_미친다() {
        // 초과 달성 상태면 "얼마나 더 모아야 하는지"를 보여주는 플랜·실행 화면이 데모에서 의미를 잃는다.
        double held = totalFxAmount();
        double target = DemoSampleData.GOAL.targetAmount();

        assertThat(held)
                .as("보유 외화 %.2f 는 목표 %.2f 보다 적어야 한다", held, target)
                .isLessThan(target);
    }

    @Test
    void 한_종목이_균형항로형_집중도_기준선을_넘는다() {
        // X-ray 집중도 경고가 실제로 뜨는 구성이어야 데모에서 보여줄 것이 있다.
        double concentrationThreshold = balancedAssessment().concentrationThreshold();
        double topWeight = DemoSampleData.HOLDINGS.stream()
                .mapToDouble(DemoSampleDataTest::valuation)
                .max()
                .orElseThrow() / totalFxAmount();

        assertThat(topWeight)
                .as("최대 비중 %.4f 는 기준선 %.4f 를 넘어야 한다", topWeight, concentrationThreshold)
                .isGreaterThan(concentrationThreshold);
    }

    @Test
    void 원화_자산이_있고_kind_가_허용값이다() {
        // 원화 자산이 없으면 외화 비중의 분모가 외화뿐이라 항상 100% 로 보인다.
        assertThat(DemoSampleData.KRW_ASSETS).isNotEmpty();
        assertThat(DemoSampleData.KRW_ASSETS)
                .allSatisfy(sample -> {
                    assertThat(sample.kind()).isIn(ALLOWED_KRW_ASSET_KINDS);
                    assertThat(sample.label()).isNotBlank();
                    assertThat(sample.amountKrw()).isPositive();
                });
    }

    @Test
    void 진단_응답은_Q1_Q3_를_모두_채워_균형항로형을_산출한다() {
        // 유형을 직접 시드하지 않고 응답만 두는 것이 핵심이다 — 유형은 Scorer 가 만든다.
        assertThat(DemoSampleData.RISK_PROFILE_ANSWERS.keySet())
                .containsExactlyInAnyOrderElementsOf(RiskProfileScorer.SIMPLE_QUESTIONS);
        assertThat(balancedAssessment().riskType()).isEqualTo(RiskProfileScorer.BALANCED);
    }

    @Test
    void 매입일은_기준일보다_과거이고_목표일은_미래다() {
        // 상대 날짜라 언제 시연해도 이 관계가 유지된다.
        LocalDate today = LocalDate.of(2026, 9, 7);

        assertThat(DemoSampleData.HOLDINGS)
                .allSatisfy(sample -> assertThat(sample.purchasedOn(today)).isBefore(today));
        assertThat(DemoSampleData.DEPOSITS)
                .allSatisfy(sample -> assertThat(sample.purchasedOn(today)).isBefore(today));
        assertThat(DemoSampleData.GOAL.targetDate(today)).isAfter(today);
    }

    @Test
    void 보유_종목과_예금은_매입_환율을_갖는다() {
        assertThat(DemoSampleData.HOLDINGS)
                .allSatisfy(sample -> assertThat(sample.purchaseFxRateKrw()).isPositive());
        assertThat(DemoSampleData.DEPOSITS)
                .allSatisfy(sample -> assertThat(sample.purchaseFxRateKrw()).isPositive());
    }

    @Test
    void 데모_유저_식별_상수는_실제로_수신할_수_없는_주소를_만든다() {
        assertThat(DemoSampleData.EMAIL_PREFIX).isEqualTo("demo-");
        assertThat(DemoSampleData.EMAIL_DOMAIN).endsWith(".local");
        assertThat(DemoSampleData.USER_NAME).isNotBlank();
        assertThat(DemoSampleData.PURCHASE_FX_RATE_SOURCE).isEqualTo("manual");
    }

    @Test
    void 목표는_정기_예산까지_채워진_활성_목표다() {
        GoalSample goal = DemoSampleData.GOAL;

        assertThat(goal.name()).isNotBlank();
        assertThat(goal.kind()).isNotBlank();
        assertThat(goal.purpose()).isNotBlank();
        assertThat(goal.currencyCode()).isEqualTo("USD");
        assertThat(goal.budgetAmount()).isPositive();
        assertThat(goal.budgetCurrencyCode()).isEqualTo("KRW");
        assertThat(goal.budgetPeriod()).isEqualTo("monthly");
        assertThat(goal.isSpeculative()).isFalse();
        assertThat(goal.status()).isEqualTo("active");
    }

    @Test
    void 보유_종목은_개별주와_지수_ETF_를_함께_담는다() {
        // 집중도 대비가 드러나려면 한 종목에 쏠린 쪽과 분산된 쪽이 함께 있어야 한다.
        assertThat(DemoSampleData.HOLDINGS).hasSizeGreaterThanOrEqualTo(2);
        assertThat(DemoSampleData.HOLDINGS)
                .allSatisfy(sample -> {
                    assertThat(sample.ticker()).isNotBlank();
                    assertThat(sample.currencyCode()).isEqualTo("USD");
                    assertThat(sample.quantity()).isPositive();
                    assertThat(sample.avgPrice()).isPositive();
                });
        assertThat(DemoSampleData.DEPOSITS)
                .allSatisfy(sample -> {
                    assertThat(sample.currencyCode()).isEqualTo("USD");
                    assertThat(sample.amount()).isPositive();
                });
    }

    /** 간편 진단 응답으로 산출된 결과. 이 조합은 Q1~Q3 이 모두 차 있어 항상 값이 나온다. */
    private RiskAssessment balancedAssessment() {
        Optional<RiskAssessment> assessment = riskProfileScorer.assess(DemoSampleData.RISK_PROFILE_ANSWERS);
        assertThat(assessment).isPresent();
        return assessment.orElseThrow();
    }

    /** 보유 외화 합계 — 종목 평가액 + 외화 예금. 목표 통화(USD)와 같은 통화로만 구성돼 있다. */
    private static double totalFxAmount() {
        double holdings = DemoSampleData.HOLDINGS.stream()
                .mapToDouble(DemoSampleDataTest::valuation)
                .sum();
        double deposits = DemoSampleData.DEPOSITS.stream()
                .mapToDouble(sample -> sample.amount().doubleValue())
                .sum();
        return holdings + deposits;
    }

    private static double valuation(HoldingSample sample) {
        return sample.quantity() * sample.avgPrice();
    }

    /** 컴파일 경고 없이 타입 파라미터를 고정하기 위한 접근자 — 목록 타입이 바뀌면 여기서 먼저 깨진다. */
    @Test
    void 샘플_목록_타입이_계약대로다() {
        List<HoldingSample> holdings = DemoSampleData.HOLDINGS;
        List<DepositSample> deposits = DemoSampleData.DEPOSITS;
        List<KrwAssetSample> krwAssets = DemoSampleData.KRW_ASSETS;

        assertThat(holdings).isNotEmpty();
        assertThat(deposits).isNotEmpty();
        assertThat(krwAssets).isNotEmpty();
    }
}
