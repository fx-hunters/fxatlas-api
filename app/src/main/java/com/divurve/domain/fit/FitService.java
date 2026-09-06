package com.divurve.domain.fit;

import com.divurve.common.architecture.UseCase;
import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.holding.DepositRepository;
import com.divurve.domain.holding.HoldingRepository;
import com.divurve.domain.holding.entity.Deposit;
import com.divurve.domain.holding.entity.Holding;
import com.divurve.domain.port.FxRateProvider;
import com.divurve.domain.port.RateSnapshot;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import com.divurve.engine.concentration.ConcentrationCalculator;
import com.divurve.engine.diversification.DiversificationSimulator;
import com.divurve.engine.weight.WeightCalculator;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fit(적합성) 유스케이스 (이슈 #14, FR-FT-01/02).
 * 포트폴리오의 집중도를 진단하고 분산효과를 시뮬레이션한다.
 * 읽기만 수행하고, 계산 로직은 모두 engine에 위임한다(SRP).
 */
@UseCase
public class FitService {

    private final HoldingRepository holdingRepository;
    private final DepositRepository depositRepository;
    private final UserRepository userRepository;
    private final FxRateProvider fxRateProvider;
    private final WeightCalculator weightCalculator;
    private final ConcentrationCalculator concentrationCalculator;
    private final DiversificationSimulator diversificationSimulator;

    public FitService(
            HoldingRepository holdingRepository,
            DepositRepository depositRepository,
            UserRepository userRepository,
            FxRateProvider fxRateProvider,
            WeightCalculator weightCalculator,
            ConcentrationCalculator concentrationCalculator,
            DiversificationSimulator diversificationSimulator) {
        this.holdingRepository = Objects.requireNonNull(holdingRepository, "holdingRepository is null");
        this.depositRepository = Objects.requireNonNull(depositRepository, "depositRepository is null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository is null");
        this.fxRateProvider = Objects.requireNonNull(fxRateProvider, "fxRateProvider is null");
        this.weightCalculator = Objects.requireNonNull(weightCalculator, "weightCalculator is null");
        this.concentrationCalculator = Objects.requireNonNull(concentrationCalculator, "concentrationCalculator is null");
        this.diversificationSimulator = Objects.requireNonNull(diversificationSimulator, "diversificationSimulator is null");
    }

    /**
     * 포트폴리오의 집중도를 진단한다.
     *
     * @param userId 사용자 ID (NFR-SE-03)
     * @return 집중도 진단 결과
     * @throws NotFoundException 사용자를 찾을 수 없는 경우
     */
    @Transactional(readOnly = true)
    public ConcentrationDiagnosis diagnoseConcentration(UUID userId) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));

        List<Holding> holdings = holdingRepository.findByOwner_Id(userId);
        List<Deposit> deposits = depositRepository.findByOwner_Id(userId);

        // 환율 조회
        Map<String, RateSnapshot> rates = fetchLatestRates(holdings, deposits);

        // 통화별 자산 계산 (원화)
        Map<String, Long> currencyToAssetKrw = calculateCurrencyAssets(holdings, deposits, rates);

        // 집중도 진단 (기본 임계값 35%)
        ConcentrationCalculator.ConcentrationResult result =
                concentrationCalculator.diagnose(currencyToAssetKrw, 0.35);

        return new ConcentrationDiagnosis(result, owner.getId());
    }

    /**
     * 특정 통화의 비중 조정 시 분산효과를 시뮬레이션한다.
     *
     * @param userId 사용자 ID
     * @param targetCurrency 조정 대상 통화
     * @param deltaShare 비중 변화량
     * @return 시뮬레이션 결과
     */
    @Transactional(readOnly = true)
    public DiversificationSimulation simulateDiversification(
            UUID userId,
            String targetCurrency,
            double deltaShare) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));

        List<Holding> holdings = holdingRepository.findByOwner_Id(userId);
        List<Deposit> deposits = depositRepository.findByOwner_Id(userId);

        // 환율 조회
        Map<String, RateSnapshot> rates = fetchLatestRates(holdings, deposits);

        // 통화별 자산 계산 (원화)
        Map<String, Long> currencyToAssetKrw = calculateCurrencyAssets(holdings, deposits, rates);

        // 비중 계산
        long fxAssetKrw = currencyToAssetKrw.values().stream()
                .mapToLong(Long::longValue)
                .sum();

        if (fxAssetKrw == 0L) {
            throw new IllegalArgumentException("외화자산이 없어 시뮬레이션할 수 없습니다.");
        }

        Map<String, Double> currentShare = new HashMap<>();
        for (String currency : currencyToAssetKrw.keySet()) {
            double share = (double) currencyToAssetKrw.get(currency) / fxAssetKrw;
            currentShare.put(currency, share);
        }

        // 변동성 조회 (임시: 기본값 사용)
        Map<String, Double> volatility = buildDefaultVolatility(currentShare.keySet());

        // 상관계수 조회 (임시: 기본값 사용)
        Map<String, Double> correlation = buildDefaultCorrelation(currentShare.keySet());

        // 시뮬레이션 수행
        DiversificationSimulator.SimulationResult result = diversificationSimulator.simulate(
                currentShare,
                volatility,
                correlation,
                targetCurrency,
                deltaShare
        );

        // 집중도 진단 (조정 후)
        Map<String, Long> adjustedAssetKrw = new HashMap<>();
        for (String currency : result.adjustedShare().keySet()) {
            long assetKrw = Math.round(result.adjustedShare().get(currency) * fxAssetKrw);
            adjustedAssetKrw.put(currency, assetKrw);
        }

        ConcentrationCalculator.ConcentrationResult concentrationAfter =
                concentrationCalculator.diagnose(adjustedAssetKrw, 0.35);

        return new DiversificationSimulation(
                result,
                concentrationAfter,
                owner.getId(),
                targetCurrency
        );
    }

    private Map<String, RateSnapshot> fetchLatestRates(List<Holding> holdings, List<Deposit> deposits) {
        Map<String, RateSnapshot> rates = new HashMap<>();

        for (Holding h : holdings) {
            String currency = h.getCurrencyCode();
            if (!rates.containsKey(currency)) {
                RateSnapshot rate = fxRateProvider.fetchLatest(currency + "_KRW");
                rates.put(currency, rate);
            }
        }

        for (Deposit d : deposits) {
            String currency = d.getCurrencyCode();
            if (!rates.containsKey(currency)) {
                RateSnapshot rate = fxRateProvider.fetchLatest(currency + "_KRW");
                rates.put(currency, rate);
            }
        }

        return rates;
    }

    private Map<String, Long> calculateCurrencyAssets(
            List<Holding> holdings,
            List<Deposit> deposits,
            Map<String, RateSnapshot> rates) {
        Map<String, Long> result = new HashMap<>();

        for (Holding h : holdings) {
            String currency = h.getCurrencyCode();
            RateSnapshot rate = rates.get(currency);
            if (rate != null) {
                long assetKrw = Math.round(h.getQuantity() * h.getAvgPrice() * rate.rate().doubleValue());
                result.merge(currency, assetKrw, Long::sum);
            }
        }

        for (Deposit d : deposits) {
            String currency = d.getCurrencyCode();
            RateSnapshot rate = rates.get(currency);
            if (rate != null) {
                long assetKrw = d.getAmount().multiply(rate.rate()).longValue();
                result.merge(currency, assetKrw, Long::sum);
            }
        }

        return result;
    }

    /**
     * 기본 변동성 값을 구성한다 (임시: 실제로는 시장 데이터에서 조회).
     */
    private Map<String, Double> buildDefaultVolatility(java.util.Set<String> currencies) {
        Map<String, Double> volatility = new HashMap<>();
        for (String currency : currencies) {
            // 임시: 통화별 기본 연간 변동성 (%)
            double vol = switch (currency) {
                case "USD" -> 0.12;
                case "EUR" -> 0.14;
                case "JPY" -> 0.10;
                case "CNY" -> 0.08;
                default -> 0.10;
            };
            volatility.put(currency, vol);
        }
        return volatility;
    }

    /**
     * 기본 상관계수를 구성한다 (임시: 실제로는 시장 데이터에서 조회).
     */
    private Map<String, Double> buildDefaultCorrelation(java.util.Set<String> currencies) {
        Map<String, Double> correlation = new HashMap<>();
        String[] currencyArray = currencies.toArray(new String[0]);

        for (int i = 0; i < currencyArray.length; i++) {
            for (int j = i + 1; j < currencyArray.length; j++) {
                String curr1 = currencyArray[i];
                String curr2 = currencyArray[j];

                // 임시: 기본 상관계수
                double corr = 0.5; // 기본값: 중간 상관성
                correlation.put(curr1 + "_" + curr2, corr);
            }
        }

        return correlation;
    }

    // DTO 클래스들
    public record ConcentrationDiagnosis(
            ConcentrationCalculator.ConcentrationResult result,
            UUID userId
    ) {
    }

    public record DiversificationSimulation(
            DiversificationSimulator.SimulationResult result,
            ConcentrationCalculator.ConcentrationResult concentrationAfter,
            UUID userId,
            String targetCurrency
    ) {
    }
}
