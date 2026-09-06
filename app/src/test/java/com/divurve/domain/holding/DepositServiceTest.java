package com.divurve.domain.holding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.holding.entity.Deposit;
import com.divurve.domain.holding.entity.PurchaseFxRate;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link DepositService} — 조회·등록 + 매입 환율 컨텍스트 통합.
 */
@ExtendWith(MockitoExtension.class)
class DepositServiceTest {

    @Mock
    private DepositRepository depositRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PurchaseFxRateResolver purchaseFxRateResolver;

    private final UUID userId = UUID.randomUUID();
    private final User user = User.createDemo("me-d@divurve.com", "나");

    private DepositService service() {
        return new DepositService(depositRepository, userRepository, purchaseFxRateResolver);
    }

    @Test
    void list_는_소유자의_외화_예금만_반환한다() {
        Deposit d = Deposit.create(user, "USD", new BigDecimal("100"));
        when(depositRepository.findByOwner_Id(userId)).thenReturn(List.of(d));

        assertThat(service().list(userId)).containsExactly(d);
    }

    @Test
    void create_는_예치_시점_환율_컨텍스트를_붙여_저장한다() {
        LocalDate purchasedAt = LocalDate.of(2025, 6, 1);
        PurchaseFxRate fx = new PurchaseFxRate(new BigDecimal("1380.5"), "ECOS", purchasedAt.minusDays(1));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(purchaseFxRateResolver.resolve("USD", purchasedAt, null)).thenReturn(fx);
        when(depositRepository.save(any(Deposit.class))).thenAnswer(inv -> inv.getArgument(0));

        Deposit created = service().create(userId, "USD", new BigDecimal("500"), purchasedAt, null);

        assertThat(created.getCurrencyCode()).isEqualTo("USD");
        assertThat(created.getPurchasedAt()).isEqualTo(purchasedAt);
        assertThat(created.getPurchaseFxRateKrw()).isEqualByComparingTo("1380.5");
        assertThat(created.getPurchaseFxRateSource()).isEqualTo("ECOS");
    }

    @Test
    void create_는_사용자가_없으면_404_를_던진다() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(userId, "USD", new BigDecimal("1"), null, null))
                .isInstanceOf(NotFoundException.class);
        verify(purchaseFxRateResolver, never()).resolve(any(), any(), any());
    }
}
