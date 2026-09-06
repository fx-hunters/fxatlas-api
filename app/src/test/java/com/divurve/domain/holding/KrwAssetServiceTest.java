package com.divurve.domain.holding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.divurve.common.exception.InvalidRequestException;
import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.holding.entity.KrwAsset;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link KrwAssetService} — 원화 자산 CRUD (API 명세 v2 §3 {@code /krw-assets}, FR-XR-07).
 * 원화 자산은 외화 비중의 <b>분모</b>다.
 */
@ExtendWith(MockitoExtension.class)
class KrwAssetServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-01T15:30:00Z");

    @Mock
    private KrwAssetRepository krwAssetRepository;
    @Mock
    private UserRepository userRepository;

    private final UUID userId = UUID.randomUUID();
    private final User user = User.createDemo("me@divurve.com", "나");

    private KrwAssetService service() {
        return new KrwAssetService(
                krwAssetRepository, userRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("목록은 소유자의 원화 자산만 반환한다")
    void 목록은_소유자_것만_반환한다() {
        KrwAsset asset = KrwAsset.create(user, "cash", "주거래 통장", 43_680_000L, NOW);
        when(krwAssetRepository.findByOwner_Id(userId)).thenReturn(List.of(asset));

        assertThat(service().list(userId)).containsExactly(asset);
    }

    @Test
    @DisplayName("총액은 외화 비중의 분모로 쓰인다")
    void 총액을_합산한다() {
        when(krwAssetRepository.findByOwner_Id(userId)).thenReturn(List.of(
                KrwAsset.create(user, "cash", "통장", 43_000_000L, NOW),
                KrwAsset.create(user, "deposit", "적금", 680_000L, NOW)));

        assertThat(service().totalKrw(userId)).isEqualTo(43_680_000L);
    }

    @Test
    @DisplayName("등록은 소유자를 붙여 저장한다")
    void 등록한다() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(krwAssetRepository.save(any(KrwAsset.class))).thenAnswer(inv -> inv.getArgument(0));

        KrwAsset created = service().create(userId, "cash", "주거래 통장", 43_680_000L);

        assertThat(created.getKind()).isEqualTo("cash");
        assertThat(created.getLabel()).isEqualTo("주거래 통장");
        assertThat(created.getAmountKrw()).isEqualTo(43_680_000L);
        assertThat(created.getOwner()).isSameAs(user);
        assertThat(created.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("등록: 사용자가 없으면 404")
    void 등록_사용자_없으면_404() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(userId, "cash", null, 1L))
                .isInstanceOf(NotFoundException.class);
        verify(krwAssetRepository, never()).save(any());
    }

    @Test
    @DisplayName("허용되지 않은 종류는 400 (ERD krw_asset_kind)")
    void 종류_검증() {
        assertThatThrownBy(() -> service().create(userId, "stock", null, 1L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("원화 자산 종류");
        assertThatThrownBy(() -> service().create(userId, null, null, 1L))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(KrwAssetService.ALLOWED_KINDS)
                .containsExactlyInAnyOrder("cash", "deposit", "domestic_equity", "other");
    }

    @Test
    @DisplayName("음수 금액은 400")
    void 음수_금액은_400() {
        assertThatThrownBy(() -> service().create(userId, "cash", null, -1L))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("0보다 작을 수 없습니다");
    }

    @Test
    @DisplayName("수정은 종류·이름표·금액을 바꾸고 갱신 시각을 남긴다")
    void 수정한다() {
        UUID assetId = UUID.randomUUID();
        KrwAsset asset = KrwAsset.create(user, "cash", "통장", 1_000L, Instant.EPOCH);
        setField(user, "id", userId);
        when(krwAssetRepository.findById(assetId)).thenReturn(Optional.of(asset));

        KrwAsset updated = service().update(userId, assetId, "deposit", "적금", 2_000L);

        assertThat(updated.getKind()).isEqualTo("deposit");
        assertThat(updated.getLabel()).isEqualTo("적금");
        assertThat(updated.getAmountKrw()).isEqualTo(2_000L);
        assertThat(updated.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("삭제는 소유자의 자산만 지운다")
    void 삭제한다() {
        UUID assetId = UUID.randomUUID();
        KrwAsset asset = KrwAsset.create(user, "cash", "통장", 1_000L, NOW);
        setField(user, "id", userId);
        when(krwAssetRepository.findById(assetId)).thenReturn(Optional.of(asset));

        service().delete(userId, assetId);

        verify(krwAssetRepository).delete(asset);
    }

    @Test
    @DisplayName("없는 자산은 404")
    void 없는_자산은_404() {
        UUID assetId = UUID.randomUUID();
        when(krwAssetRepository.findById(assetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().delete(userId, assetId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("원화 자산");
    }

    @Test
    @DisplayName("남의 자산은 존재를 숨겨 404 (NFR-SE-03)")
    void 남의_자산은_404() {
        UUID assetId = UUID.randomUUID();
        User other = User.createDemo("other@divurve.com", "남");
        setField(other, "id", UUID.randomUUID());
        when(krwAssetRepository.findById(assetId))
                .thenReturn(Optional.of(KrwAsset.create(other, "cash", null, 1L, NOW)));

        assertThatThrownBy(() -> service().update(userId, assetId, "cash", null, 1L))
                .isInstanceOf(NotFoundException.class);
        verify(krwAssetRepository, never()).delete(any(KrwAsset.class));
    }

    /** 단위테스트에서 JPA 가 채우는 UUID 를 리플렉션으로 주입한다. */
    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
