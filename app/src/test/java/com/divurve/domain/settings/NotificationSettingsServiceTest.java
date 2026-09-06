package com.divurve.domain.settings;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.settings.NotificationSettingsService.NotificationSettingsView;
import com.divurve.domain.settings.entity.UserSettings;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class NotificationSettingsServiceTest {

    private UserSettingsRepository userSettingsRepository;
    private UserRepository userRepository;
    private NotificationSettingsService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userSettingsRepository = Mockito.mock(UserSettingsRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        service = new NotificationSettingsService(userSettingsRepository, userRepository);
    }

    @Test
    void testGetNotifications_ReturnsDefaultValuesWhenNotExists() {
        when(userSettingsRepository.findByOwner_Id(userId)).thenReturn(Optional.empty());

        NotificationSettingsView notifications = service.getNotifications(userId);

        assertThat(notifications.exchangeScheduleReminder()).isTrue();
        assertThat(notifications.reviewRequiredAlert()).isTrue();
        assertThat(notifications.deadlineApproachAlert()).isTrue();
        assertThat(notifications.bucketEntryAlert()).isTrue();
    }

    @Test
    void testGetNotifications_ReturnsPersistedValues() {
        User user = User.create("test@example.com", "테스트사용자", false);
        UserSettings settings = UserSettings.create(user, null, 0.0, "simple", "plain");
        settings.updateNotifications(true, false, true, false);
        when(userSettingsRepository.findByOwner_Id(userId)).thenReturn(Optional.of(settings));

        NotificationSettingsView notifications = service.getNotifications(userId);

        assertThat(notifications.exchangeScheduleReminder()).isTrue();
        assertThat(notifications.reviewRequiredAlert()).isFalse();
        assertThat(notifications.deadlineApproachAlert()).isTrue();
        assertThat(notifications.bucketEntryAlert()).isFalse();
    }

    @Test
    void testUpdateNotifications_CreatesNewSettingsWhenNotExists() {
        User user = User.create("test@example.com", "테스트사용자", false);
        UserSettings created = UserSettings.create(user, null, 0.0, "simple", "plain");
        created.updateNotifications(false, false, true, true);

        when(userSettingsRepository.findByOwner_Id(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userSettingsRepository.save(Mockito.any())).thenReturn(created);

        NotificationSettingsView updated = service.updateNotifications(userId, false, false, true, true);

        assertThat(updated.exchangeScheduleReminder()).isFalse();
        assertThat(updated.reviewRequiredAlert()).isFalse();
        assertThat(updated.deadlineApproachAlert()).isTrue();
        assertThat(updated.bucketEntryAlert()).isTrue();
    }

    @Test
    void testUpdateNotifications_UpdatesExistingSettings() {
        User user = User.create("test@example.com", "테스트사용자", false);
        UserSettings settings = UserSettings.create(user, null, 0.0, "simple", "plain");
        when(userSettingsRepository.findByOwner_Id(userId)).thenReturn(Optional.of(settings));
        when(userSettingsRepository.save(settings)).thenReturn(settings);

        service.updateNotifications(userId, false, true, false, true);

        assertThat(settings.isExchangeScheduleReminder()).isFalse();
        assertThat(settings.isReviewRequiredAlert()).isTrue();
    }

    @Test
    void testUpdateNotifications_PreservesUnchangedValues() {
        User user = User.create("test@example.com", "테스트사용자", false);
        UserSettings settings = UserSettings.create(user, null, 0.0, "simple", "plain");
        settings.updateNotifications(true, false, true, false);
        when(userSettingsRepository.findByOwner_Id(userId)).thenReturn(Optional.of(settings));
        when(userSettingsRepository.save(settings)).thenReturn(settings);

        // null 필드는 기존값 유지
        NotificationSettingsView updated = service.updateNotifications(userId, null, true, null, false);

        assertThat(updated.exchangeScheduleReminder()).isTrue();
        assertThat(updated.reviewRequiredAlert()).isTrue();
        assertThat(updated.deadlineApproachAlert()).isTrue();
        assertThat(updated.bucketEntryAlert()).isFalse();
    }

    @Test
    void testUpdateNotifications_ExistingSettingsPreserveReviewAndBucketWhenNull() {
        User user = User.create("test@example.com", "테스트사용자", false);
        UserSettings settings = UserSettings.create(user, null, 0.0, "simple", "plain");
        settings.updateNotifications(false, false, false, true);
        when(userSettingsRepository.findByOwner_Id(userId)).thenReturn(Optional.of(settings));
        when(userSettingsRepository.save(settings)).thenReturn(settings);

        // 재검토필요·구간진입만 null → 기본값 true 가 아니라 기존 저장값(false·true)이 유지돼야 한다
        NotificationSettingsView updated = service.updateNotifications(userId, true, null, true, null);

        assertThat(updated.exchangeScheduleReminder()).isTrue();
        assertThat(updated.reviewRequiredAlert()).isFalse();
        assertThat(updated.deadlineApproachAlert()).isTrue();
        assertThat(updated.bucketEntryAlert()).isTrue();
    }

    @Test
    void testUpdateNotifications_UserNotFound() {
        when(userSettingsRepository.findByOwner_Id(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateNotifications(userId, true, true, true, true))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void testUpdateNotifications_NewUserWithPartialNulls() {
        User user = User.create("test@example.com", "테스트사용자", false);
        UserSettings created = UserSettings.create(user, null, 0.0, "simple", "plain");
        created.updateNotifications(true, false, true, true);

        when(userSettingsRepository.findByOwner_Id(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userSettingsRepository.save(Mockito.any())).thenReturn(created);

        // null 필드는 기본값 true로 설정됨
        NotificationSettingsView updated = service.updateNotifications(userId, null, false, null, true);

        assertThat(updated.exchangeScheduleReminder()).isTrue();
        assertThat(updated.reviewRequiredAlert()).isFalse();
        assertThat(updated.deadlineApproachAlert()).isTrue();
        assertThat(updated.bucketEntryAlert()).isTrue();
    }

    @Test
    void testUpdateNotifications_NewUserAllNull() {
        User user = User.create("test@example.com", "테스트사용자", false);
        UserSettings created = UserSettings.create(user, null, 0.0, "simple", "plain");
        created.updateNotifications(true, true, true, true);

        when(userSettingsRepository.findByOwner_Id(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userSettingsRepository.save(Mockito.any())).thenReturn(created);

        // 모든 필드가 null이면 모두 기본값 true로 설정됨
        NotificationSettingsView updated = service.updateNotifications(userId, null, null, null, null);

        assertThat(updated.exchangeScheduleReminder()).isTrue();
        assertThat(updated.reviewRequiredAlert()).isTrue();
        assertThat(updated.deadlineApproachAlert()).isTrue();
        assertThat(updated.bucketEntryAlert()).isTrue();
    }
}
