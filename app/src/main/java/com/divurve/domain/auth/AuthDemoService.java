package com.divurve.domain.auth;

import com.divurve.common.architecture.UseCase;
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
import com.divurve.domain.holding.entity.PurchaseFxRate;
import com.divurve.domain.port.AuthTokens;
import com.divurve.domain.port.TokenProvider;
import com.divurve.domain.settings.RiskProfileService;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데모 세션 발급 유스케이스 (이슈 #9, FR-ON-07). 회원가입 없이 샘플 데이터가 채워진 데모 계정을 만들고
 * 토큰을 발급한다. 데모 유저는 매 호출마다 고유 이메일로 새로 만들어 시연 간 데이터가 섞이지 않게 한다.
 *
 * <p><b>격리를 세션 단위로 두는 이유</b> — 데모 계정에는 쓰기 차단이 없어 보유 종목·목표를 실제로
 * 추가·수정할 수 있다. 고정된 단일 데모 계정을 공유하면 동시 시연 중 서로의 데이터를 침범한다.
 *
 * <p>계산 로직은 없다 — 유저·샘플 데이터 시드와 토큰 발급뿐이다. <b>시드 값 자체는 이 클래스에 있지 않고
 * {@link DemoSampleData} 한 곳에 모여 있다</b>(이슈 #78) — 템플릿은 하나, 인스턴스는 세션마다.
 * 위험성향만은 유형을 시드하지 않고 진단 응답을 제출해 {@link RiskProfileService} 가
 * 결정론적으로 산출하게 한다(CLAUDE.md 1장 — 수치는 계산 로직만 만든다).
 */
@UseCase
public class AuthDemoService {

    private final UserRepository userRepository;
    private final HoldingRepository holdingRepository;
    private final DepositRepository depositRepository;
    private final KrwAssetRepository krwAssetRepository;
    private final GoalRepository goalRepository;
    private final RiskProfileService riskProfileService;
    private final TokenProvider tokenProvider;
    private final Clock clock;

    public AuthDemoService(
            UserRepository userRepository,
            HoldingRepository holdingRepository,
            DepositRepository depositRepository,
            KrwAssetRepository krwAssetRepository,
            GoalRepository goalRepository,
            RiskProfileService riskProfileService,
            TokenProvider tokenProvider,
            Clock clock) {
        this.userRepository = userRepository;
        this.holdingRepository = holdingRepository;
        this.depositRepository = depositRepository;
        this.krwAssetRepository = krwAssetRepository;
        this.goalRepository = goalRepository;
        this.riskProfileService = riskProfileService;
        this.tokenProvider = tokenProvider;
        this.clock = clock;
    }

    /** 데모 유저를 생성하고 샘플 데이터를 시드한 뒤, {@code is_demo=true} 토큰을 발급해 반환한다. */
    @Transactional
    public AuthTokens createDemoSession() {
        User demoUser = userRepository.save(User.createDemo(newDemoEmail(), DemoSampleData.USER_NAME));
        seedSampleData(demoUser);
        return tokenProvider.issue(demoUser.getId(), true);
    }

    private String newDemoEmail() {
        return DemoSampleData.EMAIL_PREFIX + UUID.randomUUID() + DemoSampleData.EMAIL_DOMAIN;
    }

    /** {@link DemoSampleData} 정의를 이 유저의 데이터로 복제한다. */
    private void seedSampleData(User owner) {
        LocalDate today = LocalDate.now(clock);

        for (HoldingSample sample : DemoSampleData.HOLDINGS) {
            Holding holding = Holding.create(
                    owner, sample.ticker(), sample.currencyCode(), sample.quantity(), sample.avgPrice());
            holding.assignPurchaseContext(
                    sample.purchasedOn(today), purchaseFxRate(sample.purchaseFxRateKrw(), sample.purchasedOn(today)));
            holdingRepository.save(holding);
        }

        for (DepositSample sample : DemoSampleData.DEPOSITS) {
            Deposit deposit = Deposit.create(owner, sample.currencyCode(), sample.amount());
            deposit.assignPurchaseContext(
                    sample.purchasedOn(today), purchaseFxRate(sample.purchaseFxRateKrw(), sample.purchasedOn(today)));
            depositRepository.save(deposit);
        }

        for (KrwAssetSample sample : DemoSampleData.KRW_ASSETS) {
            krwAssetRepository.save(
                    KrwAsset.create(owner, sample.kind(), sample.label(), sample.amountKrw(), clock.instant()));
        }

        GoalSample goal = DemoSampleData.GOAL;
        goalRepository.save(Goal.builder(owner, goal.name(), goal.kind(), goal.purpose(), goal.currencyCode())
                .targetAmount(goal.targetAmount())
                .targetDate(goal.targetDate(today))
                .budgetAmount(goal.budgetAmount())
                .budgetCurrencyCode(goal.budgetCurrencyCode())
                .budgetPeriod(goal.budgetPeriod())
                .isSpeculative(goal.isSpeculative())
                .status(goal.status())
                .build());

        // 유형·점수·기준선은 여기서 만들지 않는다 — 응답만 제출하고 산출은 RiskProfileScorer 가 한다.
        riskProfileService.submitSimple(owner.getId(), DemoSampleData.RISK_PROFILE_ANSWERS);
    }

    private PurchaseFxRate purchaseFxRate(BigDecimal rateKrw, LocalDate purchasedOn) {
        return new PurchaseFxRate(rateKrw, DemoSampleData.PURCHASE_FX_RATE_SOURCE, purchasedOn);
    }
}
