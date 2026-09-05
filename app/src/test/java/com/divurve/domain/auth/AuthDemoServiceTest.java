package com.divurve.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AuthDemoService} 단위 테스트 — 데모 유저 생성·샘플 시드·토큰 발급의 협력을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AuthDemoServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private HoldingRepository holdingRepository;
    @Mock
    private DepositRepository depositRepository;
    @Mock
    private GoalRepository goalRepository;
    @Mock
    private TokenProvider tokenProvider;

    @InjectMocks
    private AuthDemoService authDemoService;

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
        assertThat(userCaptor.getValue().isDemo()).isTrue();
        assertThat(userCaptor.getValue().getEmail()).startsWith("demo-").endsWith("@divurve.local");

        // 샘플 데이터: 보유 종목 2건·외화 예금 1건·목표 1건.
        verify(holdingRepository, times(2)).save(any(Holding.class));
        verify(depositRepository).save(any(Deposit.class));
        verify(goalRepository).save(any(Goal.class));

        // 토큰은 데모 플래그(true)로 발급된다.
        verify(tokenProvider).issue(any(), eq(true));
    }
}
