package com.divurve.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.divurve.domain.RepositoryTestBase;
import com.divurve.domain.goal.GoalRepository;
import com.divurve.domain.goal.entity.Goal;
import com.divurve.domain.plan.entity.Plan;
import com.divurve.domain.plan.entity.PlanStep;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PlanRepositoryTest extends RepositoryTestBase {

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private PlanStepRepository planStepRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private UserRepository userRepository;

    private Goal newGoal(String email) {
        User owner = userRepository.save(User.createDemo(email, "사용자"));
        return goalRepository.save(Goal.builder(owner, "목표", "onetime", "spend", "USD")
            .targetAmount(10000).budgetAmount(1_000_000).status("active").build());
    }

    @Test
    void findByGoal_Id_는_목표의_모든_계획_버전을_반환한다() {
        Goal goal = newGoal("plan1@divurve.com");
        planRepository.save(Plan.builder(goal, 1).isActive(false).safeRatio(0.7).splitCount(3).build());
        planRepository.save(Plan.builder(goal, 2).isActive(true).safeRatio(0.6).splitCount(4).build());

        assertThat(planRepository.findByGoal_Id(goal.getId()))
            .extracting(Plan::getVersion)
            .containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void findByGoal_IdAndIsActiveTrue_는_활성_계획만_반환한다() {
        Goal goal = newGoal("plan2@divurve.com");
        planRepository.save(Plan.builder(goal, 1).isActive(false).safeRatio(0.7).splitCount(3).build());
        planRepository.save(Plan.builder(goal, 2).isActive(true).safeRatio(0.6).splitCount(4)
            .opportunityAmount(1000).opportunityTriggerRate(1350.0).reason("변동성 상승").build());

        assertThat(planRepository.findByGoal_IdAndIsActiveTrue(goal.getId()))
            .isPresent()
            .get()
            .satisfies(p -> {
                assertThat(p.getVersion()).isEqualTo(2);
                assertThat(p.getReason()).isEqualTo("변동성 상승");
            });
    }

    @Test
    void findByPlan_IdOrderBySeqAsc_는_회차를_seq_오름차순으로_반환한다() {
        Goal goal = newGoal("plan3@divurve.com");
        Plan plan = planRepository.save(Plan.builder(goal, 1).isActive(true).safeRatio(0.7).splitCount(3).build());
        planStepRepository.save(PlanStep.create(plan, 3, LocalDate.of(2026, 3, 1), 100.0, 0.0, "pending"));
        planStepRepository.save(PlanStep.create(plan, 1, LocalDate.of(2026, 1, 1), 100.0, 100.0, "done"));
        planStepRepository.save(PlanStep.create(plan, 2, LocalDate.of(2026, 2, 1), 100.0, 0.0, "pending"));

        assertThat(planStepRepository.findByPlan_IdOrderBySeqAsc(plan.getId()))
            .extracting(PlanStep::getSeq)
            .containsExactly(1, 2, 3);
    }
}
