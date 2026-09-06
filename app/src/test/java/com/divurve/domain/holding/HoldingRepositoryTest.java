package com.divurve.domain.holding;

import static org.assertj.core.api.Assertions.assertThat;

import com.divurve.domain.RepositoryTestBase;
import com.divurve.domain.holding.entity.Holding;
import com.divurve.domain.holding.entity.PurchaseFxRate;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class HoldingRepositoryTest extends RepositoryTestBase {

    @Autowired
    private HoldingRepository holdingRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByOwner_Id_는_소유자의_보유_종목만_반환한다() {
        User alice = userRepository.save(User.createDemo("alice@divurve.com", "앨리스"));
        User bob = userRepository.save(User.createDemo("bob@divurve.com", "밥"));
        holdingRepository.save(Holding.create(alice, "AAPL", "USD", 10.0, 150.0));
        holdingRepository.save(Holding.create(alice, "MSFT", "USD", 5.0, 300.0));
        holdingRepository.save(Holding.create(bob, "GOOG", "USD", 2.0, 120.0));

        assertThat(holdingRepository.findByOwner_Id(alice.getId()))
            .extracting(Holding::getTicker)
            .containsExactlyInAnyOrder("AAPL", "MSFT");
        assertThat(holdingRepository.findByOwner_Id(bob.getId()))
            .extracting(Holding::getTicker)
            .containsExactly("GOOG");
    }

    @Test
    void 저장한_보유_종목의_소유자_연관을_다시_조회할_수_있다() {
        User owner = userRepository.save(User.createDemo("carol@divurve.com", "캐럴"));
        Holding saved = holdingRepository.save(Holding.create(owner, "TSLA", "USD", 3.0, 200.0));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOwner().getId()).isEqualTo(owner.getId());
    }

    @Test
    void 매입_환율_컨텍스트를_보존한다() {
        User owner = userRepository.save(User.createDemo("dave@divurve.com", "데이브"));
        Holding holding = Holding.create(owner, "NVDA", "USD", 1.0, 100.0);
        LocalDate purchasedAt = LocalDate.of(2025, 3, 10);
        holding.assignPurchaseContext(
                purchasedAt, new PurchaseFxRate(new BigDecimal("1350.4200"), "ECOS", purchasedAt.minusDays(1)));
        Holding saved = holdingRepository.save(holding);

        Holding found = holdingRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getPurchasedAt()).isEqualTo(purchasedAt);
        assertThat(found.getPurchaseFxRateKrw()).isEqualByComparingTo("1350.4200");
        assertThat(found.getPurchaseFxRateSource()).isEqualTo("ECOS");
        assertThat(found.getPurchaseFxRateAsOf()).isEqualTo(purchasedAt.minusDays(1));
    }
}
