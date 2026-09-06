package com.divurve.domain.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.divurve.domain.RepositoryTestBase;
import com.divurve.domain.settings.entity.RiskAnswer;
import com.divurve.domain.settings.entity.RiskProfile;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@link RiskProfileRepository} 통합 테스트 — Flyway V3 스키마와 엔티티 매핑(응답 값 컬렉션 포함) 실연동 검증.
 */
class RiskProfileRepositoryTest extends RepositoryTestBase {

    @Autowired
    private RiskProfileRepository riskProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 성향_프로필과_응답이력을_저장하고_소유자로_조회한다() {
        User owner = userRepository.save(User.createDemo("rp-a@divurve.com", "앨리스"));
        riskProfileRepository.save(RiskProfile.create(
                owner, "balanced", 6, List.of(RiskAnswer.of("Q1", 1), RiskAnswer.of("Q2", 2), RiskAnswer.of("Q3", 3))));

        assertThat(riskProfileRepository.findByOwner_Id(owner.getId()))
                .get()
                .satisfies(p -> {
                    assertThat(p.getRiskType()).isEqualTo("balanced");
                    assertThat(p.getScore()).isEqualTo(6);
                    assertThat(p.getAnswers()).hasSize(3);
                });
    }

    @Test
    void 재진단하면_등급과_응답이력을_덮어쓴다() {
        User owner = userRepository.save(User.createDemo("rp-b@divurve.com", "밥"));
        RiskProfile profile = riskProfileRepository.saveAndFlush(RiskProfile.create(
                owner, "stable", 3, List.of(RiskAnswer.of("Q1", 1))));

        profile.reassess("flexible", 9, List.of(RiskAnswer.of("Q1", 3), RiskAnswer.of("Q2", 3), RiskAnswer.of("Q3", 3)));
        riskProfileRepository.saveAndFlush(profile);

        assertThat(riskProfileRepository.findByOwner_Id(owner.getId()))
                .get()
                .satisfies(p -> {
                    assertThat(p.getRiskType()).isEqualTo("flexible");
                    assertThat(p.getAnswers()).hasSize(3);
                });
    }
}
