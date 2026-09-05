package com.divurve.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.divurve.domain.RepositoryTestBase;
import com.divurve.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserRepositoryTest extends RepositoryTestBase {

    @Autowired
    private UserRepository userRepository;

    @Test
    void 저장_시_id와_created_at을_DB가_채운다() {
        // saveAndFlush 로 INSERT 를 즉시 반영해야 @Generated(INSERT) 가 created_at 을 재조회한다.
        User saved = userRepository.saveAndFlush(User.create("a@divurve.com", "앨리스", false));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void findByEmail_은_해당_이메일의_사용자를_반환한다() {
        userRepository.save(User.create("b@divurve.com", "밥", true));

        assertThat(userRepository.findByEmail("b@divurve.com"))
            .isPresent()
            .get()
            .satisfies(u -> {
                assertThat(u.getName()).isEqualTo("밥");
                assertThat(u.isDemo()).isTrue();
            });
    }

    @Test
    void findByEmail_은_없는_이메일이면_빈_Optional을_반환한다() {
        assertThat(userRepository.findByEmail("none@divurve.com")).isEmpty();
    }
}
