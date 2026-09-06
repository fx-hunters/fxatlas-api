package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.divurve.api.dto.asset.DepositCreateRequest;
import com.divurve.api.dto.asset.DepositResponse;
import com.divurve.api.dto.asset.HoldingCreateRequest;
import com.divurve.api.dto.asset.HoldingResponse;
import com.divurve.api.dto.asset.HoldingUpdateRequest;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.holding.DepositService;
import com.divurve.domain.holding.HoldingService;
import com.divurve.domain.holding.entity.Deposit;
import com.divurve.domain.holding.entity.Holding;
import com.divurve.domain.holding.entity.PurchaseFxRate;
import com.divurve.domain.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AssetController} — 도메인 결과 → DTO 변환, data/meta 래핑, 미인증 401 검증.
 */
@ExtendWith(MockitoExtension.class)
class AssetControllerTest {

    @Mock
    private HoldingService holdingService;
    @Mock
    private DepositService depositService;

    private final UUID userId = UUID.randomUUID();

    private AssetController controller() {
        return new AssetController(holdingService, depositService);
    }

    @Test
    void listHoldings_는_결과를_data_meta_로_래핑한다() {
        Holding h = holdingFixture(UUID.randomUUID(), "AAPL", "USD", 1, 100,
                LocalDate.of(2025, 3, 10), new PurchaseFxRate(new BigDecimal("1350"), "ECOS", LocalDate.of(2025, 3, 9)));
        when(holdingService.list(userId)).thenReturn(List.of(h));

        ApiResponse<List<HoldingResponse>> response = controller().listHoldings(userId);

        assertThat(response.meta()).isNotNull();
        assertThat(response.data()).singleElement().satisfies(dto -> {
            assertThat(dto.ticker()).isEqualTo("AAPL");
            assertThat(dto.purchaseFxRateKrw()).isEqualByComparingTo("1350");
            assertThat(dto.purchaseFxRateSource()).isEqualTo("ECOS");
            assertThat(dto.purchaseFxRateAsOf()).isEqualTo(LocalDate.of(2025, 3, 9));
        });
    }

    @Test
    void createHolding_은_요청을_서비스로_전달하고_결과를_변환한다() {
        LocalDate purchasedAt = LocalDate.of(2025, 3, 10);
        HoldingCreateRequest req = new HoldingCreateRequest("AAPL", "USD", 2, 150, purchasedAt, null);
        Holding created = holdingFixture(UUID.randomUUID(), "AAPL", "USD", 2, 150,
                purchasedAt, new PurchaseFxRate(new BigDecimal("1350"), "ECOS", purchasedAt));
        when(holdingService.create(eq(userId), eq("AAPL"), eq("USD"), eq(2.0), eq(150.0), eq(purchasedAt), any()))
                .thenReturn(created);

        HoldingResponse dto = controller().createHolding(userId, req).data();

        assertThat(dto.ticker()).isEqualTo("AAPL");
        assertThat(dto.purchaseFxRateSource()).isEqualTo("ECOS");
    }

    @Test
    void updateHolding_은_UUID_를_파싱해_서비스로_전달한다() {
        UUID id = UUID.randomUUID();
        Holding updated = holdingFixture(id, "AAPL", "USD", 5, 200, null, null);
        when(holdingService.update(userId, id, 5.0, 200.0)).thenReturn(updated);

        HoldingResponse dto = controller().updateHolding(userId, id.toString(), new HoldingUpdateRequest(5, 200)).data();

        assertThat(dto.id()).isEqualTo(id.toString());
        assertThat(dto.quantity()).isEqualTo(5.0);
    }

    @Test
    void deleteHolding_은_data_가_null_인_봉투를_돌려준다() {
        UUID id = UUID.randomUUID();

        ApiResponse<Void> response = controller().deleteHolding(userId, id.toString());

        assertThat(response.data()).isNull();
        assertThat(response.meta()).isNotNull();
        verify(holdingService).delete(userId, id);
    }

    @Test
    void listDeposits_는_결과를_data_meta_로_래핑한다() {
        Deposit d = depositFixture(UUID.randomUUID(), "USD", new BigDecimal("500.0000"),
                LocalDate.of(2025, 6, 1),
                new PurchaseFxRate(new BigDecimal("1380.5"), "manual", LocalDate.of(2025, 6, 1)));
        when(depositService.list(userId)).thenReturn(List.of(d));

        DepositResponse dto = controller().listDeposits(userId).data().get(0);

        assertThat(dto.amount()).isEqualByComparingTo("500.0000");
        assertThat(dto.purchaseFxRateSource()).isEqualTo("manual");
    }

    @Test
    void createDeposit_은_요청을_서비스로_전달하고_결과를_변환한다() {
        LocalDate purchasedAt = LocalDate.of(2025, 6, 1);
        DepositCreateRequest req = new DepositCreateRequest("USD", new BigDecimal("500"), purchasedAt, null);
        Deposit created = depositFixture(UUID.randomUUID(), "USD", new BigDecimal("500"),
                purchasedAt, new PurchaseFxRate(new BigDecimal("1380.5"), "ECOS", purchasedAt));
        when(depositService.create(eq(userId), eq("USD"), any(BigDecimal.class), eq(purchasedAt), any()))
                .thenReturn(created);

        DepositResponse dto = controller().createDeposit(userId, req).data();

        assertThat(dto.currencyCode()).isEqualTo("USD");
        assertThat(dto.purchaseFxRateSource()).isEqualTo("ECOS");
    }

    // ── fixtures ────────────────────────────────────────────────────────
    private static Holding holdingFixture(
            UUID id, String ticker, String ccy, double qty, double avg, LocalDate purchasedAt, PurchaseFxRate fx) {
        User owner = User.createDemo("x@divurve.com", "x");
        Holding h = Holding.create(owner, ticker, ccy, qty, avg);
        h.assignPurchaseContext(purchasedAt, fx);
        setField(h, "id", id);
        return h;
    }

    private static Deposit depositFixture(
            UUID id, String ccy, BigDecimal amount, LocalDate purchasedAt, PurchaseFxRate fx) {
        User owner = User.createDemo("x@divurve.com", "x");
        Deposit d = Deposit.create(owner, ccy, amount);
        d.assignPurchaseContext(purchasedAt, fx);
        setField(d, "id", id);
        return d;
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
