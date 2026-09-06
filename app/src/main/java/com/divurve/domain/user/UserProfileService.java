package com.divurve.domain.user;

import com.divurve.common.architecture.UseCase;
import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.user.entity.User;
import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 프로필·초기 설정 유스케이스 (API 명세 v2 §3, FR-IS-05·FR-IS-07·FR-MY-01).
 * 계정 정보(이메일·이름) 조회/수정과 초기 설정 종료 기록을 담당한다.
 *
 * <p>초기 설정은 <b>전부 건너뛰어도 종료할 수 있다</b>(FR-IS-05). 건너뛴 항목을 임의 기본값으로 저장하지 않으므로
 * 이 서비스는 {@code users.onboarded_at} 만 기록하고 성향·설정에는 손대지 않는다(FR-IS-06).
 */
@UseCase
public class UserProfileService {

    private final UserRepository userRepository;

    public UserProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 사용자 프로필을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 프로필 조회 결과
     * @throws NotFoundException 사용자를 찾을 수 없는 경우
     */
    @Transactional(readOnly = true)
    public ProfileView getProfile(UUID userId) {
        return toProfileView(findUser(userId));
    }

    /**
     * 사용자 프로필(이름)을 수정한다.
     *
     * @param userId 사용자 ID
     * @param name 새 이름
     * @return 수정된 프로필
     * @throws NotFoundException 사용자를 찾을 수 없는 경우
     */
    @Transactional
    public ProfileView updateProfile(UUID userId, String name) {
        User user = findUser(userId);
        user.updateName(name);
        return toProfileView(userRepository.save(user));
    }

    /**
     * 초기 설정을 종료 처리한다 ({@code POST /me/onboarding/complete}). 진단·자산을 전부 건너뛴 상태에서도
     * 호출할 수 있고, 건너뛴 항목에 임의 기본값을 채우지 않는다(FR-IS-05·FR-IS-06).
     * 이미 완료한 사용자가 다시 호출해도 최초 완료 시각을 유지한다(멱등, FR-IS-07).
     *
     * @throws NotFoundException 사용자를 찾을 수 없는 경우
     */
    @Transactional
    public ProfileView completeOnboarding(UUID userId) {
        User user = findUser(userId);
        user.completeOnboarding(Instant.now());
        return toProfileView(userRepository.save(user));
    }

    /** 초기 설정 완료 여부. 로그인 응답의 {@code onboarded} 가 이 값이다(FR-IS-01). */
    @Transactional(readOnly = true)
    public boolean isOnboarded(UUID userId) {
        return findUser(userId).isOnboarded();
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
    }

    private ProfileView toProfileView(User user) {
        return new ProfileView(
                user.getId(), user.getEmail(), user.getName(), user.isDemo(),
                user.isOnboarded(), user.getOnboardedAt());
    }

    /**
     * 프로필 조회 결과.
     *
     * @param onboarded   초기 설정 완료 여부 (FR-IS-01)
     * @param onboardedAt 초기 설정 완료 시각. 미완료면 {@code null}
     */
    public record ProfileView(
            UUID userId,
            String email,
            String name,
            boolean isDemo,
            boolean onboarded,
            Instant onboardedAt) {
    }
}
