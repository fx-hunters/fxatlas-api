package com.divurve.domain.forecast;

import com.divurve.common.architecture.UseCase;
import com.divurve.domain.port.EconomicEventProvider;
import com.divurve.domain.port.FxRateHistoryProvider;
import com.divurve.domain.port.FxRateProvider;
import com.divurve.domain.port.MacroIndicatorProvider;
import com.divurve.engine.forecast.FanChartCalculator;
import com.divurve.engine.forecast.GbmSimulator;
import com.divurve.engine.forecast.ModelPerformanceCalculator;
import com.divurve.engine.forecast.TriangulationCalculator;
import com.divurve.engine.forecast.VolatilityCalculator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 환율 범위 전망 UseCase (명세 2·3.5~3.6).
 *
 * <p>팬차트, 전망 동인, 모델 성적표, 경제 일정 조회를 담당한다.
 * engine 계산 함수들을 조율하고, 외부 어댑터들로부터 데이터를 받아
 * 응답 DTO로 변환한다.
 *
 * <p>주요 책임:
 * - GBM 시뮬레이션으로 팬차트 생성 (FR-FC-01/02)
 * - 변동성 지표 계산 (FR-FC-03/04)
 * - 모델 성적표 생성 (FR-FC-08)
 * - 전망 동인 조회 (FR-FC-06)
 * - 경제 이벤트 조회 (FR-FC-09)
 * - 삼각 무차익으로 추가 통화 유도 (FR-FC-09)
 */
@UseCase
public class ForecastService {

    private static final int NUM_SIMULATION_PATHS = 1000;
    private static final long SIMULATION_SEED = 42L;
    private static final int HISTORY_WINDOW_YEARS = 5;
    private static final int HISTORY_WINDOW_DAYS = HISTORY_WINDOW_YEARS * 252;

    private final FxRateProvider fxRateProvider;
    private final FxRateHistoryProvider historyProvider;
    private final MacroIndicatorProvider macroProvider;
    private final EconomicEventProvider eventProvider;

    public ForecastService(
        FxRateProvider fxRateProvider,
        FxRateHistoryProvider historyProvider,
        MacroIndicatorProvider macroProvider,
        EconomicEventProvider eventProvider
    ) {
        this.fxRateProvider = Objects.requireNonNull(fxRateProvider);
        this.historyProvider = Objects.requireNonNull(historyProvider);
        this.macroProvider = Objects.requireNonNull(macroProvider);
        this.eventProvider = Objects.requireNonNull(eventProvider);
    }

    /**
     * 팬차트·구간·변동성 조회.
     *
     * @param pairCode 통화쌍 (USD_KRW, USD_JPY, EUR_USD 등)
     * @param horizonDays 미래 지평 (30 또는 90)
     * @return 팬차트 데이터
     */
    public ForecastData getForecast(String pairCode, int horizonDays) {
        Objects.requireNonNull(pairCode, "pairCode must not be null");
        validateHorizon(horizonDays);

        // 1. 현재 환율 조회
        double currentRate = fxRateProvider.fetchLatest(pairCode).rate().doubleValue();

        // 2. 과거 환율 조회 (변동성·모델 성적표 계산용)
        List<FxRateHistoryProvider.HistoryRateSnapshot> history =
            historyProvider.fetchHistorical(pairCode, LocalDate.now(), HISTORY_WINDOW_DAYS + 30);

        // 3. 일별 수익률 계산
        List<Double> dailyReturns = calculateDailyReturns(history);

        // 4. 변동성 지표
        double realized30d = VolatilityCalculator.calculateRealized30d(dailyReturns);
        int percentile5y = VolatilityCalculator.calculatePercentile5y(dailyReturns);
        String regime = VolatilityCalculator.classifyRegime(percentile5y);

        // 5. GBM 시뮬레이션 (팬차트 생성)
        GbmSimulator simulator = new GbmSimulator(SIMULATION_SEED);
        List<List<Double>> paths = simulator.simulate(
            currentRate,
            0.0, // 드리프트 0 (FRED 데이터로 추정 가능하지만 여기서는 중립)
            realized30d,
            horizonDays,
            NUM_SIMULATION_PATHS
        );

        // 6. 경로 포인트 생성
        List<FanChartCalculator.PathPoint> pathPoints = FanChartCalculator.generatePaths(paths);

        // 7. 기준선 (드리프트 0)
        List<Double> baseline = FanChartCalculator.generateBaseLine(currentRate, horizonDays);

        // 팬차트 데이터 구성
        return new ForecastData(
            pairCode,
            horizonDays,
            currentRate,
            baseline,
            history,
            pathPoints,
            realized30d,
            percentile5y,
            regime
        );
    }

