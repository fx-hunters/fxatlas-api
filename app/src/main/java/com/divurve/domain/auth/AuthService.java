package com.divurve.domain.auth;

import com.divurve.common.architecture.UseCase;
import com.divurve.domain.port.AuthTokens;
import com.divurve.domain.port.AuthPrincipal;
import com.divurve.domain.port.TokenProvider;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입·로그인·토큰 갱신 유스케이스 (이슈 #22). 비밀번호 해싱(BCrypt, NFR-SE-01),
 * 중복 검증, 토큰 발급을 통합한다.
 *
 * <p>데모 계정(AuthDemoService)과 달리, 일반 회원은 passwordHash를 가지며,
 * 이메일·비밀번호로 인증한다. 온보딩 목적(OVERSEAS_INVESTMENT/FOREIGN_CURRENCY_GOAL)도 저장한다.
 *
 * <p>refresh 메서드는 기존 리프레시 토큰 검증 후 새 액세스 토큰만 발급한다.
 *
 * <p>로그인·갱신 결과에는 {@code onboarded}(초기 설정 완료 여부)가 함께 실린다 — 클라이언트가 초기 설정으로
 * 보낼지 홈으로 보낼지 이 값 하나로 결정한다(API 명세 v2 §3, FR-IS-01·FR-IS-07).
 */
@UseCase
public class AuthService {

    private final UserRepository userRepository;
    private final TokenProvider tokenProvider;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, TokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * 회원가입: 이메일 중복 검사 → 비밀번호 BCrypt 해시 → User 저장 → 토큰 발급.
     *
     * @param email 이메일
     * @param password 평문 비밀번호
     * @param name 사용자 이름
     * @param onboardingPurpose 온보딩 목적 (OVERSEAS_INVESTMENT 또는 FOREIGN_CURRENCY_GOAL)
     * @return 발급된 액세스·리프레시 토큰
     * @throws IllegalArgumentException 이메일 중복 또는 입력 파라미터 검증 실패 시
     */
    @Transactional
    public AuthTokens signup(String email, String password, String name, String onboardingPurpose) {
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(password, "password must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(onboardingPurpose, "onboardingPurpose must not be null");

        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already exists: " + email);
        }

        String passwordHash = passwordEncoder.encode(password);
        User user = User.create(email, name, passwordHash, onboardingPurpose);
        User savedUser = userRepository.save(user);

        return tokenProvider.issue(savedUser.getId(), false);
    }

    /**
     * 로그인: 이메일로 User 조회 → 비밀번호 검증 → 토큰 발급.
     *
     * @param email 이메일
     * @param password 평문 비밀번호
     * @return 발급된 토큰과 초기 설정 완료 여부
     * @throws IllegalArgumentException 이메일 미존재 또는 비밀번호 불일치 시
     */
    @Transactional(readOnly = true)
    public AuthResult login(String email, String password) {
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(password, "password must not be null");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid password");
        }

        return new AuthResult(tokenProvider.issue(user.getId(), false), user.isOnboarded());
    }

    /**
     * 토큰 갱신: 리프레시 토큰 검증 → 새 액세스 토큰 발급.
     * 리프레시 토큰은 재사용되며, 새 리프레시 토큰은 발급하지 않는다.
     *
     * @param refreshToken 리프레시 토큰
     * @return 새 액세스 토큰과 초기 설정 완료 여부 (refreshToken 필드는 입력 값과 동일)
     * @throws IllegalArgumentException 리프레시 토큰 검증 실패 시
     */
    @Transactional(readOnly = true)
    public AuthResult refreshAccessToken(String refreshToken) {
        Objects.requireNonNull(refreshToken, "refreshToken must not be null");

        Optional<AuthPrincipal> principal = tokenProvider.verifyRefreshToken(refreshToken);
        if (principal.isEmpty()) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        AuthPrincipal authPrincipal = principal.get();
        AuthTokens newTokens = tokenProvider.issue(authPrincipal.userId(), authPrincipal.isDemo());

        boolean onboarded = userRepository.findById(authPrincipal.userId())
                .map(User::isOnboarded)
                .orElse(false);

        // 기존 리프레시 토큰 유지 (클라이언트는 리프레시 토큰 교체 필요 없음)
        return new AuthResult(
                new AuthTokens(newTokens.accessToken(), refreshToken, newTokens.accessTokenTtlSeconds()),
                onboarded);
    }

    /**
     * 인증 결과 — 토큰과 초기 설정 완료 여부.
     *
     * @param tokens    발급된 액세스·리프레시 토큰
     * @param onboarded {@code users.onboarded_at} 이 기록됐는지. false 면 클라이언트가 초기 설정으로 보낸다
     */
    public record AuthResult(AuthTokens tokens, boolean onboarded) {
    }
}
