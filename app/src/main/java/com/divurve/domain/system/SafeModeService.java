package com.divurve.domain.system;

import com.divurve.common.architecture.UseCase;
import com.divurve.domain.goal.GoalRepository;
import com.divurve.domain.goal.entity.Goal;
import com.divurve.domain.holding.DepositRepository;
import com.divurve.domain.holding.HoldingRepository;
import com.divurve.domain.plan.PlanRepository;
import com.divurve.domain.plan.entity.Plan;
import com.divurve.domain.port.FxRateProvider;
import com.divurve.engine.safemode.SafeModeCheckResult;
import com.divurve.engine.safemode.SafeModeEvaluation;
import com.divurve.engine.safemode.SafeModeEvaluator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 안전모드 평가 유스케이스 (명세 3.9, FR-SF-01~05).
 * 현재 사용자의 목표·보유 자산·계획 데이터를 조회하여 engine 의 {@link SafeModeEvaluator}에 전달하고,
 * 안전모드 발동 여부 및 상태 라벨을 결정한다.
 *
 * <p>정책: 외부 데이터가 부분적으로 부재해도 평가를 중단하지 않는다 (null 허용).
 * 평가 불가능한 조건은 자동으로 통과 처리되어 안전모드 발동에 기여하지 않는다.
 */
@UseCase
public class SafeModeService {

    private final GoalRepository goalRepository;
    private final PlanRepository planRepository;
    private final HoldingRepository holdingRepository;
    private final DepositRepository depositRepository;
    private final FxRateProvider fxRateProvider;
    private final SafeModeEvaluator safeModeEvaluator;

    public SafeModeService(
            GoalRepository goalRepository,
            PlanRepository planRepository,
            HoldingRepository holdingRepository,
            DepositRepository depositRepository,
            FxRateProvider fxRateProvider,
            SafeModeEvaluator safeModeEvaluator) {
        this.goalRepository = Objects.requireNonNull(goalRepository);
        this.planRepository = Objects.requireNonNull(planRepository);
        this.holdingRepository = Objects.requireNonNull(holdingRepository);
        this.depositRepository = Objects.requireNonNull(depositRepository);
        this.fxRateProvider = Objects.requireNonNull(fxRateProvider);
        this.safeModeEvaluator = Objects.requireNonNull(safeModeEvaluator);
    }

    /**
     * 사용자의 현재 안전모드 상태를 평가한다.
     *
     * @param userId 평가 대상 사용자 ID
     * @return 안전모드 평가 결과
     */
    @Transactional(readOnly = true)
    public SafeModeCheckResult evaluateSafeMode(UUID userId) {
        Objects.requireNonNull(userId, "userId는 null일 수 없습니다.");

        SafeModeEvaluation evaluation = buildEvaluation(userId);
        return safeModeEvaluator.evaluate(evaluation);
    }

    /**
     * 사용자 데이터로부터 engine 평가 인자를 구성한다.
     * 데이터 부재 시에도 평가를 진행하며, null을 허용한다.
     */
    private SafeModeEvaluation buildEvaluation(UUID userId) {
        List<Goal> userGoals = goalRepository.findByOwner_Id(userId);

        LocalDate lastUpdateDate = resolveLastUpdateDate(userGoals);
        BigDecimal primaryRate = resolvePrimaryRate();
        BigDecimal secondaryRate = resolveSecondaryRate();
        BigDecimal currentRate = resolveCurrentRate();
        BigDecimal movingAverageRate = resolveMovingAverageRate();
        BigDecimal currentVolatility = resolveCurrentVolatility(userGoals);
        BigDecimal historicalAverageVolatility = resolveHistoricalAverageVolatility();
        Long daysUntilDeadline = resolveDaysUntilDeadline(userGoals);
        BigDecimal uncoveredRatio = resolveUncoveredRatio(userGoals);
        Long consecutiveSkipCount = resolveConsecutiveSkipCount(userGoals);

        return SafeModeEvaluation.builder()
            .lastUpdateDate(lastUpdateDate)
            .primaryRate(primaryRate)
            .secondaryRate(secondaryRate)
            .currentRate(currentRate)
            .movingAverageRate(movingAverageRate)
            .currentVolatility(currentVolatility)
            .historicalAverageVolatility(historicalAverageVolatility)
            .daysUntilDeadline(daysUntilDeadline)
            .uncoveredRatio(uncoveredRatio)
            .consecutiveSkipCount(consecutiveSkipCount)
            .build();
    }

    /**
     * 마지막 데이터 업데이트일을 결정한다.
     * 현재는 오늘 날짜로 기본값을 반환 (향후 통합 데이터 업데이트 시점으로 대체 예정).
     */
    private LocalDate resolveLastUpdateDate(List<Goal> goals) {
        return LocalDate.now();
    }

    /**
     * 주요 출처 환율(ECOS)을 조회한다.
     */
    private BigDecimal resolvePrimaryRate() {
        try {
            return fxRateProvider.fetchLatest("USD_KRW").rate();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 보조 출처 환율을 조회한다.
     * 현재는 미구현 (향후 AlphaVantage 등 추가 시 구현).
     */
    private BigDecimal resolveSecondaryRate() {
        return null;
    }

    /**
     * 현재 환율(가장 최신 조회값)을 반환한다.
     */
    private BigDecimal resolveCurrentRate() {
        return resolvePrimaryRate();
    }

    /**
     * 20일 이동평균 환율을 계산한다.
     * 현재는 미구현 (향후 과거 환율 데이터 기반으로 계산).
     */
    private BigDecimal resolveMovingAverageRate() {
        return null;
    }

    /**
     * 현재 변동성을 계산한다.
     * 현재는 미구현 (향후 engine/volatility 모듈 활용).
     */
    private BigDecimal resolveCurrentVolatility(List<Goal> goals) {
        return null;
    }

    /**
     * 역사 평균 변동성을 조회한다.
     * 현재는 미구현 (향후 마스터 데이터 조회).
     */
    private BigDecimal resolveHistoricalAverageVolatility() {
        return null;
    }

    /**
     * 가장 임박한 목표의 마감까지 남은 일수를 계산한다.
     */
    private Long resolveDaysUntilDeadline(List<Goal> goals) {
        return goals.stream()
            .map(Goal::getTargetDate)
            .filter(java.util.Objects::nonNull)
            .map(date -> java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), date))
            .min(Long::compareTo)
            .orElse(null);
    }

    /**
     * 마감 14일 이내의 목표 미확보율을 계산한다.
     * 현재는 미구현 (향후 목표별 집중도·보유자산 기반으로 계산).
     */
    private BigDecimal resolveUncoveredRatio(List<Goal> goals) {
        return null;
    }

    /**
     * 연속 환전 기회 불이행 횟수를 계산한다.
     * 현재는 미구현 (향후 실행 기록 기반으로 계산).
     */
    private Long resolveConsecutiveSkipCount(List<Goal> goals) {
        return null;
    }
}
