package com.divurve.domain.user;

import com.divurve.common.architecture.UseCase;
import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.user.entity.User;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 프로필 유스케이스 (이슈 #21, FR-MY-01·FR-MY-02).
 * 프로필은 이메일·이름·생성시각 등 기본 사용자 정보를 조회하고 수정한다.
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
        return toProfileView(user);
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
        user.updateName(name);
        return toProfileView(userRepository.save(user));
    }

    private ProfileView toProfileView(User user) {
        return new ProfileView(user.getId(), user.getEmail(), user.getName(), user.isDemo());
    }

    /** 프로필 조회 결과. */
    public record ProfileView(UUID userId, String email, String name, boolean isDemo) {
    }
}
