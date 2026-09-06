package com.divurve.domain.fit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link FitService} — 집중도 진단과 분산효과 시뮬레이션.
 *
 * <p>engine 계산기는 순수 함수이므로 실제 인스턴스를 쓰고 결과 수치를 직접 단언한다.
 * 외부 의존(FxRateProvider)만 목으로 막아 네트워크를 타지 않게 한다.
 */
@ExtendWith(MockitoExtension.class)
class FitServiceTest {

    private static final double TOLERANCE = 1e-9;
    private static final LocalDate AS_OF = LocalDate.of(2026, 9, 5);
    private static final Instant FETCHED_AT = Instant.parse("2026-09-06T00:00:00Z");

    @Mock
    private HoldingRepository holdingRepository;
    @Mock
    private DepositRepository depositRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FxRateProvider fxRateProvider;

    private final UUID userId = UUID.randomUUID();
    private final User user = User.create("me@divurve.com", "나", false);

    private FitService service() {
        return new FitService(
                holdingRepository,
                depositRepository,
                userRepository,
                fxRateProvider,
                new WeightCalculator(),
                new ConcentrationCalculator(),
                new DiversificationSimulator());
    }

    // ── diagnoseConcentration ───────────────────────────────────────────────────

    @Test
    void diagnoseConcentration_은_통화별_비중을_계산하고_임계값_초과를_경고한다() {
        assignUserId(user, userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(holdingRepository.findByOwner_Id(userId)).thenReturn(List.of(
                holding("AAPL", "USD", 10, 100),
                holding("7203.T", "JPY", 100, 50)));
        when(depositRepository.findByOwner_Id(userId)).thenReturn(List.of(deposit("EUR", "1000")));
        when(fxRateProvider.fetchLatest("USD_KRW")).thenReturn(rate("USD_KRW", "1300"));
        when(fxRateProvider.fetchLatest("JPY_KRW")).thenReturn(rate("JPY_KRW", "9"));
        when(fxRateProvider.fetchLatest("EUR_KRW")).thenReturn(rate("EUR_KRW", "1400"));

        FitService.ConcentrationDiagnosis diagnosis = service().diagnoseConcentration(userId);

        // USD 1,300,000 + JPY 45,000 + EUR 1,400,000 = 2,745,000 원
        assertThat(diagnosis.exposure()).containsOnly(
                entry("USD", 0.4736), entry("JPY", 0.0164), entry("EUR", 0.5100));
        assertThat(diagnosis.topCurrency()).isEqualTo("EUR");
        assertThat(diagnosis.topShare()).isEqualTo(0.5100);
        assertThat(diagnosis.threshold()).isEqualTo(0.35);
        assertThat(diagnosis.status()).isEqualTo("warning");
        assertThat(diagnosis.userId()).isEqualTo(userId);
    }

    @Test
    void diagnoseConcentration_은_임계값_이하면_안전으로_진단한다() {
        assignUserId(user, userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        // 네 통화에 1,300,000 원씩 고르게 담아 최대 비중이 25% 가 되게 한다 → 임계값 35% 이내.
        when(holdingRepository.findByOwner_Id(userId)).thenReturn(List.of());
        when(depositRepository.findByOwner_Id(userId)).thenReturn(List.of(
                deposit("USD", "1000"),
                deposit("EUR", "1000"),
                deposit("JPY", "100000"),
                deposit("GBP", "1000")));
        when(fxRateProvider.fetchLatest("USD_KRW")).thenReturn(rate("USD_KRW", "1300"));
        when(fxRateProvider.fetchLatest("EUR_KRW")).thenReturn(rate("EUR_KRW", "1300"));
        when(fxRateProvider.fetchLatest("JPY_KRW")).thenReturn(rate("JPY_KRW", "13"));
        when(fxRateProvider.fetchLatest("GBP_KRW")).thenReturn(rate("GBP_KRW", "1300"));

        FitService.ConcentrationDiagnosis diagnosis = service().diagnoseConcentration(userId);

        // 네 통화 모두 1,300,000 원 → 각 25%
        assertThat(diagnosis.exposure()).containsOnly(
                entry("USD", 0.25), entry("EUR", 0.25), entry("JPY", 0.25), entry("GBP", 0.25));
        assertThat(diagnosis.topShare()).isEqualTo(0.25);
        assertThat(diagnosis.status()).isEqualTo("safe");
    }

    @Test
    void diagnoseConcentration_은_환율을_못_구한_통화를_제외하고_같은_통화는_한_번만_조회한다() {
        assignUserId(user, userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(holdingRepository.findByOwner_Id(userId)).thenReturn(List.of(
                holding("AAPL", "USD", 10, 100),
                holding("MSFT", "USD", 5, 200),
                holding("VOD.L", "GBP", 1, 100)));
        when(depositRepository.findByOwner_Id(userId)).thenReturn(List.of(
                deposit("USD", "500"),
                deposit("AUD", "100")));
        when(fxRateProvider.fetchLatest("USD_KRW")).thenReturn(rate("USD_KRW", "1300"));
        when(fxRateProvider.fetchLatest("GBP_KRW")).thenReturn(null);
        when(fxRateProvider.fetchLatest("AUD_KRW")).thenReturn(null);

        FitService.ConcentrationDiagnosis diagnosis = service().diagnoseConcentration(userId);

        // 환율이 없는 GBP/AUD 는 빠지고 USD 1,300,000 + 1,300,000 + 650,000 = 3,250,000 만 남는다.
        assertThat(diagnosis.exposure()).containsExactly(entry("USD", 1.0));
        assertThat(diagnosis.topCurrency()).isEqualTo("USD");
        assertThat(diagnosis.status()).isEqualTo("warning");
        verify(fxRateProvider, times(1)).fetchLatest("USD_KRW");
    }

    @Test
    void diagnoseConcentration_은_사용자가_없으면_404_를_던진다() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().diagnoseConcentration(userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("사용자를 찾을 수 없습니다.");
    }

    // ── simulateDiversification ─────────────────────────────────────────────────

    @Test
    void simulateDiversification_은_대상_통화_비중을_올리고_나머지를_비례_축소한다() {
        assignUserId(user, userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(holdingRepository.findByOwner_Id(userId)).thenReturn(List.of());
        // USD 1,300,000 / EUR 1,400,000 / JPY 900,000 / CNY 180,000 / GBP 1,600,000 = 5,380,000 원
        // (통화별 기본 변동성 USD .12 / EUR .14 / JPY .10 / CNY .08 / 그 외 기본값 .10 을 모두 태운다)
        when(depositRepository.findByOwner_Id(userId)).thenReturn(List.of(
                deposit("USD", "1000"),
                deposit("EUR", "1000"),
                deposit("JPY", "100000"),
                deposit("CNY", "1000"),
                deposit("GBP", "1000")));
        when(fxRateProvider.fetchLatest("USD_KRW")).thenReturn(rate("USD_KRW", "1300"));
        when(fxRateProvider.fetchLatest("EUR_KRW")).thenReturn(rate("EUR_KRW", "1400"));
        when(fxRateProvider.fetchLatest("JPY_KRW")).thenReturn(rate("JPY_KRW", "9"));
        when(fxRateProvider.fetchLatest("CNY_KRW")).thenReturn(rate("CNY_KRW", "180"));
        when(fxRateProvider.fetchLatest("GBP_KRW")).thenReturn(rate("GBP_KRW", "1600"));

        FitService.DiversificationSimulation simulation =
                service().simulateDiversification(userId, "USD", 0.1);

        // 조정 전 비중: USD 1,300,000 / 5,380,000 = 0.2416...
        // 조정 후 USD = 0.3416..., 나머지는 (1-0.3416)/(1-0.2416) 배로 축소된다.
        assertThat(simulation.adjustedShare()).hasSize(5);
        assertThat(simulation.adjustedShare().get("USD")).isCloseTo(0.341635687732342, Offset.offset(TOLERANCE));
        assertThat(simulation.adjustedShare().get("EUR")).isCloseTo(0.2259093228369415, Offset.offset(TOLERANCE));
        assertThat(simulation.adjustedShare().get("JPY")).isCloseTo(0.1452274218237481, Offset.offset(TOLERANCE));
        assertThat(simulation.adjustedShare().get("CNY")).isCloseTo(0.0290454843647496, Offset.offset(TOLERANCE));
        assertThat(simulation.adjustedShare().get("GBP")).isCloseTo(0.2581820832422189, Offset.offset(TOLERANCE));
        assertThat(simulation.adjustedShare().values().stream().mapToDouble(Double::doubleValue).sum())
                .isCloseTo(1.0, Offset.offset(TOLERANCE));

        // 변동성이 큰 USD 비중을 늘렸으므로 포트폴리오 변동성은 소폭 상승한다.
        assertThat(simulation.portfolioVolBefore()).isCloseTo(0.0907368161300162, Offset.offset(TOLERANCE));
        assertThat(simulation.portfolioVolAfter()).isCloseTo(0.0918020549478858, Offset.offset(TOLERANCE));
        assertThat(simulation.portfolioVolAfter()).isGreaterThan(simulation.portfolioVolBefore());

        // 조정 후 최대 비중은 USD 0.3416 로 임계값 0.35 이내다.
        assertThat(simulation.thresholdAfter()).isEqualTo(0.35);
        assertThat(simulation.topShareAfter()).isCloseTo(0.3416, Offset.offset(1e-6));
        assertThat(simulation.userId()).isEqualTo(userId);
        assertThat(simulation.targetCurrency()).isEqualTo("USD");
    }

    @Test
    void simulateDiversification_은_외화자산이_없으면_거부한다() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(holdingRepository.findByOwner_Id(userId)).thenReturn(List.of());
        when(depositRepository.findByOwner_Id(userId)).thenReturn(List.of());

        assertThatThrownBy(() -> service().simulateDiversification(userId, "USD", 0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("외화자산이 없어 시뮬레이션할 수 없습니다.");
    }

    @Test
    void simulateDiversification_은_포트폴리오에_없는_통화를_대상으로_하면_거부한다() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(holdingRepository.findByOwner_Id(userId)).thenReturn(List.of(holding("AAPL", "USD", 10, 100)));
        when(depositRepository.findByOwner_Id(userId)).thenReturn(List.of());
        when(fxRateProvider.fetchLatest("USD_KRW")).thenReturn(rate("USD_KRW", "1300"));

        assertThatThrownBy(() -> service().simulateDiversification(userId, "EUR", 0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("조정 대상 통화 EUR");
    }

    @Test
    void simulateDiversification_은_사용자가_없으면_404_를_던진다() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().simulateDiversification(userId, "USD", 0.1))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("사용자를 찾을 수 없습니다.");
    }

    // ── 픽스처 ──────────────────────────────────────────────────────────────────

    private Holding holding(String ticker, String currencyCode, double quantity, double avgPrice) {
        return Holding.create(user, ticker, currencyCode, quantity, avgPrice);
    }

    private Deposit deposit(String currencyCode, String amount) {
        return Deposit.create(user, currencyCode, new BigDecimal(amount));
    }

    private static RateSnapshot rate(String pairCode, String value) {
        return new RateSnapshot(pairCode, new BigDecimal(value), AS_OF, "ECOS", FETCHED_AT);
    }

    private static void assignUserId(User u, UUID id) {
        try {
            var field = u.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(u, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
