package com.divurve.domain.holding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.holding.entity.Holding;
import com.divurve.domain.holding.entity.PurchaseFxRate;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link HoldingService} — CRUD + 소유자 격리(NFR-SE-03) + 매입 환율 컨텍스트 통합.
 */
@ExtendWith(MockitoExtension.class)
class HoldingServiceTest {

    @Mock
    private HoldingRepository holdingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PurchaseFxRateResolver purchaseFxRateResolver;

    private final UUID userId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final User user = User.createDemo("me@divurve.com", "나");
    private final User other = User.createDemo("other@divurve.com", "타인");

    private HoldingService service() {
        return new HoldingService(holdingRepository, userRepository, purchaseFxRateResolver);
    }

    @Test
    void list_는_소유자의_보유_종목만_반환한다() {
        Holding h = Holding.create(user, "AAPL", "USD", 1, 100);
        when(holdingRepository.findByOwner_Id(userId)).thenReturn(List.of(h));

        assertThat(service().list(userId)).containsExactly(h);
    }

    @Test
    void create_는_매입_환율_컨텍스트를_붙여_저장한다() {
        LocalDate purchasedAt = LocalDate.of(2025, 3, 10);
        PurchaseFxRate fx = new PurchaseFxRate(new BigDecimal("1350"), "ECOS", purchasedAt);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(purchaseFxRateResolver.resolve("USD", purchasedAt, null)).thenReturn(fx);
        when(holdingRepository.save(any(Holding.class))).thenAnswer(inv -> inv.getArgument(0));

        Holding created = service().create(userId, "AAPL", "USD", 2.0, 150.0, purchasedAt, null);

        assertThat(created.getTicker()).isEqualTo("AAPL");
        assertThat(created.getPurchasedAt()).isEqualTo(purchasedAt);
        assertThat(created.getPurchaseFxRateKrw()).isEqualByComparingTo("1350");
        assertThat(created.getPurchaseFxRateSource()).isEqualTo("ECOS");
    }

    @Test
    void create_는_사용자가_없으면_404_를_던진다() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(userId, "AAPL", "USD", 1, 100, null, null))
                .isInstanceOf(NotFoundException.class);
        verify(purchaseFxRateResolver, never()).resolve(any(), any(), any());
    }

    @Test
    void update_는_소유_종목의_수량_평균단가를_수정한다() {
        assignUserId(user, userId);
        Holding h = Holding.create(user, "AAPL", "USD", 1, 100);
        UUID holdingId = UUID.randomUUID();
        assignId(h, holdingId);
        when(holdingRepository.findById(holdingId)).thenReturn(Optional.of(h));

        Holding updated = service().update(userId, holdingId, 5.0, 200.0);

        assertThat(updated.getQuantity()).isEqualTo(5.0);
        assertThat(updated.getAvgPrice()).isEqualTo(200.0);
    }

    @Test
    void update_는_없는_종목이면_404_를_던진다() {
        UUID holdingId = UUID.randomUUID();
        when(holdingRepository.findById(holdingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().update(userId, holdingId, 1, 1))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_는_타인_소유_종목_접근도_404_로_숨긴다() {
        Holding h = Holding.create(other, "AAPL", "USD", 1, 100);
        UUID holdingId = UUID.randomUUID();
        assignId(h, holdingId);
        assignUserId(other, otherUserId);
        when(holdingRepository.findById(holdingId)).thenReturn(Optional.of(h));

        assertThatThrownBy(() -> service().update(userId, holdingId, 1, 1))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_는_소유_종목이면_repository_delete_를_호출한다() {
        Holding h = Holding.create(user, "AAPL", "USD", 1, 100);
        UUID holdingId = UUID.randomUUID();
        assignId(h, holdingId);
        assignUserId(user, userId);
        when(holdingRepository.findById(holdingId)).thenReturn(Optional.of(h));

        service().delete(userId, holdingId);

        verify(holdingRepository).delete(h);
    }

    @Test
    void delete_는_타인_소유_종목이면_404() {
        Holding h = Holding.create(other, "AAPL", "USD", 1, 100);
        UUID holdingId = UUID.randomUUID();
        assignId(h, holdingId);
        assignUserId(other, otherUserId);
        when(holdingRepository.findById(holdingId)).thenReturn(Optional.of(h));

        assertThatThrownBy(() -> service().delete(userId, holdingId))
                .isInstanceOf(NotFoundException.class);
        verify(holdingRepository, never()).delete(any(Holding.class));
    }

    // ── 유틸 : 단위테스트에서 JPA 가 채우는 UUID 를 리플렉션으로 주입한다 ────────────────────────
    private static void assignId(Holding h, UUID id) {
        setField(h, "id", id);
    }

    private void assignUserId(User u, UUID id) {
        setField(u, "id", id);
        // update 케이스에서 홀딩의 소유자 참조가 user 또는 other 인스턴스이므로 그 id 를 채워두면 비교가 성립한다.
    }

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
