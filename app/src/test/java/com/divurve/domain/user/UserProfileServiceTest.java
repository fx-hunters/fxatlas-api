package com.divurve.domain.user;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.user.UserProfileService.ProfileView;
import com.divurve.domain.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

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
        User user = User.create("test@example.com", "테스트사용자", false);
        user = Mockito.spy(user);
        Mockito.doReturn(userId).when(user).getId();
        when(repository.findById(userId)).thenReturn(Optional.of(user));

        ProfileView profile = service.getProfile(userId);

        assertThat(profile.userId()).isEqualTo(userId);
        assertThat(profile.email()).isEqualTo("test@example.com");
        assertThat(profile.name()).isEqualTo("테스트사용자");
        assertThat(profile.isDemo()).isFalse();
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
        User user = User.create("test@example.com", "테스트사용자", false);
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
}
