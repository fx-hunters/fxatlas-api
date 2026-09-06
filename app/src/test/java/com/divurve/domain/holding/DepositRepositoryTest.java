package com.divurve.domain.holding;

import static org.assertj.core.api.Assertions.assertThat;

import com.divurve.domain.RepositoryTestBase;
import com.divurve.domain.holding.entity.Deposit;
import com.divurve.domain.holding.entity.PurchaseFxRate;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DepositRepositoryTest extends RepositoryTestBase {

    @Autowired
    private DepositRepository depositRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByOwner_Id_는_소유자의_외화_예금만_반환한다() {
        User alice = userRepository.save(User.createDemo("alice-d@divurve.com", "앨리스"));
        User bob = userRepository.save(User.createDemo("bob-d@divurve.com", "밥"));
        depositRepository.save(Deposit.create(alice, "USD", new BigDecimal("1000.5000")));
        depositRepository.save(Deposit.create(bob, "JPY", new BigDecimal("50000.0000")));

        assertThat(depositRepository.findByOwner_Id(alice.getId()))
            .singleElement()
            .satisfies(d -> {
                assertThat(d.getCurrencyCode()).isEqualTo("USD");
                assertThat(d.getAmount()).isEqualByComparingTo("1000.5000");
            });
    }

    @Test
    void 외화_금액은_소수_4자리까지_보존된다() {
        User owner = userRepository.save(User.createDemo("carol-d@divurve.com", "캐럴"));
        depositRepository.save(Deposit.create(owner, "EUR", new BigDecimal("12.3456")));

        assertThat(depositRepository.findByOwner_Id(owner.getId()))
            .singleElement()
            .satisfies(d -> assertThat(d.getAmount()).isEqualByComparingTo("12.3456"));
    }

    @Test
    void 매입_환율_컨텍스트를_보존한다() {
        User owner = userRepository.save(User.createDemo("dave-d@divurve.com", "데이브"));
        Deposit deposit = Deposit.create(owner, "USD", new BigDecimal("500.0000"));
        LocalDate purchasedAt = LocalDate.of(2025, 6, 1);
        deposit.assignPurchaseContext(
                purchasedAt, new PurchaseFxRate(new BigDecimal("1380.5000"), "ECOS", purchasedAt.minusDays(1)));
        Deposit saved = depositRepository.save(deposit);

        Deposit found = depositRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getPurchasedAt()).isEqualTo(purchasedAt);
        assertThat(found.getPurchaseFxRateKrw()).isEqualByComparingTo("1380.5000");
        assertThat(found.getPurchaseFxRateSource()).isEqualTo("ECOS");
        assertThat(found.getPurchaseFxRateAsOf()).isEqualTo(purchasedAt.minusDays(1));
    }
}
