package com.divurve.domain.settings;

import com.divurve.common.architecture.UseCase;
import com.divurve.common.exception.InvalidRequestException;
import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.settings.entity.UserSettings;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import com.divurve.engine.cost.EffectiveSpreadCalculator;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 설정 유스케이스 (API 명세 v2 §3 마이페이지 표, FR-MY-03~FR-MY-06, ERD v3.0 {@code user_settings}).
 * 설명 프로필·주거래 은행·환전 우대율·<b>알림 스위치 5종</b>을 한 엔드포인트({@code GET/PUT /me/settings})로 관리하고,
 * 은행 기본 스프레드({@link BankSpreadTable})에 우대율을 적용한 실효 스프레드를 engine 으로 계산해 함께 반환한다.
 *
 * <p>v1 의 {@code PUT /me/notifications} 는 여기로 흡수됐다 — 명세 §3 이 설정과 알림을 한 리소스로 묶었다.
 *
 * <p>설명 프로필({@code explain_level}·{@code explain_domain})은 문구·비유·설명 밀도에만 쓰고 금액·위험 판정과 분리한다
 * (FR-MY-03) — 여기서는 저장·검증만 한다. 실효 스프레드 수치는 {@link EffectiveSpreadCalculator}(순수 함수)만 만든다.
 */
@UseCase
public class UserSettingsService {

    /** 설명 선호 3단계 — 핵심만 쉽게. */
    public static final String EXPLAIN_LEVEL_SIMPLE = "simple";
    /** 설명 선호 3단계 — 숫자와 이유. */
    public static final String EXPLAIN_LEVEL_STANDARD = "standard";
    /** 설명 선호 3단계 — 지표와 한계. */
    public static final String EXPLAIN_LEVEL_DETAILED = "detailed";
    /** 허용하는 설명 선호 값(ERD v3.0 user_settings.explain_level). */
    public static final Set<String> ALLOWED_EXPLAIN_LEVELS =
            Set.of(EXPLAIN_LEVEL_SIMPLE, EXPLAIN_LEVEL_STANDARD, EXPLAIN_LEVEL_DETAILED);
    /** 설명 선호 기본값. */
    public static final String DEFAULT_EXPLAIN_LEVEL = EXPLAIN_LEVEL_SIMPLE;

    /** 허용하는 익숙한 설명 분야 값(ERD v3.0 user_settings.explain_domain). */
    public static final Set<String> ALLOWED_EXPLAIN_DOMAINS = Set.of("finance", "dev", "marketing", "plain");
    /** 익숙한 설명 분야 기본값. */
    public static final String DEFAULT_EXPLAIN_DOMAIN = "plain";

    /** 환전 우대율 기본값. */
    public static final double DEFAULT_FX_DISCOUNT_RATIO = 0.0;

    /** 알림 기본값 — ERD v3.0 은 {@code notify_target_zone} 만 false 로 둔다. */
    public static final boolean DEFAULT_NOTIFY_TARGET_ZONE = false;
    /** 나머지 알림 4종의 기본값. */
    public static final boolean DEFAULT_NOTIFY_ON = true;

    private final UserSettingsRepository userSettingsRepository;
    private final UserRepository userRepository;
    private final EffectiveSpreadCalculator effectiveSpreadCalculator;

    public UserSettingsService(
            UserSettingsRepository userSettingsRepository,
            UserRepository userRepository,
            EffectiveSpreadCalculator effectiveSpreadCalculator) {
        this.userSettingsRepository = userSettingsRepository;
        this.userRepository = userRepository;
        this.effectiveSpreadCalculator = effectiveSpreadCalculator;
    }

    /**
     * 현재 설정·알림 스위치와 실효 스프레드를 조회한다. 아직 설정한 적 없으면 기본값으로 응답한다(저장하지 않음).
     */
    @Transactional(readOnly = true)
    public SettingsView getSettings(UUID userId) {
        return userSettingsRepository.findByOwner_Id(userId)
                .map(this::toView)
                .orElseGet(() -> new SettingsView(
                        null,
                        DEFAULT_FX_DISCOUNT_RATIO,
                        DEFAULT_EXPLAIN_LEVEL,
                        DEFAULT_EXPLAIN_DOMAIN,
                        BankSpreadTable.baseSpreadRatio(null),
                        effectiveSpreadCalculator.effectiveSpreadRatio(
                                BankSpreadTable.baseSpreadRatio(null), DEFAULT_FX_DISCOUNT_RATIO),
                        DEFAULT_NOTIFY_ON,
                        DEFAULT_NOTIFY_ON,
                        DEFAULT_NOTIFY_ON,
                        DEFAULT_NOTIFY_TARGET_ZONE,
                        DEFAULT_NOTIFY_ON));
    }

