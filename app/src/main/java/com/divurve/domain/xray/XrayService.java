package com.divurve.domain.xray;

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
import com.divurve.engine.attribution.AttributionCalculator;
import com.divurve.engine.concentration.ConcentrationCalculator;
import com.divurve.engine.stress.StressCalculator;
import com.divurve.engine.weight.WeightCalculator;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * X-ray 진단 유스케이스 (이슈 #14, FR-XR-01~08).
 * 사용자의 보유 자산을 조회하고 engine 계산기를 통해 비중·손익·스트레스를 분석한다.
 * Holding/Deposit은 읽기만 수행하고, 계산 로직은 모두 engine에 위임한다(SRP).
 */
@UseCase
public class XrayService {

    private final HoldingRepository holdingRepository;
    private final DepositRepository depositRepository;
    private final UserRepository userRepository;
    private final FxRateProvider fxRateProvider;
    private final WeightCalculator weightCalculator;
    private final AttributionCalculator attributionCalculator;
    private final StressCalculator stressCalculator;
    private final ConcentrationCalculator concentrationCalculator;

    public XrayService(
            HoldingRepository holdingRepository,
            DepositRepository depositRepository,
            UserRepository userRepository,
            FxRateProvider fxRateProvider,
            WeightCalculator weightCalculator,
            AttributionCalculator attributionCalculator,
            StressCalculator stressCalculator,
            ConcentrationCalculator concentrationCalculator) {
        this.holdingRepository = Objects.requireNonNull(holdingRepository, "holdingRepository is null");
        this.depositRepository = Objects.requireNonNull(depositRepository, "depositRepository is null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository is null");
        this.fxRateProvider = Objects.requireNonNull(fxRateProvider, "fxRateProvider is null");
        this.weightCalculator = Objects.requireNonNull(weightCalculator, "weightCalculator is null");
        this.attributionCalculator = Objects.requireNonNull(attributionCalculator, "attributionCalculator is null");
        this.stressCalculator = Objects.requireNonNull(stressCalculator, "stressCalculator is null");
        this.concentrationCalculator = Objects.requireNonNull(concentrationCalculator, "concentrationCalculator is null");
    }

    /**
     * 사용자의 포트폴리오 정보를 조회한다 (통화별 노출, 비중, 민감도).
     *
     * @param userId 사용자 ID (NFR-SE-03)
     * @return 포트폴리오 정보
     * @throws NotFoundException 사용자를 찾을 수 없는 경우
     */
    @Transactional(readOnly = true)
    public PortfolioSnapshot getPortfolio(UUID userId) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));

        List<Holding> holdings = holdingRepository.findByOwner_Id(userId);
        List<Deposit> deposits = depositRepository.findByOwner_Id(userId);

        return buildPortfolioSnapshot(owner, holdings, deposits);
    }

    /**
     * 원화 수익률을 자산/환율/교차항으로 분해한다.
     *
     * @param userId 사용자 ID
     * @param currencyCode 통화코드 (null이면 모든 외화)
     * @param mode 분해 모드 ("three_way" 또는 "shapley")
     * @return 귀속분해 결과
     */
    @Transactional(readOnly = true)
    public AttributionAnalysis getAttribution(UUID userId, String currencyCode, String mode) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));

        List<Holding> holdings = holdingRepository.findByOwner_Id(userId);

        // 통화 필터링 (currencyCode가 null이면 모든 통화)
        List<Holding> filteredHoldings = currencyCode != null
                ? holdings.stream()
                .filter(h -> h.getCurrencyCode().equals(currencyCode))
                .toList()
                : holdings;

        if (filteredHoldings.isEmpty()) {
            throw new NotFoundException("해당 통화의 보유 종목을 찾을 수 없습니다.");
        }

        String modeToUse = mode != null ? mode : "three_way";
        return buildAttributionAnalysis(filteredHoldings, modeToUse);
    }

    /**
     * 스트레스 시나리오를 적용한 포트폴리오 영향을 계산한다.
     *
     * @param userId 사용자 ID
     * @param currencyToShock 통화별 환율 충격(비율)의 맵
     * @return 스트레스 결과
     */
    @Transactional(readOnly = true)
    public StressAnalysis applyStress(UUID userId, Map<String, Double> currencyToShock) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));

        List<Holding> holdings = holdingRepository.findByOwner_Id(userId);
        List<Deposit> deposits = depositRepository.findByOwner_Id(userId);

        return buildStressAnalysis(holdings, deposits, currencyToShock);
    }

    private PortfolioSnapshot buildPortfolioSnapshot(User owner, List<Holding> holdings, List<Deposit> deposits) {
        // 환율 조회
        Map<String, RateSnapshot> rates = fetchLatestRates(holdings, deposits);

        // 자산 계산
        long krwAssetKrw = 0L; // 원화 자산은 별도 필드에서 조회해야 함
        Map<String, Long> currencyToAssetKrw = calculateCurrencyAssets(holdings, deposits, rates);

        long fxAssetKrw = currencyToAssetKrw.values().stream()
                .mapToLong(Long::longValue)
                .sum();
        long totalAssetKrw = krwAssetKrw + fxAssetKrw;

        // 비중 계산
        double fxRatio = weightCalculator.calculateFxRatio(totalAssetKrw, fxAssetKrw);
        Map<String, Double> exposure = weightCalculator.calculateExposureMap(currencyToAssetKrw, fxAssetKrw);

        // 집중도 진단
        ConcentrationCalculator.ConcentrationResult concentration =
                concentrationCalculator.diagnose(currencyToAssetKrw, 0.35); // 기본 임계값 35%

        return new PortfolioSnapshot(
                owner.getId(),
                totalAssetKrw,
                fxAssetKrw,
                fxRatio,
                currencyToAssetKrw,
                exposure,
                concentration
        );
    }

    private AttributionAnalysis buildAttributionAnalysis(List<Holding> holdings, String mode) {
        // 현재가/매입가 정보로 귀속분해 계산
        // 예시: 첫 번째 종목 기준으로 계산
        if (holdings.isEmpty()) {
            return null;
        }

        Holding holding = holdings.get(0);
        long costBasis = Math.round(holding.getQuantity() * holding.getAvgPrice() * holding.getPurchaseFxRateKrw().doubleValue());

        // 임시 구현: 실제로는 현재가 조회 필요
        long current = costBasis; // 임시

        AttributionCalculator.AttributionResult result = attributionCalculator.decompose(
                holding.getQuantity() * holding.getAvgPrice(),
                holding.getQuantity() * holding.getAvgPrice(), // 실제 현재가 필요
                holding.getPurchaseFxRateKrw(),
                holding.getPurchaseFxRateKrw(), // 현재 환율 필요
                0.0, // 비용 비율
                mode
        );

        return new AttributionAnalysis(result);
    }

    private StressAnalysis buildStressAnalysis(
            List<Holding> holdings,
            List<Deposit> deposits,
            Map<String, Double> currencyToShock) {
        // 환율 조회
        Map<String, RateSnapshot> rates = fetchLatestRates(holdings, deposits);

        // 자산 계산
        Map<String, Double> currencyToAssetLocal = new HashMap<>();
        for (Holding h : holdings) {
            String currency = h.getCurrencyCode();
            currencyToAssetLocal.merge(currency, h.getQuantity() * h.getAvgPrice(), Double::sum);
        }
        for (Deposit d : deposits) {
            String currency = d.getCurrencyCode();
            currencyToAssetLocal.merge(currency, d.getAmount().doubleValue(), Double::sum);
        }

        // 환율 맵 구성
        Map<String, BigDecimal> currencyToRate = new HashMap<>();
        for (String currency : currencyToAssetLocal.keySet()) {
            RateSnapshot rate = rates.get(currency);
            if (rate != null) {
                currencyToRate.put(currency, rate.rate());
            }
        }

        // 스트레스 계산
        StressCalculator.StressResult result = stressCalculator.apply(
                currencyToAssetLocal,
                currencyToRate,
                currencyToShock
        );

        return new StressAnalysis(result);
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

    // DTO 클래스들
    public record PortfolioSnapshot(
            UUID userId,
            long totalAssetKrw,
            long fxAssetKrw,
            double fxRatio,
            Map<String, Long> currencyToAssetKrw,
            Map<String, Double> exposure,
            ConcentrationCalculator.ConcentrationResult concentration
    ) {
    }

    public record AttributionAnalysis(
            AttributionCalculator.AttributionResult result
    ) {
    }

    public record StressAnalysis(
            StressCalculator.StressResult result
    ) {
    }
}
