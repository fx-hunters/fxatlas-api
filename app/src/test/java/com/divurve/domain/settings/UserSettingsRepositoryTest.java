package com.divurve.domain.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.divurve.domain.RepositoryTestBase;
import com.divurve.domain.settings.entity.UserSettings;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@link UserSettingsRepository} 통합 테스트 — Flyway V3+V5 스키마와 엔티티 매핑 실연동 검증.
 */
class UserSettingsRepositoryTest extends RepositoryTestBase {

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 설정을_저장하고_소유자로_조회한다() {
        User owner = userRepository.save(User.create("us-a@divurve.com", "앨리스", false));
        userSettingsRepository.save(UserSettings.create(owner, "081", 0.5, "standard", "dev"));

        assertThat(userSettingsRepository.findByOwner_Id(owner.getId()))
                .get()
                .satisfies(s -> {
                    assertThat(s.getDefaultBankCode()).isEqualTo("081");
                    assertThat(s.getFxDiscountRatio()).isEqualTo(0.5);
                    assertThat(s.getExplainLevel()).isEqualTo("standard");
                    assertThat(s.getExplainDomain()).isEqualTo("dev");
                });
    }

    @Test
    void 주거래_은행을_비워도_저장하고_조회할_수_있다() {
        User owner = userRepository.save(User.create("us-b@divurve.com", "밥", false));
        userSettingsRepository.save(UserSettings.create(owner, null, 0.0, "standard", "plain"));

        assertThat(userSettingsRepository.findByOwner_Id(owner.getId()))
                .get()
                .satisfies(s -> assertThat(s.getDefaultBankCode()).isNull());
    }
}
