package com.divurve.domain.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.divurve.common.exception.InvalidRequestException;
import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.settings.entity.UserSettings;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import com.divurve.engine.cost.EffectiveSpreadCalculator;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UserSettingsService} 단위 테스트 — 조회(기본값 포함)·부분 수정·실효 스프레드 계산·설명 프로필 검증.
 * 스프레드 계산은 실제 engine({@link EffectiveSpreadCalculator})으로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class UserSettingsServiceTest {

    @Mock
    private UserSettingsRepository userSettingsRepository;
    @Mock
    private UserRepository userRepository;

    private final UUID userId = UUID.randomUUID();
    private final User user = User.createDemo("me@divurve.com", "나");

    private UserSettingsService service() {
        return new UserSettingsService(userSettingsRepository, userRepository, new EffectiveSpreadCalculator());
    }

    @Test
    void getSettings_은_저장된_설정과_실효_스프레드를_반환한다() {
        when(userSettingsRepository.findByOwner_Id(userId))
                .thenReturn(Optional.of(UserSettings.create(user, "081", 0.5, "standard", "dev")));

        SettingsView view = service().getSettings(userId);

        assertThat(view.defaultBankCode()).isEqualTo("081");
        assertThat(view.explainLevel()).isEqualTo("standard");
        assertThat(view.explainDomain()).isEqualTo("dev");
        assertThat(view.baseSpreadRatio()).isEqualTo(0.0165, within(1e-9));
        assertThat(view.effectiveSpreadRatio()).isEqualTo(0.00825, within(1e-9));
    }

    @Test
    void getSettings_은_설정이_없으면_기본값으로_응답한다() {
        when(userSettingsRepository.findByOwner_Id(userId)).thenReturn(Optional.empty());

        SettingsView view = service().getSettings(userId);

        assertThat(view.defaultBankCode()).isNull();
        assertThat(view.fxDiscountRatio()).isEqualTo(UserSettingsService.DEFAULT_FX_DISCOUNT_RATIO);
        assertThat(view.explainLevel()).isEqualTo(UserSettingsService.DEFAULT_EXPLAIN_LEVEL);
        assertThat(view.explainDomain()).isEqualTo(UserSettingsService.DEFAULT_EXPLAIN_DOMAIN);
        assertThat(view.effectiveSpreadRatio()).isEqualTo(BankSpreadTable.DEFAULT_BASE_SPREAD_RATIO, within(1e-9));
    }

    @Test
    void updateSettings_은_처음이면_사용자를_찾아_새_설정을_만든다() {
        when(userSettingsRepository.findByOwner_Id(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        SettingsView view = service().updateSettings(userId, "004", 0.8, "detailed", "finance");

        assertThat(view.defaultBankCode()).isEqualTo("004");
        assertThat(view.fxDiscountRatio()).isEqualTo(0.8);
        assertThat(view.explainLevel()).isEqualTo("detailed");
        assertThat(view.explainDomain()).isEqualTo("finance");
        assertThat(view.effectiveSpreadRatio()).isEqualTo(0.0035, within(1e-9));
        verify(userSettingsRepository).save(any(UserSettings.class));
    }

    @Test
    void updateSettings_은_null_필드는_기존값을_유지한다() {
        UserSettings existing = UserSettings.create(user, "081", 0.5, "standard", "dev");
        when(userSettingsRepository.findByOwner_Id(userId)).thenReturn(Optional.of(existing));
        when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        // 우대율만 갱신, 은행·설명 프로필은 null → 기존값 유지
        SettingsView view = service().updateSettings(userId, null, 0.9, null, null);

        assertThat(view.defaultBankCode()).isEqualTo("081");
        assertThat(view.explainLevel()).isEqualTo("standard");
        assertThat(view.explainDomain()).isEqualTo("dev");
        assertThat(view.fxDiscountRatio()).isEqualTo(0.9);
        assertThat(view.effectiveSpreadRatio()).isEqualTo(0.00165, within(1e-9));
        assertThat(existing.getFxDiscountRatio()).isEqualTo(0.9);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void updateSettings_은_설명선호만_바꿔도_나머지를_유지한다() {
        UserSettings existing = UserSettings.create(user, "081", 0.5, "simple", "plain");
        when(userSettingsRepository.findByOwner_Id(userId)).thenReturn(Optional.of(existing));
        when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        SettingsView view = service().updateSettings(userId, null, null, "detailed", null);

        assertThat(view.fxDiscountRatio()).isEqualTo(0.5);
        assertThat(view.explainLevel()).isEqualTo("detailed");
        assertThat(view.explainDomain()).isEqualTo("plain");
        assertThat(view.defaultBankCode()).isEqualTo("081");
    }

    @Test
    void updateSettings_은_최초설정에서_생략한_필드는_기본값을_쓴다() {
        when(userSettingsRepository.findByOwner_Id(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        SettingsView view = service().updateSettings(userId, null, null, null, null);

        assertThat(view.defaultBankCode()).isNull();
        assertThat(view.fxDiscountRatio()).isEqualTo(UserSettingsService.DEFAULT_FX_DISCOUNT_RATIO);
        assertThat(view.explainLevel()).isEqualTo(UserSettingsService.DEFAULT_EXPLAIN_LEVEL);
        assertThat(view.explainDomain()).isEqualTo(UserSettingsService.DEFAULT_EXPLAIN_DOMAIN);
        assertThat(view.effectiveSpreadRatio()).isEqualTo(BankSpreadTable.DEFAULT_BASE_SPREAD_RATIO, within(1e-9));
    }

    @Test
    void updateSettings_은_우대율이_상한을_넘으면_400() {
        when(userSettingsRepository.findByOwner_Id(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().updateSettings(userId, "004", 1.5, "detailed", "finance"))
                .isInstanceOf(InvalidRequestException.class);
        verify(userSettingsRepository, never()).save(any());
    }

    /** 상한과 하한은 {@code ||} 로 묶인 별개 분기다 — 상한만 검증하면 하한 분기가 미커버로 남는다(이슈 #40). */
    @Test
    void updateSettings_은_우대율이_음수면_400() {
        when(userSettingsRepository.findByOwner_Id(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().updateSettings(userId, "004", -0.1, "detailed", "finance"))
                .isInstanceOf(InvalidRequestException.class);
        verify(userSettingsRepository, never()).save(any());
    }

    @Test
    void updateSettings_은_설명선호가_허용값이_아니면_400() {
        assertThatThrownBy(() -> service().updateSettings(userId, "004", 0.5, "beginner", null))
                .isInstanceOf(InvalidRequestException.class);
        verify(userSettingsRepository, never()).save(any());
    }

    @Test
    void updateSettings_은_설명분야가_허용값이_아니면_400() {
        assertThatThrownBy(() -> service().updateSettings(userId, "004", 0.5, null, "cooking"))
                .isInstanceOf(InvalidRequestException.class);
        verify(userSettingsRepository, never()).save(any());
    }

    @Test
    void updateSettings_은_사용자를_찾지_못하면_404() {
        when(userSettingsRepository.findByOwner_Id(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().updateSettings(userId, "004", 0.5, "detailed", "finance"))
                .isInstanceOf(NotFoundException.class);
    }
}
