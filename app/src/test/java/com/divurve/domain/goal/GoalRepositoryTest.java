package com.divurve.domain.goal;

import static org.assertj.core.api.Assertions.assertThat;

import com.divurve.domain.RepositoryTestBase;
import com.divurve.domain.goal.entity.Goal;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class GoalRepositoryTest extends RepositoryTestBase {

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByOwner_Id_는_소유자의_목표만_반환한다() {
        User alice = userRepository.save(User.create("alice-g@divurve.com", "앨리스", false));
        User bob = userRepository.save(User.create("bob-g@divurve.com", "밥", false));
        goalRepository.save(Goal.builder(alice, "유학자금", "onetime", "spend", "USD")
            .targetAmount(50000).targetDate(LocalDate.of(2027, 3, 1)).budgetAmount(10_000_000).status("active")
            .build());
        goalRepository.save(Goal.builder(bob, "여행", "onetime", "spend", "JPY")
            .targetAmount(300000).budgetAmount(3_000_000).status("active").build());

        assertThat(goalRepository.findByOwner_Id(alice.getId()))
            .singleElement()
            .satisfies(g -> {
                assertThat(g.getName()).isEqualTo("유학자금");
                assertThat(g.getCurrencyCode()).isEqualTo("USD");
                assertThat(g.getTargetDate()).isEqualTo(LocalDate.of(2027, 3, 1));
            });
    }

    @Test
    void 선택_필드가_없어도_목표를_저장하고_조회할_수_있다() {
        User owner = userRepository.save(User.create("carol-g@divurve.com", "캐럴", false));
        goalRepository.save(Goal.builder(owner, "적립", "recurring", "invest", "USD")
            .targetAmount(0).budgetAmount(500_000).status("active").build());

        assertThat(goalRepository.findByOwner_Id(owner.getId()))
            .singleElement()
            .satisfies(g -> {
                assertThat(g.getTargetDate()).isNull();
                assertThat(g.getRecurInterval()).isNull();
            });
    }
}