    /**
     * 알림 스위치는 그대로 두고 설명 프로필·은행·우대율만 갱신한다. 상세 진단(Q5·Q6) 반영 경로가 이 오버로드를 쓴다.
     */
    @Transactional
    public SettingsView updateSettings(
            UUID userId, String defaultBankCode, Double fxDiscountRatio, String explainLevel, String explainDomain) {
        return updateSettings(
                userId, defaultBankCode, fxDiscountRatio, explainLevel, explainDomain,
                NotificationSwitches.unchanged());
    }

    /**
     * 설정과 알림 스위치를 갱신하고 갱신된 값·실효 스프레드를 반환한다.
     * <b>{@code null} 필드는 기존값(설정이 없으면 기본값)을 유지한다</b> — 부분 수정을 허용한다.
     *
     * @throws NotFoundException       사용자를 찾을 수 없는 경우
     * @throws InvalidRequestException 우대율이 0.0~1.0 밖이거나, 설명 선호/분야가 허용값이 아닌 경우
     */
    @Transactional
    public SettingsView updateSettings(
            UUID userId,
            String defaultBankCode,
            Double fxDiscountRatio,
            String explainLevel,
            String explainDomain,
            NotificationSwitches notifications) {
        // 설명 프로필 검증 — 허용값 밖 입력은 400. null(미변경)은 통과.
        if (explainLevel != null && !ALLOWED_EXPLAIN_LEVELS.contains(explainLevel)) {
            throw new InvalidRequestException(
                    "설명 선호는 " + ALLOWED_EXPLAIN_LEVELS + " 중 하나여야 합니다 (입력 " + explainLevel + ").");
        }
        if (explainDomain != null && !ALLOWED_EXPLAIN_DOMAINS.contains(explainDomain)) {
            throw new InvalidRequestException(
                    "설명 분야는 " + ALLOWED_EXPLAIN_DOMAINS + " 중 하나여야 합니다 (입력 " + explainDomain + ").");
        }

        UserSettings existing = userSettingsRepository.findByOwner_Id(userId).orElse(null);
        NotificationSwitches requested =
                notifications == null ? NotificationSwitches.unchanged() : notifications;

        String bankCode = defaultBankCode != null
                ? defaultBankCode
                : (existing != null ? existing.getDefaultBankCode() : null);
        double discount = fxDiscountRatio != null
                ? fxDiscountRatio
                : (existing != null ? existing.getFxDiscountRatio() : DEFAULT_FX_DISCOUNT_RATIO);
        String level = explainLevel != null
                ? explainLevel
                : (existing != null ? existing.getExplainLevel() : DEFAULT_EXPLAIN_LEVEL);
        String domain = explainDomain != null
                ? explainDomain
                : (existing != null ? existing.getExplainDomain() : DEFAULT_EXPLAIN_DOMAIN);

        // 우대율 검증 — 계산 계약(0~1) 위반은 사용자 입력 오류(400)로 표면화한다.
        if (discount < 0.0 || discount > 1.0) {
            throw new InvalidRequestException("환전 우대율은 0.0~1.0 이어야 합니다 (입력 " + discount + ").");
        }

        UserSettings settings;
        if (existing != null) {
            existing.update(bankCode, discount, level, domain);
            settings = existing;
        } else {
            User owner = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
            settings = UserSettings.create(owner, bankCode, discount, level, domain);
        }
        settings.updateNotifications(
                pick(requested.notifyStepDue(), existing, UserSettings::isNotifyStepDue, DEFAULT_NOTIFY_ON),
                pick(requested.notifyRegimeShift(), existing, UserSettings::isNotifyRegimeShift, DEFAULT_NOTIFY_ON),
                pick(requested.notifyDeadlineNear(), existing, UserSettings::isNotifyDeadlineNear, DEFAULT_NOTIFY_ON),
                pick(requested.notifyTargetZone(), existing, UserSettings::isNotifyTargetZone,
                        DEFAULT_NOTIFY_TARGET_ZONE),
                pick(requested.notifyConcentration(), existing, UserSettings::isNotifyConcentration,
                        DEFAULT_NOTIFY_ON));
        return toView(userSettingsRepository.save(settings));
    }

    /** 요청값 우선 → 기존값 → 기본값. {@code null} 은 미변경이다. */
    private static boolean pick(
            Boolean requested, UserSettings existing, Predicate<UserSettings> current, boolean fallback) {
        if (requested != null) {
            return requested;
        }
        return existing != null ? current.test(existing) : fallback;
    }

    private SettingsView toView(UserSettings settings) {
        double baseSpread = BankSpreadTable.baseSpreadRatio(settings.getDefaultBankCode());
        double effectiveSpread =
                effectiveSpreadCalculator.effectiveSpreadRatio(baseSpread, settings.getFxDiscountRatio());
        return new SettingsView(
                settings.getDefaultBankCode(),
                settings.getFxDiscountRatio(),
                settings.getExplainLevel(),
                settings.getExplainDomain(),
                baseSpread,
                effectiveSpread,
                settings.isNotifyStepDue(),
                settings.isNotifyRegimeShift(),
                settings.isNotifyDeadlineNear(),
                settings.isNotifyTargetZone(),
                settings.isNotifyConcentration());
    }
}
