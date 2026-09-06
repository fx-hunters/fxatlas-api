package com.divurve.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.divurve.domain.auth.DemoSampleData.DepositSample;
import com.divurve.domain.auth.DemoSampleData.GoalSample;
import com.divurve.domain.auth.DemoSampleData.HoldingSample;
import com.divurve.domain.auth.DemoSampleData.KrwAssetSample;
import com.divurve.domain.goal.GoalRepository;
import com.divurve.domain.goal.entity.Goal;
import com.divurve.domain.holding.DepositRepository;
import com.divurve.domain.holding.HoldingRepository;
import com.divurve.domain.holding.KrwAssetRepository;
import com.divurve.domain.holding.entity.Deposit;
import com.divurve.domain.holding.entity.Holding;
import com.divurve.domain.holding.entity.KrwAsset;
import com.divurve.domain.port.AuthTokens;
import com.divurve.domain.port.TokenProvider;
import com.divurve.domain.settings.RiskProfileService;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AuthDemoService} 단위 테스트 — 데모 유저 생성·샘플 시드·토큰 발급의 협력을 검증한다.
 *
 * <p>시드 값 자체는 검증하지 않는다. 이 테스트가 확인하는 것은 <b>{@link DemoSampleData} 정의가
 * 빠짐없이 이 유저의 데이터로 복제되는가</b>이고, 값이 시연 시나리오를 만족하는지는
 * {@link DemoSampleDataTest} 가 본다(이슈 #78).
 */
@ExtendWith(MockitoExtension.class)
class AuthDemoServiceTest {

    /** 상대 날짜 계산의 기준일을 고정한다. */
    private static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");
    private static final LocalDate TODAY = LocalDate.ofInstant(NOW, ZoneOffset.UTC);

    @Mock
    private UserRepository userRepository;
    @Mock
    private HoldingRepository holdingRepository;
    @Mock
    private DepositRepository depositRepository;
    @Mock
    private KrwAssetRepository krwAssetRepository;
    @Mock
    private GoalRepository goalRepository;
    @Mock
    private RiskProfileService riskProfileService;
    @Mock
    private TokenProvider tokenProvider;

    private AuthDemoService authDemoService;

    @BeforeEach
    void setUp() {
        authDemoService = new AuthDemoService(
                userRepository,
                holdingRepository,
                depositRepository,
                krwAssetRepository,
                goalRepository,
                riskProfileService,
                tokenProvider,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createDemoSession_은_데모_유저를_만들고_샘플을_시드한_뒤_데모_토큰을_발급한다() {
        AuthTokens issued = new AuthTokens("access", "refresh", 1800L);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenProvider.issue(any(), eq(true))).thenReturn(issued);

        AuthTokens result = authDemoService.createDemoSession();

        assertThat(result).isSameAs(issued);

        // 데모 유저는 고유 이메일 + is_demo=true 로 생성된다.
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User demoUser = userCaptor.getValue();
        assertThat(demoUser.isDemo()).isTrue();
        assertThat(demoUser.getName()).isEqualTo(DemoSampleData.USER_NAME);
        assertThat(demoUser.getEmail())
                .startsWith(DemoSampleData.EMAIL_PREFIX)
                .endsWith(DemoSampleData.EMAIL_DOMAIN);

        // 토큰은 데모 플래그(true)로 발급된다.
        verify(tokenProvider).issue(any(), eq(true));
    }

    @Test
    void 데모_이메일은_호출마다_달라_시연_간_데이터가_섞이지_않는다() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenProvider.issue(any(), eq(true))).thenReturn(new AuthTokens("a", "r", 1L));

        authDemoService.createDemoSession();
        authDemoService.createDemoSession();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(userCaptor.capture());
        List<User> created = userCaptor.getAllValues();
        assertThat(created.get(0).getEmail()).isNotEqualTo(created.get(1).getEmail());
    }

    @Test
    void 보유_종목은_정의대로_매입_컨텍스트까지_복제된다() {
        seedOnce();

        ArgumentCaptor<Holding> captor = ArgumentCaptor.forClass(Holding.class);
        verify(holdingRepository, times(DemoSampleData.HOLDINGS.size())).save(captor.capture());
        List<Holding> saved = captor.getAllValues();

        for (int i = 0; i < DemoSampleData.HOLDINGS.size(); i++) {
            HoldingSample sample = DemoSampleData.HOLDINGS.get(i);
            Holding holding = saved.get(i);

            assertThat(holding.getTicker()).isEqualTo(sample.ticker());
            assertThat(holding.getCurrencyCode()).isEqualTo(sample.currencyCode());
            assertThat(holding.getQuantity()).isEqualTo(sample.quantity());
            assertThat(holding.getAvgPrice()).isEqualTo(sample.avgPrice());
            assertThat(holding.getPurchasedAt()).isEqualTo(sample.purchasedOn(TODAY));
            assertThat(holding.getPurchaseFxRateKrw()).isEqualByComparingTo(sample.purchaseFxRateKrw());
            assertThat(holding.getPurchaseFxRateSource()).isEqualTo(DemoSampleData.PURCHASE_FX_RATE_SOURCE);
            assertThat(holding.getPurchaseFxRateAsOf()).isEqualTo(sample.purchasedOn(TODAY));
        }
    }

    @Test
    void 외화_예금은_정의대로_매입_컨텍스트까지_복제된다() {
        seedOnce();

        ArgumentCaptor<Deposit> captor = ArgumentCaptor.forClass(Deposit.class);
        verify(depositRepository, times(DemoSampleData.DEPOSITS.size())).save(captor.capture());
        List<Deposit> saved = captor.getAllValues();

        for (int i = 0; i < DemoSampleData.DEPOSITS.size(); i++) {
            DepositSample sample = DemoSampleData.DEPOSITS.get(i);
            Deposit deposit = saved.get(i);

            assertThat(deposit.getCurrencyCode()).isEqualTo(sample.currencyCode());
            assertThat(deposit.getAmount()).isEqualByComparingTo(sample.amount());
            assertThat(deposit.getPurchasedAt()).isEqualTo(sample.purchasedOn(TODAY));
            assertThat(deposit.getPurchaseFxRateKrw()).isEqualByComparingTo(sample.purchaseFxRateKrw());
            assertThat(deposit.getPurchaseFxRateSource()).isEqualTo(DemoSampleData.PURCHASE_FX_RATE_SOURCE);
            assertThat(deposit.getPurchaseFxRateAsOf()).isEqualTo(sample.purchasedOn(TODAY));
        }
    }

    @Test
    void 원화_자산은_정의대로_복제된다() {
        seedOnce();

        ArgumentCaptor<KrwAsset> captor = ArgumentCaptor.forClass(KrwAsset.class);
        verify(krwAssetRepository, times(DemoSampleData.KRW_ASSETS.size())).save(captor.capture());
        List<KrwAsset> saved = captor.getAllValues();

        for (int i = 0; i < DemoSampleData.KRW_ASSETS.size(); i++) {
            KrwAssetSample sample = DemoSampleData.KRW_ASSETS.get(i);
            KrwAsset krwAsset = saved.get(i);

            assertThat(krwAsset.getKind()).isEqualTo(sample.kind());
            assertThat(krwAsset.getLabel()).isEqualTo(sample.label());
            assertThat(krwAsset.getAmountKrw()).isEqualTo(sample.amountKrw());
            assertThat(krwAsset.getUpdatedAt()).isEqualTo(NOW);
        }
    }

    @Test
    void 목표는_정의대로_복제되고_목표일은_기준일_기준_상대값이다() {
        seedOnce();

        ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
        verify(goalRepository).save(captor.capture());
        Goal saved = captor.getValue();
        GoalSample sample = DemoSampleData.GOAL;

        assertThat(saved.getName()).isEqualTo(sample.name());
        assertThat(saved.getKind()).isEqualTo(sample.kind());
        assertThat(saved.getPurpose()).isEqualTo(sample.purpose());
        assertThat(saved.getCurrencyCode()).isEqualTo(sample.currencyCode());
        assertThat(saved.getTargetAmount()).isEqualTo(sample.targetAmount());
        assertThat(saved.getTargetDate()).isEqualTo(sample.targetDate(TODAY));
        assertThat(saved.getBudgetAmount()).isEqualTo(sample.budgetAmount());
        assertThat(saved.getBudgetCurrencyCode()).isEqualTo(sample.budgetCurrencyCode());
        assertThat(saved.getBudgetPeriod()).isEqualTo(sample.budgetPeriod());
        assertThat(saved.isSpeculative()).isEqualTo(sample.isSpeculative());
        assertThat(saved.getStatus()).isEqualTo(sample.status());
    }

    @Test
    void 위험성향은_유형이_아니라_진단_응답을_제출해_산출하게_한다() {
        seedOnce();

        // 유형·점수·기준선을 직접 박지 않는다 — 산출은 RiskProfileScorer 의 몫이다(CLAUDE.md 1장).
        verify(riskProfileService).submitSimple(any(), eq(DemoSampleData.RISK_PROFILE_ANSWERS));
    }

    /** 시드 협력만 보면 되는 테스트를 위한 공통 실행. */
    private void seedOnce() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenProvider.issue(any(), eq(true))).thenReturn(new AuthTokens("a", "r", 1L));

        authDemoService.createDemoSession();
    }
}
