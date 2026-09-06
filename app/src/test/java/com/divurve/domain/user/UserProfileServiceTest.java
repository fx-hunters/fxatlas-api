package com.divurve.domain.user;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.user.UserProfileService.ProfileView;
import com.divurve.domain.user.entity.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * {@link UserProfileService} 단위 테스트 — 계정 조회·수정과 초기 설정 종료({@code POST /me/onboarding/complete}).
 *
 * <p>초기 설정은 전부 건너뛰어도 종료할 수 있고, 건너뛴 항목에 임의 기본값을 채우지 않는다(FR-IS-05·FR-IS-06).
 * 재호출해도 최초 완료 시각을 유지한다(FR-IS-07).
 */
class UserProfileServiceTest {

    private UserRepository repository;
    private UserProfileService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(UserRepository.class);
        service = new UserProfileService(repository);
    }

    @Test
    void testGetProfile_ReturnsProfileWithCorrectInfo() {
        User user = User.create("test@example.com", "테스트사용자", null);
        user = Mockito.spy(user);
        Mockito.doReturn(userId).when(user).getId();
        when(repository.findById(userId)).thenReturn(Optional.of(user));

        ProfileView profile = service.getProfile(userId);

        assertThat(profile.userId()).isEqualTo(userId);
        assertThat(profile.email()).isEqualTo("test@example.com");
        assertThat(profile.name()).isEqualTo("테스트사용자");
        assertThat(profile.isDemo()).isFalse();
        assertThat(profile.onboarded()).isFalse();
        assertThat(profile.onboardedAt()).isNull();
    }

    @Test
    void testGetProfile_UserNotFound() {
        when(repository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("사용자를 찾을 수 없습니다.");
    }

    @Test
    void testUpdateProfile_UpdatesNameSuccessfully() {
        User user = User.create("test@example.com", "테스트사용자", null);
        user = Mockito.spy(user);
        Mockito.doReturn(userId).when(user).getId();
        when(repository.findById(userId)).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);

        ProfileView updated = service.updateProfile(userId, "새이름");

        assertThat(updated.name()).isEqualTo("새이름");
        Mockito.verify(user).updateName("새이름");
    }

    @Test
    void testUpdateProfile_UserNotFound() {
        when(repository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateProfile(userId, "새이름"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void 초기_설정을_전부_건너뛰어도_종료할_수_있다() {
        User user = User.create("test@example.com", "테스트사용자", null);
        when(repository.findById(userId)).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);

        ProfileView profile = service.completeOnboarding(userId);

        assertThat(profile.onboarded()).isTrue();
        assertThat(profile.onboardedAt()).isNotNull();
        // users.onboarded_at 만 기록한다 — 성향·설정에 임의 기본값을 만들지 않는다(FR-IS-06).
        Mockito.verify(repository).save(user);
        assertThat(user.getOnboardedAt()).isNotNull();
    }

    @Test
    void 초기_설정_종료는_멱등이며_최초_완료_시각을_유지한다() {
        Instant first = Instant.parse("2026-09-01T15:30:00Z");
        User user = User.create("test@example.com", "테스트사용자", null);
        user.completeOnboarding(first);
        when(repository.findById(userId)).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);

        assertThat(service.completeOnboarding(userId).onboardedAt()).isEqualTo(first);
        assertThat(user.getOnboardedAt()).isEqualTo(first);
    }

    @Test
    void 초기_설정_종료는_사용자를_찾지_못하면_404() {
        when(repository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeOnboarding(userId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void isOnboarded_는_완료_여부만_돌려준다() {
        User user = User.create("test@example.com", "테스트사용자", null);
        when(repository.findById(userId)).thenReturn(Optional.of(user));
        assertThat(service.isOnboarded(userId)).isFalse();

        user.completeOnboarding(Instant.parse("2026-09-01T15:30:00Z"));
        assertThat(service.isOnboarded(userId)).isTrue();
    }

    @Test
    void isOnboarded_는_사용자를_찾지_못하면_404() {
        when(repository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.isOnboarded(userId))
                .isInstanceOf(NotFoundException.class);
    }
}
