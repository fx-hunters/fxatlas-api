package com.divurve.domain.auth;

import com.divurve.common.architecture.UseCase;
import com.divurve.common.exception.DuplicateResourceException;
import com.divurve.common.exception.UnauthorizedException;
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
 * 이메일·비밀번호로 인증한다.
 *
 * <p>refresh 메서드는 기존 리프레시 토큰 검증 후 새 액세스 토큰만 발급한다.
 *
 * <p>로그인·갱신 결과에는 {@code onboarded}(초기 설정 완료 여부)가 함께 실린다 — 클라이언트가 초기 설정으로
 * 보낼지 홈으로 보낼지 이 값 하나로 결정한다(API 명세 v2 §3, FR-IS-01·FR-IS-07).
 */
@UseCase
public class AuthService {

    private static final String EMAIL_FIELD = "email";
    private static final String DUPLICATE_EMAIL_MESSAGE = "이미 사용 중인 이메일입니다.";

    /**
     * 로그인 실패 시 계정 존재 여부와 무관하게 노출하는 단일 메시지 (사용자 열거 방지, 이슈 #61).
     * "없는 이메일"과 "비밀번호 불일치"는 상태코드·메시지 모두 이 문자열 하나로만 응답한다.
     */
    private static final String INVALID_CREDENTIALS_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다.";

    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "유효하지 않거나 만료된 리프레시 토큰입니다.";

    /** bcrypt 비교 자체를 항상 수행해, 계정 존재 여부가 응답 시간으로 새는 것도 함께 막는다. */
    private static final String DUMMY_PASSWORD_FOR_TIMING_SAFETY = "dummy-password-for-timing-safety";

    private final UserRepository userRepository;
    private final TokenProvider tokenProvider;
    private final BCryptPasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, TokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.dummyPasswordHash = passwordEncoder.encode(DUMMY_PASSWORD_FOR_TIMING_SAFETY);
    }

    /**
     * 회원가입: 이메일 중복 검사 → 비밀번호 BCrypt 해시 → User 저장 → 토큰 발급.
     *
     * @param email 이메일
     * @param password 평문 비밀번호
     * @param name 사용자 이름
     * @return 발급된 액세스·리프레시 토큰
     * @throws DuplicateResourceException 이메일이 이미 가입돼 있을 때 (409)
     */
    @Transactional
    public AuthTokens signup(String email, String password, String name) {
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(password, "password must not be null");
        Objects.requireNonNull(name, "name must not be null");

        if (userRepository.findByEmail(email).isPresent()) {
            throw new DuplicateResourceException(DUPLICATE_EMAIL_MESSAGE, EMAIL_FIELD);
        }

        String passwordHash = passwordEncoder.encode(password);
        User user = User.create(email, name, passwordHash);
        User savedUser = userRepository.save(user);

        return tokenProvider.issue(savedUser.getId(), false);
    }

    /**
     * 로그인: 이메일로 User 조회 → 비밀번호 검증 → 토큰 발급.
     *
     * @param email 이메일
     * @param password 평문 비밀번호
     * @return 발급된 토큰과 초기 설정 완료 여부
     * @throws UnauthorizedException 이메일이 없거나 비밀번호가 틀렸을 때 (401). 사용자 열거를 막기 위해
     *                                두 경우를 구분하지 않고 같은 메시지로 던진다(이슈 #61) — 이메일이
     *                                없어도 더미 해시로 bcrypt 비교를 수행해 응답 시간도 동일하게 만든다.
     */
    @Transactional(readOnly = true)
    public AuthResult login(String email, String password) {
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(password, "password must not be null");

        Optional<User> user = userRepository.findByEmail(email);
        boolean passwordMatches =
                passwordEncoder.matches(password, user.map(User::getPasswordHash).orElse(dummyPasswordHash));

        if (user.isEmpty() || !passwordMatches) {
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }

        User authenticatedUser = user.get();
        return new AuthResult(tokenProvider.issue(authenticatedUser.getId(), false), authenticatedUser.isOnboarded());
    }

    /**
     * 토큰 갱신: 리프레시 토큰 검증 → 새 액세스 토큰 발급.
     * 리프레시 토큰은 재사용되며, 새 리프레시 토큰은 발급하지 않는다.
     *
     * @param refreshToken 리프레시 토큰
     * @return 새 액세스 토큰과 초기 설정 완료 여부 (refreshToken 필드는 입력 값과 동일)
     * @throws UnauthorizedException 리프레시 토큰이 위조됐거나 만료됐을 때 (401)
     */
    @Transactional(readOnly = true)
    public AuthResult refreshAccessToken(String refreshToken) {
        Objects.requireNonNull(refreshToken, "refreshToken must not be null");

        AuthPrincipal authPrincipal = tokenProvider.verifyRefreshToken(refreshToken)
                .orElseThrow(() -> new UnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE));
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