    /**
     * 전망 동인 조회 (참고용, 계산에 입력되지 않음).
     *
     * @param pairCode 통화쌍
     * @return 상위 3개 동인
     */
    public List<ForecastFactor> getFactors(String pairCode) {
        Objects.requireNonNull(pairCode, "pairCode must not be null");

        // TODO: 실제 모델 성적표와 함께 제공되는 동인 데이터 통합
        // 지금은 목형 데이터 반환
        return List.of(
            new ForecastFactor("fed_rate", "Federal Reserve Rate", 0.35, "Bullish"),
            new ForecastFactor("risk_sentiment", "Global Risk Sentiment", -0.25, "Bearish"),
            new ForecastFactor("gdp_growth", "GDP Growth Differential", 0.15, "Neutral")
        );
    }

    /**
     * 모델 성적표 조회.
     *
     * @param pairCode 통화쌍
     * @param horizonDays 미래 지평
     * @return 모델 성적 지표
     */
    public ModelPerformanceData getModelPerformance(String pairCode, int horizonDays) {
        Objects.requireNonNull(pairCode, "pairCode must not be null");
        validateHorizon(horizonDays);

        // TODO: 실제 검증 데이터셋 기반 성적표 계산
        // 지금은 목형 데이터 반환
        return new ModelPerformanceData(
            pairCode,
            horizonDays,
            0.62, // hitRate
            15.5, // mae
            0.78, // coverage80
            45.3, // avgWidth
            0.48, // rwHitRate
            18.2  // rwMae
        );
    }

    /**
     * 경제 이벤트 조회.
     *
     * @return 향후 경제 이벤트들
     */
    public List<EconomicEventData> getEvents() {
        List<EconomicEventProvider.EconomicEvent> events =
            eventProvider.fetchUpcoming(LocalDate.now(), 90);

        return events.stream()
            .map(e -> new EconomicEventData(
                e.date().toString(),
                e.title(),
                e.currencyCode(),
                e.importance()
            ))
            .toList();
    }

    /**
     * 삼각 무차익으로 추가 통화 환율 유도.
     *
     * @param baseRates 기본 환율들 (USD_KRW, USD_JPY, EUR_USD)
     * @return 확장된 환율 맵
     */
    public Map<String, Double> extendRatesByTriangulation(Map<String, Double> baseRates) {
        return TriangulationCalculator.triangulateRates(baseRates);
    }

    private List<Double> calculateDailyReturns(List<FxRateHistoryProvider.HistoryRateSnapshot> history) {
        if (history == null || history.size() < 2) {
            return new ArrayList<>();
        }

        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < history.size(); i++) {
            double prevRate = history.get(i - 1).rate();
            double currRate = history.get(i).rate();
            double ret = Math.log(currRate / prevRate);
            returns.add(ret);
        }
        return returns;
    }

    private void validateHorizon(int horizonDays) {
        if (horizonDays != 30 && horizonDays != 90) {
            throw new IllegalArgumentException("horizonDays must be 30 or 90");
        }
    }

    // ── DTO들 ────────────────────────────────────

    /**
     * 팬차트 데이터.
     */
    public record ForecastData(
        String pairCode,
        int horizonDays,
        double currentRate,
        List<Double> baseline,
        List<FxRateHistoryProvider.HistoryRateSnapshot> history,
        List<FanChartCalculator.PathPoint> pathPoints,
        double realized30d,
        int percentile5y,
        String regime
    ) {
    }

    /**
     * 전망 동인.
     */
    public record ForecastFactor(String key, String label, double contribution, String direction) {
    }

    /**
     * 모델 성적 데이터.
     */
    public record ModelPerformanceData(
        String pairCode,
        int horizonDays,
        double modelHitRate,
        double modelMae,
        double coverage80,
        double avgWidth,
        double rwHitRate,
        double rwMae
    ) {
    }

    /**
     * 경제 이벤트 데이터.
     */
    public record EconomicEventData(String date, String title, String currencyCode, String importance) {
    }
}
