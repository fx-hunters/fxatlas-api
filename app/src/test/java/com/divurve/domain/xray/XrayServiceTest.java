package com.divurve.domain.xray;

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
import com.divurve.domain.holding.entity.PurchaseFxRate;
import com.divurve.domain.port.FxRateProvider;
import com.divurve.domain.port.RateSnapshot;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import com.divurve.engine.attribution.AttributionCalculator;
import com.divurve.engine.concentration.ConcentrationCalculator;
import com.divurve.engine.stress.StressCalculator;
import com.divurve.engine.weight.WeightCalculator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link XrayService} — 보유 자산 조회 → engine 계산 위임 → 도메인 경계 record 변환.
 *
 * <p>engine 계산기는 순수 함수이므로 목이 아닌 실제 인스턴스를 쓴다: 서비스가 넘긴 입력이
 * 실제 계산 결과로 이어지는지를 수치로 단언하기 위함이다. 외부 의존(FxRateProvider)만 목으로 막는다.
 */
@ExtendWith(MockitoExtension.class)
class XrayServiceTest {

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
    private final User user = User.create("me@divurve.com", "나", null, null);

    private XrayService service() {
        return new XrayService(
                holdingRepository,
                depositRepository,
                userRepository,
                fxRateProvider,
                new WeightCalculator(),
                new AttributionCalculator(),
                new StressCalculator(),
                new ConcentrationCalculator());
    }

    // ── getPortfolio ────────────────────────────────────────────────────────────

