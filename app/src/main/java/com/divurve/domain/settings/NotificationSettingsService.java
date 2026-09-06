package com.divurve.domain.settings;

import com.divurve.common.architecture.UseCase;
import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.settings.entity.UserSettings;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 알림 설정 유스케이스 (이슈 #21, FR-MY-05·FR-MY-06).
 * 환전예정일·재검토필요·마감임박·구간진입 4종 알림의 활성화/비활성화를 관리한다.
 * 알림 설정이 없으면 기본값(모두 활성화)으로 응답한다.
 */
@UseCase
public class NotificationSettingsService {

    private final UserSettingsRepository userSettingsRepository;
    private final UserRepository userRepository;

    public NotificationSettingsService(
            UserSettingsRepository userSettingsRepository, UserRepository userRepository) {
        this.userSettingsRepository = userSettingsRepository;
        this.userRepository = userRepository;
    }

    /**
     * 사용자의 알림 설정을 조회한다. 설정이 없으면 모두 활성화된 기본값을 반환한다.
     *
     * @param userId 사용자 ID
     * @return 알림 설정 조회 결과
     */
    @Transactional(readOnly = true)
    public NotificationSettingsView getNotifications(UUID userId) {
        return userSettingsRepository.findByOwner_Id(userId)
                .map(this::toView)
                .orElseGet(() -> toView(null));
    }

    /**
     * 사용자의 알림 설정을 갱신한다. {@code null} 필드는 기존값(없으면 기본값 true)을 유지한다.
     *
     * @param userId 사용자 ID
     * @param exchangeScheduleReminder 환전예정일 알림
     * @param reviewRequiredAlert 재검토필요 알림
     * @param deadlineApproachAlert 마감임박 알림
     * @param bucketEntryAlert 구간진입 알림
     * @return 수정된 알림 설정
     * @throws NotFoundException 사용자를 찾을 수 없는 경우
     */
    @Transactional
    public NotificationSettingsView updateNotifications(
            UUID userId,
            Boolean exchangeScheduleReminder,
            Boolean reviewRequiredAlert,
            Boolean deadlineApproachAlert,
            Boolean bucketEntryAlert) {
        UserSettings existing = userSettingsRepository.findByOwner_Id(userId).orElse(null);

        boolean exchange = exchangeScheduleReminder != null
                ? exchangeScheduleReminder
                : (existing != null ? existing.isExchangeScheduleReminder() : true);
        boolean review = reviewRequiredAlert != null
                ? reviewRequiredAlert
                : (existing != null ? existing.isReviewRequiredAlert() : true);
        boolean deadline = deadlineApproachAlert != null
                ? deadlineApproachAlert
                : (existing != null ? existing.isDeadlineApproachAlert() : true);
        boolean bucket = bucketEntryAlert != null
                ? bucketEntryAlert
                : (existing != null ? existing.isBucketEntryAlert() : true);

        UserSettings settings;
        if (existing != null) {
            existing.updateNotifications(exchange, review, deadline, bucket);
            settings = existing;
        } else {
            User owner = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
            // 새 설정을 만들되, 표시·거래 필드는 기본값, 알림은 지정된 값으로 설정
            settings = UserSettings.create(owner, null, 0.0, "simple", "plain");
            settings.updateNotifications(exchange, review, deadline, bucket);
        }
        return toView(userSettingsRepository.save(settings));
    }

    private NotificationSettingsView toView(UserSettings settings) {
        if (settings == null) {
            return new NotificationSettingsView(true, true, true, true);
        }
        return new NotificationSettingsView(
                settings.isExchangeScheduleReminder(),
                settings.isReviewRequiredAlert(),
                settings.isDeadlineApproachAlert(),
                settings.isBucketEntryAlert());
    }

    /** 알림 설정 조회 결과. */
    public record NotificationSettingsView(
            boolean exchangeScheduleReminder,
            boolean reviewRequiredAlert,
            boolean deadlineApproachAlert,
            boolean bucketEntryAlert) {
    }
}
