package com.divurve.domain.auth;

import com.divurve.common.architecture.UseCase;
import com.divurve.domain.goal.GoalRepository;
import com.divurve.domain.goal.entity.Goal;
import com.divurve.domain.holding.DepositRepository;
import com.divurve.domain.holding.HoldingRepository;
import com.divurve.domain.holding.entity.Deposit;
import com.divurve.domain.holding.entity.Holding;
import com.divurve.domain.port.AuthTokens;
import com.divurve.domain.port.TokenProvider;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데모 세션 발급 유스케이스 (이슈 #9, FR-ON-07). 회원가입 없이 샘플 데이터가 채워진 데모 계정을 만들고
 * 토큰을 발급한다. 데모 유저는 매 호출마다 고유 이메일로 새로 만들어 시연 간 데이터가 섞이지 않게 한다.
 *
 * <p>계산 로직은 없다 — 유저·샘플 데이터 시드와 토큰 발급뿐이다. 시드 값은 전 화면을 둘러보기 위한
 * 대표 표본(보유 종목·외화 예금·목표)이다.
 */
@UseCase
public class AuthDemoService {

    private final UserRepository userRepository;
    private final HoldingRepository holdingRepository;
    private final DepositRepository depositRepository;
    private final GoalRepository goalRepository;
    private final TokenProvider tokenProvider;

    public AuthDemoService(
            UserRepository userRepository,
            HoldingRepository holdingRepository,
            DepositRepository depositRepository,
            GoalRepository goalRepository,
            TokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.holdingRepository = holdingRepository;
        this.depositRepository = depositRepository;
        this.goalRepository = goalRepository;
        this.tokenProvider = tokenProvider;
    }

    /** 데모 유저를 생성하고 샘플 데이터를 시드한 뒤, {@code is_demo=true} 토큰을 발급해 반환한다. */
    @Transactional
    public AuthTokens createDemoSession() {
        User demoUser = userRepository.save(User.createDemo(newDemoEmail(), "데모 사용자"));
        seedSampleData(demoUser);
        return tokenProvider.issue(demoUser.getId(), true);
    }

    private String newDemoEmail() {
        return "demo-" + UUID.randomUUID() + "@divurve.local";
    }

    private void seedSampleData(User owner) {
        holdingRepository.save(Holding.create(owner, "AAPL", "USD", 10, 180.0));
        holdingRepository.save(Holding.create(owner, "VOO", "USD", 5, 420.0));
        depositRepository.save(Deposit.create(owner, "USD", new BigDecimal("3000.0000")));
        goalRepository.save(Goal.builder(owner, "미국 여행 경비", "travel", "여행", "USD")
                .targetAmount(5000.0)
                .targetDate(LocalDate.now().plusMonths(6))
                .budgetAmount(1_000_000L)
                .budgetCurrencyCode("KRW")
                .budgetPeriod("monthly")
                .isSpeculative(false)
                .status("active")
                .build());
    }
}