    @Test
    void getPortfolio_는_보유종목과_예금을_통화별로_합산하고_비중과_집중도를_계산한다() {
        assignUserId(user, userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        // USD 두 종목(1,000 USD + 1,000 USD) + JPY 5,000 + USD 예금 500 + EUR 예금 1,000
        when(holdingRepository.findByOwner_Id(userId)).thenReturn(List.of(
                holding("AAPL", "USD", 10, 100),
                holding("MSFT", "USD", 5, 200),
                holding("7203.T", "JPY", 100, 50)));
        when(depositRepository.findByOwner_Id(userId)).thenReturn(List.of(
                deposit("USD", "500"),
                deposit("EUR", "1000")));
        when(fxRateProvider.fetchLatest("USD_KRW")).thenReturn(rate("USD_KRW", "1300"));
        when(fxRateProvider.fetchLatest("JPY_KRW")).thenReturn(rate("JPY_KRW", "9"));
        when(fxRateProvider.fetchLatest("EUR_KRW")).thenReturn(rate("EUR_KRW", "1400"));

        XrayService.PortfolioSnapshot snapshot = service().getPortfolio(userId);

        // USD 10*100*1300 + 5*200*1300 + 500*1300 = 3,250,000 / JPY 100*50*9 = 45,000 / EUR 1,000*1,400 = 1,400,000
        assertThat(snapshot.currencyToAssetKrw()).containsOnly(
                entry("USD", 3_250_000L), entry("JPY", 45_000L), entry("EUR", 1_400_000L));
        assertThat(snapshot.fxAssetKrw()).isEqualTo(4_695_000L);
        assertThat(snapshot.totalAssetKrw()).isEqualTo(4_695_000L);
        assertThat(snapshot.fxRatio()).isEqualTo(1.0);
        assertThat(snapshot.exposure()).containsOnly(
                entry("USD", 0.6922), entry("JPY", 0.0096), entry("EUR", 0.2982));
        assertThat(snapshot.concentrationTopCurrency()).isEqualTo("USD");
        assertThat(snapshot.concentrationTopShare()).isEqualTo(0.6922);
        assertThat(snapshot.concentrationThreshold()).isEqualTo(0.35);
        assertThat(snapshot.concentrationStatus()).isEqualTo("warning");
        assertThat(snapshot.userId()).isEqualTo(userId);
    }

    @Test
    void getPortfolio_는_같은_통화의_환율을_한_번만_조회한다() {
        assignUserId(user, userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(holdingRepository.findByOwner_Id(userId)).thenReturn(List.of(
                holding("AAPL", "USD", 10, 100),
                holding("MSFT", "USD", 5, 200)));
        when(depositRepository.findByOwner_Id(userId)).thenReturn(List.of(deposit("USD", "500")));
        when(fxRateProvider.fetchLatest("USD_KRW")).thenReturn(rate("USD_KRW", "1300"));

        service().getPortfolio(userId);

        verify(fxRateProvider, times(1)).fetchLatest("USD_KRW");
    }

    @Test
    void getPortfolio_는_환율을_못_구한_통화를_합산에서_제외한다() {
        assignUserId(user, userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(holdingRepository.findByOwner_Id(userId)).thenReturn(List.of(
                holding("AAPL", "USD", 10, 100),
                holding("VOD.L", "GBP", 1, 100)));
        when(depositRepository.findByOwner_Id(userId)).thenReturn(List.of(
                deposit("USD", "500"),
                deposit("AUD", "100")));
        when(fxRateProvider.fetchLatest("USD_KRW")).thenReturn(rate("USD_KRW", "1300"));
        when(fxRateProvider.fetchLatest("GBP_KRW")).thenReturn(null);
        when(fxRateProvider.fetchLatest("AUD_KRW")).thenReturn(null);

        XrayService.PortfolioSnapshot snapshot = service().getPortfolio(userId);

        assertThat(snapshot.currencyToAssetKrw()).containsExactly(entry("USD", 1_950_000L));
        assertThat(snapshot.fxAssetKrw()).isEqualTo(1_950_000L);
        assertThat(snapshot.exposure()).containsExactly(entry("USD", 1.0));
        assertThat(snapshot.concentrationStatus()).isEqualTo("warning");
    }

    @Test
    void getPortfolio_는_자산이_없으면_비중을_0_으로_두고_안전으로_진단한다() {
        assignUserId(user, userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(holdingRepository.findByOwner_Id(userId)).thenReturn(List.of());
        when(depositRepository.findByOwner_Id(userId)).thenReturn(List.of());

        XrayService.PortfolioSnapshot snapshot = service().getPortfolio(userId);

        assertThat(snapshot.totalAssetKrw()).isZero();
        assertThat(snapshot.fxRatio()).isZero();
        assertThat(snapshot.exposure()).isEmpty();
        assertThat(snapshot.concentrationTopCurrency()).isNull();
        assertThat(snapshot.concentrationStatus()).isEqualTo("safe");
    }

    @Test
    void getPortfolio_는_사용자가_없으면_404_를_던진다() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getPortfolio(userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("사용자를 찾을 수 없습니다.");
    }

    // ── getAttribution ──────────────────────────────────────────────────────────

    @Test
    void getAttribution_는_통화로_필터하고_모드가_없으면_three_way_로_분해한다() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(holdingRepository.findByOwner_Id(userId)).thenReturn(List.of(
                holdingWithFxRate("7203.T", "JPY", 100, 50, "9"),
                holdingWithFxRate("AAPL", "USD", 10, 100, "1300")));

        XrayService.AttributionAnalysis analysis = service().getAttribution(userId, "USD", null);

        // USD 종목만 남으므로 매입원가 = 10 * 100 * 1,300 = 1,300,000 원
        assertThat(analysis.mode()).isEqualTo("three_way");
        assertThat(analysis.costBasisKrw()).isEqualTo(1_300_000L);
        assertThat(analysis.currentKrw()).isEqualTo(1_300_000L);
        // 현재가·현재환율이 아직 매입 시점과 동일하게 주입되므로 모든 기여는 0 이다.
        assertThat(analysis.totalReturn()).isZero();
        assertThat(analysis.asset()).isEqualTo(new XrayService.AttributionComponentData("asset", 0.0, 0L));
        assertThat(analysis.fx()).isEqualTo(new XrayService.AttributionComponentData("fx", 0.0, 0L));
        assertThat(analysis.interaction())
                .isEqualTo(new XrayService.AttributionComponentData("interaction", 0.0, 0L));
        assertThat(analysis.cost()).isEqualTo(new XrayService.AttributionComponentData("cost", 0.0, 0L));
    }

    @Test
    void getAttribution_는_통화가_null_이면_전체를_대상으로_하고_지정한_모드를_쓴다() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(holdingRepository.findByOwner_Id(userId)).thenReturn(List.of(
                holdingWithFxRate("7203.T", "JPY", 100, 50, "9"),
                holdingWithFxRate("AAPL", "USD", 10, 100, "1300")));

        XrayService.AttributionAnalysis analysis = service().getAttribution(userId, null, "shapley");

        // 필터가 없으므로 첫 종목(JPY) 기준: 100 * 50 * 9 = 45,000 원
        assertThat(analysis.mode()).isEqualTo("shapley");
        assertThat(analysis.costBasisKrw()).isEqualTo(45_000L);
        assertThat(analysis.currentKrw()).isEqualTo(45_000L);
    }

    @Test
    void getAttribution_는_해당_통화_보유종목이_없으면_404_를_던진다() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(holdingRepository.findByOwner_Id(userId)).thenReturn(List.of(
                holdingWithFxRate("AAPL", "USD", 10, 100, "1300")));

        assertThatThrownBy(() -> service().getAttribution(userId, "EUR", "three_way"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("해당 통화의 보유 종목을 찾을 수 없습니다.");
    }

    @Test
    void getAttribution_은_보유종목이_하나도_없으면_404_를_던진다() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(holdingRepository.findByOwner_Id(userId)).thenReturn(List.of());

        assertThatThrownBy(() -> service().getAttribution(userId, null, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getAttribution_는_사용자가_없으면_404_를_던진다() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getAttribution(userId, "USD", "three_way"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("사용자를 찾을 수 없습니다.");
    }

    // ── applyStress ─────────────────────────────────────────────────────────────

    @Test
    void applyStress_는_통화별_충격을_적용해_평가액_변화를_계산한다() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(holdingRepository.findByOwner_Id(userId)).thenReturn(List.of(holding("AAPL", "USD", 10, 100)));
        when(depositRepository.findByOwner_Id(userId)).thenReturn(List.of(deposit("JPY", "20000")));
        when(fxRateProvider.fetchLatest("USD_KRW")).thenReturn(rate("USD_KRW", "1300"));
        when(fxRateProvider.fetchLatest("JPY_KRW")).thenReturn(rate("JPY_KRW", "9"));

        // USD 만 +10% 충격, JPY 는 시나리오에 없으므로 0% 로 취급된다.
        XrayService.StressAnalysis analysis = service().applyStress(userId, Map.of("USD", 0.1));

        // USD 1,000 * 1,300 = 1,300,000 → 1,000 * 1,430 = 1,430,000 (+130,000) / JPY 20,000 * 9 = 180,000 (변화 없음)
        assertThat(analysis.totalAssetBeforeKrw()).isEqualTo(1_480_000L);
        assertThat(analysis.totalAssetAfterKrw()).isEqualTo(1_610_000L);
        assertThat(analysis.portfolioImpactKrw()).isEqualTo(130_000L);
        assertThat(analysis.portfolioImpactRatio()).isCloseTo(130_000d / 1_480_000d, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(analysis.byCurrencyMap()).containsOnly(
                entry("USD", new XrayService.CurrencyStressImpactData("USD", 0.1, 130_000L)),
                entry("JPY", new XrayService.CurrencyStressImpactData("JPY", 0.0, 0L)));
    }

    @Test
    void applyStress_는_환율을_못_구한_통화가_있으면_계산을_거부한다() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(holdingRepository.findByOwner_Id(userId)).thenReturn(List.of(holding("VOD.L", "GBP", 1, 100)));
        when(depositRepository.findByOwner_Id(userId)).thenReturn(List.of());
        when(fxRateProvider.fetchLatest("GBP_KRW")).thenReturn(null);

        assertThatThrownBy(() -> service().applyStress(userId, Map.of("GBP", 0.1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("환율은 null이거나 0 이하일 수 없습니다");
    }

    @Test
    void applyStress_는_사용자가_없으면_404_를_던진다() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().applyStress(userId, Map.of()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("사용자를 찾을 수 없습니다.");
    }

    // ── 픽스처 ──────────────────────────────────────────────────────────────────

    private Holding holding(String ticker, String currencyCode, double quantity, double avgPrice) {
        return Holding.create(user, ticker, currencyCode, quantity, avgPrice);
    }

    private Holding holdingWithFxRate(
            String ticker, String currencyCode, double quantity, double avgPrice, String fxRateKrw) {
        Holding h = holding(ticker, currencyCode, quantity, avgPrice);
        h.assignPurchaseContext(AS_OF, new PurchaseFxRate(new BigDecimal(fxRateKrw), "ECOS", AS_OF));
        return h;
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
