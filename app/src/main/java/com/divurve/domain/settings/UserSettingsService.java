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
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 표시·거래 설정 유스케이스 (이슈 #10, FR-MY-03·FR-MY-04). 표시 모드·주거래 은행·환전 우대율을 관리하고,
 * 은행 기본 스프레드({@link BankSpreadTable})에 우대율을 적용한 실효 스프레드를 engine 으로 계산해 함께 반환한다.
 *
 * <p>표시 모드({@code display_mode})는 설명 프로필로, 금액·위험 판정과 분리 관리한다(FR-MY-03) — 여기서 저장만 한다.
 * 실효 스프레드 수치는 {@link EffectiveSpreadCalculator}(순수 함수)만 만든다.
 */
@UseCase
public class UserSettingsService {

    /** 표시 모드 — 초보자(설명 강함). */
    public static final String DISPLAY_MODE_BEGINNER = "beginner";
    /** 표시 모드 — 전문가(지표·한계까지). */
    public static final String DISPLAY_MODE_EXPERT = "expert";
    /** 허용하는 표시 모드 값(명세 ERD user_settings.display_mode). */
    public static final Set<String> ALLOWED_DISPLAY_MODES = Set.of(DISPLAY_MODE_BEGINNER, DISPLAY_MODE_EXPERT);
    /** 표시 모드 기본값. */
    public static final String DEFAULT_DISPLAY_MODE = DISPLAY_MODE_BEGINNER;
    /** 환전 우대율 기본값. */
    public static final double DEFAULT_FX_DISCOUNT_RATIO = 0.0;

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
     * 현재 설정과 실효 스프레드를 조회한다. 아직 설정한 적 없으면 기본값으로 응답한다(저장하지 않음).
     */
    @Transactional(readOnly = true)
    public SettingsView getSettings(UUID userId) {
        return userSettingsRepository.findByOwner_Id(userId)
                .map(this::toView)
                .orElseGet(() -> toView(null, DEFAULT_FX_DISCOUNT_RATIO, DEFAULT_DISPLAY_MODE));
    }

    /**
     * 설정을 갱신하고 갱신된 값·실효 스프레드를 반환한다. {@code null} 필드는 기존값(없으면 기본값)을 유지한다.
     *
     * @throws NotFoundException       사용자를 찾을 수 없는 경우
     * @throws InvalidRequestException 환전 우대율이 0.0~1.0 범위를 벗어나거나 표시 모드가 허용값(beginner/expert)이 아닌 경우
     */
    @Transactional
    public SettingsView updateSettings(
            UUID userId, String defaultBankCode, Double fxDiscountRatio, String displayMode) {
        // 표시 모드 검증 — 명세 허용값(beginner/expert) 밖 입력은 400. null(미변경)은 통과.
        if (displayMode != null && !ALLOWED_DISPLAY_MODES.contains(displayMode)) {
            throw new InvalidRequestException(
                    "표시 모드는 " + ALLOWED_DISPLAY_MODES + " 중 하나여야 합니다 (입력 " + displayMode + ").");
        }

        UserSettings existing = userSettingsRepository.findByOwner_Id(userId).orElse(null);

        String bankCode = defaultBankCode != null
                ? defaultBankCode
                : (existing != null ? existing.getDefaultBankCode() : null);
        double discount = fxDiscountRatio != null
                ? fxDiscountRatio
                : (existing != null ? existing.getFxDiscountRatio() : DEFAULT_FX_DISCOUNT_RATIO);
        String mode = displayMode != null
                ? displayMode
                : (existing != null ? existing.getDisplayMode() : DEFAULT_DISPLAY_MODE);

        // 우대율 검증 — 계산 계약(0~1) 위반은 사용자 입력 오류(400)로 표면화한다.
        if (discount < 0.0 || discount > 1.0) {
            throw new InvalidRequestException("환전 우대율은 0.0~1.0 이어야 합니다 (입력 " + discount + ").");
        }

        UserSettings settings;
        if (existing != null) {
            existing.update(bankCode, discount, mode);
            settings = existing;
        } else {
            User owner = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
            settings = UserSettings.create(owner, bankCode, discount, mode);
        }
        return toView(userSettingsRepository.save(settings));
    }

    private SettingsView toView(UserSettings settings) {
        return toView(settings.getDefaultBankCode(), settings.getFxDiscountRatio(), settings.getDisplayMode());
    }

    private SettingsView toView(String bankCode, double discount, String displayMode) {
        double baseSpread = BankSpreadTable.baseSpreadRatio(bankCode);
        double effectiveSpread = effectiveSpreadCalculator.effectiveSpreadRatio(baseSpread, discount);
        return new SettingsView(bankCode, discount, displayMode, baseSpread, effectiveSpread);
    }
}
