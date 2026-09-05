package com.divurve.domain.holding;

import static org.assertj.core.api.Assertions.assertThat;

import com.divurve.domain.RepositoryTestBase;
import com.divurve.domain.holding.entity.Deposit;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DepositRepositoryTest extends RepositoryTestBase {

    @Autowired
    private DepositRepository depositRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByOwner_Id_는_소유자의_외화_예금만_반환한다() {
        User alice = userRepository.save(User.create("alice-d@divurve.com", "앨리스", false));
        User bob = userRepository.save(User.create("bob-d@divurve.com", "밥", false));
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
        User owner = userRepository.save(User.create("carol-d@divurve.com", "캐럴", false));
        depositRepository.save(Deposit.create(owner, "EUR", new BigDecimal("12.3456")));

        assertThat(depositRepository.findByOwner_Id(owner.getId()))
            .singleElement()
            .satisfies(d -> assertThat(d.getAmount()).isEqualByComparingTo("12.3456"));
    }
}
