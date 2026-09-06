package com.divurve.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import com.divurve.common.exception.ForbiddenException;
import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.goal.GoalRepository;
import com.divurve.domain.goal.entity.Goal;
import com.divurve.domain.plan.PlanPreviewService.PlanPreviewInfo;
import com.divurve.domain.user.entity.User;
import com.divurve.engine.bucket.BucketAllocator;
import com.divurve.engine.concentration.ConcentrationCalculator;
import com.divurve.engine.cost.CostCalculator;
import com.divurve.engine.simulate.MonteCarloSimulator;
import com.divurve.engine.split.SplitVarianceReducer;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link PlanPreviewService} — 계획 미리보기(이슈 #18, 명세 3.1) 조합 로직 검증.
 *
 * <p>engine 계산기는 순수 함수이므로 목이 아닌 실제 구현을 쓴다(값 자체의 정합성은 engine 모듈
 * 테스트가 담당하고, 여기서는 "서비스가 어떤 입력으로 무엇을 조합해 내보내는가"를 검증한다).
 * 유일한 외부 의존인 {@link GoalRepository} 만 목으로 대체한다.
 */
@ExtendWith(MockitoExtension.class)
class PlanPreviewServiceTest {

    private static final Offset<Double> EPS = Offset.offset(1e-9);

    /** 월 예산 1,000,000 KRW 이 되도록 하는 주간 예산 (서비스는 weekly × 4 로 근사한다). */
    private static final long WEEKLY_BUDGET_KRW = 250_000L;

    @Mock
    private GoalRepository goalRepository;

    private final BucketAllocator bucketAllocator = new BucketAllocator();
    private final SplitVarianceReducer splitVarianceReducer = new SplitVarianceReducer();
    private final CostCalculator costCalculator = new CostCalculator();
    private final MonteCarloSimulator monteCarloSimulator = new MonteCarloSimulator();
    private final ConcentrationCalculator concentrationCalculator = new ConcentrationCalculator();

    private final UUID goalId = UUID.randomUUID();

    private PlanPreviewService service() {
        return new PlanPreviewService(goalRepository, bucketAllocator, splitVarianceReducer,
                costCalculator, monteCarloSimulator, concentrationCalculator);
    }

    private Goal.Builder goalBuilder(String purpose) {
        return Goal.builder(User.create("me@divurve.com", "나", null, null), "미국 ETF 적립", "RECURRING",
                        purpose, "USD")
                .targetAmount(10_000_000)
                .targetDate(LocalDate.now().plusDays(365))
                .budgetAmount(1_000_000)
                .status("ACTIVE");
    }

    private void givenGoal(Goal goal) {
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
    }

    // ---------------------------------------------------------------- 정상 경로

    @Test
    void 안전비율과_분할횟수를_생략하면_권장값_070_과_4회로_미리보기를_만든다() {
        LocalDate today = LocalDate.now();
        givenGoal(goalBuilder("TRAVEL").recurInterval("MONTHLY").build());

        PlanPreviewInfo info = service().generatePreview(goalId.toString(), WEEKLY_BUDGET_KRW, null, null);

        // 목표 요약은 Goal 을 그대로 옮긴다
        assertThat(info.goal().kind()).isEqualTo("RECURRING");
        assertThat(info.goal().purpose()).isEqualTo("TRAVEL");
        assertThat(info.goal().currencyCode()).isEqualTo("USD");

        // 버킷: 월예산 1,000,000 × 0.70 / 0.30, floor 는 TRAVEL 하한 0.70
        assertThat(info.buckets().safeRatio()).isEqualTo(0.70);
        assertThat(info.buckets().safe()).isEqualTo(700_000.0);
        assertThat(info.buckets().opportunity()).isEqualTo(300_000.0);
        assertThat(info.buckets().floor()).isEqualTo(0.70);

        // 분할: MONTHLY → 30일 간격, g(4) = sqrt(45/96)
        assertThat(info.split().count()).isEqualTo(4);
        assertThat(info.split().intervalDays()).isEqualTo(30);
        assertThat(info.split().gFactor()).isCloseTo(Math.sqrt(45.0 / 96.0), EPS);
        assertThat(info.split().nextStepDelta().sigmaGain())
                .isCloseTo(splitVarianceReducer.sigmaGain(5), EPS);
        assertThat(info.split().nextStepDelta().feeIncreaseKrw()).isEqualTo(3_000L);

        // 회차: 안전 버킷을 4등분, 오늘부터 30일 간격
        assertThat(info.steps()).hasSize(4);
        assertThat(info.steps()).extracting(PlanPreviewInfo.Step::seq).containsExactly(1, 2, 3, 4);
        assertThat(info.steps()).extracting(PlanPreviewInfo.Step::scheduledDate).containsExactly(
                today.toString(),
                today.plusDays(30).toString(),
                today.plusDays(60).toString(),
                today.plusDays(90).toString());
        assertThat(info.steps()).allSatisfy(step -> {
            assertThat(step.amount()).isEqualTo(175_000.0);
            assertThat(step.krwEstimate()).isEqualTo(175_000L);
            assertThat(step.executedAmount()).isZero();
            assertThat(step.status()).isEqualTo("PENDING");
        });

        // 기회 버킷은 단일 대기 물량이며 미실행 시 목표일에 안전 버킷으로 편입된다
        assertThat(info.opportunity().amount()).isEqualTo(300_000.0);
        assertThat(info.opportunity().triggerRate()).isEqualTo(1.05);
        assertThat(info.opportunity().finalSafeDate()).isEqualTo(today.plusDays(365).toString());

        // 비용: 스프레드 700,000 × 0.0035 = 2,450 + 고정 4 × 3,000 = 12,000
        assertThat(info.metrics().fee().spreadKrw()).isEqualTo(2_450L);
        assertThat(info.metrics().fee().fixedKrw()).isEqualTo(12_000L);
        assertThat(info.metrics().fee().totalKrw()).isEqualTo(14_450L);
        assertThat(info.metrics().hero()).isEqualTo("achieveProb");

        // 전략 비교 3행: 일시환전(1회) / 현재 분할(4회) / 권장(8회) — 회차 수만큼 고정수수료가 붙는다
        assertThat(info.comparison())
                .extracting(PlanPreviewInfo.Comparison::strategy, PlanPreviewInfo.Comparison::splitCount,
                        PlanPreviewInfo.Comparison::feeKrw)
                .containsExactly(
                        tuple("LUMP_SUM", 1, 5_450L),
                        tuple("EQUAL_SPLIT", 4, 14_450L),
                        tuple("RECOMMENDED", 8, 26_450L));

        // 집중도·경고는 아직 계산 입력이 없어 중립/빈 값으로 나간다
        assertThat(info.concentration().before()).isEmpty();
        assertThat(info.concentration().after()).isEmpty();
        assertThat(info.concentration().threshold()).isEqualTo(0.02);
        assertThat(info.concentration().verdict()).isEqualTo("neutral");
        assertThat(info.warnings()).isEmpty();

        // 목표일까지 365일 → 12개월
        assertThat(info.weeks()).isEqualTo(12);
        assertThat(info.metrics().achieveProb()).isBetween(0.0, 1.0);
        assertThat(info.metrics().achieveProbOnce()).isEqualTo(info.metrics().achieveProb());
    }

    @Test
    void 하한을_만족하는_안전비율을_주면_그대로_사용한다() {
        givenGoal(goalBuilder("TUITION").recurInterval("MONTHLY").build());

        PlanPreviewInfo info = service().generatePreview(goalId.toString(), WEEKLY_BUDGET_KRW, 0.95, 2);

        assertThat(info.buckets().safeRatio()).isEqualTo(0.95);
        assertThat(info.buckets().safe()).isEqualTo(950_000.0);
        assertThat(info.buckets().opportunity()).isEqualTo(50_000.0);
        assertThat(info.buckets().floor()).isEqualTo(0.90);
        assertThat(info.steps()).hasSize(2);
        assertThat(info.steps()).allSatisfy(step -> assertThat(step.amount()).isEqualTo(475_000.0));
    }

    @Test
    void 분할을_최대치_52회로_잡으면_다음_회차_시그마_이득은_0_이다() {
        givenGoal(goalBuilder("TRAVEL").recurInterval("WEEKLY").build());

        PlanPreviewInfo info = service().generatePreview(goalId.toString(), WEEKLY_BUDGET_KRW, null, 52);

        assertThat(info.split().count()).isEqualTo(52);
        assertThat(info.steps()).hasSize(52);
        assertThat(info.split().nextStepDelta().sigmaGain()).isZero();
    }

    @Test
    void 분할이_최대치_미만이면_다음_회차_시그마_이득이_양수로_계산된다() {
        givenGoal(goalBuilder("TRAVEL").recurInterval("WEEKLY").build());

        PlanPreviewInfo info = service().generatePreview(goalId.toString(), WEEKLY_BUDGET_KRW, null, 51);

        assertThat(info.split().nextStepDelta().sigmaGain())
                .isCloseTo(splitVarianceReducer.sigmaGain(52), EPS)
                .isGreaterThan(0.0);
    }

    // ------------------------------------------------------- 회차 간격 (recur_interval)

    @ParameterizedTest(name = "recur_interval={0} → {1}일 간격")
    @CsvSource({
            "WEEKLY, 7",
            "BIWEEKLY, 14",
            "MONTHLY, 30",
            "QUARTERLY, 90",
    })
    void 정의된_반복주기는_고정_간격으로_변환된다(String recurInterval, int expectedIntervalDays) {
        givenGoal(goalBuilder("TRAVEL").recurInterval(recurInterval).build());

        PlanPreviewInfo info = service().generatePreview(goalId.toString(), WEEKLY_BUDGET_KRW, null, 4);

        assertThat(info.split().intervalDays()).isEqualTo(expectedIntervalDays);
    }

    @Test
    void 반복주기가_null_이면_1년을_분할횟수로_나눈_간격을_쓴다() {
        givenGoal(goalBuilder("TRAVEL").recurInterval(null).build());

        PlanPreviewInfo info = service().generatePreview(goalId.toString(), WEEKLY_BUDGET_KRW, null, 4);

        assertThat(info.split().intervalDays()).isEqualTo(91); // 365 / 4
    }

    @Test
    void 반복주기가_공백_문자열이면_1년을_분할횟수로_나눈_간격을_쓴다() {
        givenGoal(goalBuilder("TRAVEL").recurInterval("   ").build());

        PlanPreviewInfo info = service().generatePreview(goalId.toString(), WEEKLY_BUDGET_KRW, null, 5);

        assertThat(info.split().intervalDays()).isEqualTo(73); // 365 / 5
    }

    @Test
    void 알_수_없는_반복주기도_1년_분할_간격으로_대체된다() {
        givenGoal(goalBuilder("TRAVEL").recurInterval("YEARLY").build());

        PlanPreviewInfo info = service().generatePreview(goalId.toString(), WEEKLY_BUDGET_KRW, null, 10);

        assertThat(info.split().intervalDays()).isEqualTo(36); // 365 / 10
    }

    // --------------------------------------------------------------- 기간 / 달성 확률

    @Test
    void 목표일이_없으면_기간을_12개월로_가정한다() {
        givenGoal(goalBuilder("TRAVEL").recurInterval("MONTHLY").targetDate(null).build());

        PlanPreviewInfo info = service().generatePreview(goalId.toString(), WEEKLY_BUDGET_KRW, null, 4);

        assertThat(info.weeks()).isEqualTo(12);
        assertThat(info.opportunity().finalSafeDate()).isNull();
    }

    @Test
    void 목표액이_예산에_비해_사소하면_달성확률은_1_이다() {
        givenGoal(goalBuilder("TRAVEL").recurInterval("MONTHLY").targetAmount(1.0).build());

        PlanPreviewInfo info = service().generatePreview(goalId.toString(), WEEKLY_BUDGET_KRW, null, 4);

        assertThat(info.metrics().achieveProb()).isEqualTo(1.0);
    }

    @Test
    void 목표액이_예산으로_도달_불가능하면_달성확률은_0_이다() {
        givenGoal(goalBuilder("TRAVEL").recurInterval("MONTHLY").targetAmount(1.0e15).build());

        PlanPreviewInfo info = service().generatePreview(goalId.toString(), WEEKLY_BUDGET_KRW, null, 4);

        assertThat(info.metrics().achieveProb()).isZero();
    }

    // ------------------------------------------------------------------- 실패 경로

    @Test
    void 목표_ID_가_UUID_형식이_아니면_404_를_던진다() {
        assertThatThrownBy(() -> service().generatePreview("not-a-uuid", WEEKLY_BUDGET_KRW, null, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("not-a-uuid");
    }

    @Test
    void 목표가_존재하지_않으면_404_를_던진다() {
        when(goalRepository.findById(goalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().generatePreview(goalId.toString(), WEEKLY_BUDGET_KRW, null, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(goalId.toString());
    }

    @Test
    void 투기성_목적의_목표는_403_으로_차단된다() {
        givenGoal(goalBuilder("TRAVEL").recurInterval("MONTHLY").isSpeculative(true).build());

        assertThatThrownBy(() -> service().generatePreview(goalId.toString(), WEEKLY_BUDGET_KRW, null, null))
                .isInstanceOfSatisfying(ForbiddenException.class,
                        e -> assertThat(e.getCode()).isEqualTo("FORBIDDEN"))
                .hasMessageContaining("투기성");
    }

    @Test
    void 목적_하한보다_낮은_안전비율은_거부된다() {
        givenGoal(goalBuilder("TUITION").recurInterval("MONTHLY").build());

        assertThatThrownBy(() -> service().generatePreview(goalId.toString(), WEEKLY_BUDGET_KRW, 0.50, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0.9");
    }

    @Test
    void 분할횟수가_1_미만이면_거부된다() {
        givenGoal(goalBuilder("TRAVEL").recurInterval("MONTHLY").build());

        assertThatThrownBy(() -> service().generatePreview(goalId.toString(), WEEKLY_BUDGET_KRW, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1~52");
    }

    @Test
    void 분할횟수가_52_를_넘으면_거부된다() {
        givenGoal(goalBuilder("TRAVEL").recurInterval("MONTHLY").build());

        assertThatThrownBy(() -> service().generatePreview(goalId.toString(), WEEKLY_BUDGET_KRW, null, 53))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1~52");
    }

    // ---------------------------------------------------------------- 생성자 방어

    @Test
    void 협력자가_null_이면_생성_시점에_실패한다() {
        assertThatThrownBy(() -> new PlanPreviewService(null, bucketAllocator, splitVarianceReducer,
                costCalculator, monteCarloSimulator, concentrationCalculator))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("GoalRepository");

        assertThatThrownBy(() -> new PlanPreviewService(goalRepository, null, splitVarianceReducer,
                costCalculator, monteCarloSimulator, concentrationCalculator))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("BucketAllocator");

        assertThatThrownBy(() -> new PlanPreviewService(goalRepository, bucketAllocator, null,
                costCalculator, monteCarloSimulator, concentrationCalculator))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("SplitVarianceReducer");

        assertThatThrownBy(() -> new PlanPreviewService(goalRepository, bucketAllocator, splitVarianceReducer,
                null, monteCarloSimulator, concentrationCalculator))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("CostCalculator");

        assertThatThrownBy(() -> new PlanPreviewService(goalRepository, bucketAllocator, splitVarianceReducer,
                costCalculator, null, concentrationCalculator))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("MonteCarloSimulator");

        assertThatThrownBy(() -> new PlanPreviewService(goalRepository, bucketAllocator, splitVarianceReducer,
                costCalculator, monteCarloSimulator, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ConcentrationCalculator");
    }

    // ------------------------------------------------------------ 응답 계약용 레코드

    @Test
    void 경고_레코드는_코드와_메시지를_그대로_담는다() {
        PlanPreviewInfo.Warning warning =
                new PlanPreviewInfo.Warning("BUDGET_SHORTFALL", "예산이 목표에 부족합니다");

        assertThat(warning.code()).isEqualTo("BUDGET_SHORTFALL");
        assertThat(warning.message()).isEqualTo("예산이 목표에 부족합니다");
    }
}
