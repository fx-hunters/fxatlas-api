package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.divurve.api.dto.asset.DepositCreateRequest;
import com.divurve.api.dto.asset.DepositResponse;
import com.divurve.api.dto.asset.DepositUpdateRequest;
import com.divurve.api.dto.asset.HoldingCreateRequest;
import com.divurve.api.dto.asset.HoldingResponse;
import com.divurve.api.dto.asset.HoldingUpdateRequest;
import com.divurve.api.dto.asset.KrwAssetCreateRequest;
import com.divurve.api.dto.asset.KrwAssetResponse;
import com.divurve.api.dto.asset.KrwAssetUpdateRequest;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.holding.DepositService;
import com.divurve.domain.holding.HoldingService;
import com.divurve.domain.holding.KrwAssetService;
import com.divurve.domain.holding.entity.Deposit;
import com.divurve.domain.holding.entity.Holding;
import com.divurve.domain.holding.entity.KrwAsset;
import com.divurve.domain.holding.entity.PurchaseFxRate;
import com.divurve.domain.user.entity.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AssetController} — 도메인 결과 → DTO 변환, data/meta 래핑.
 * v2 에서 추가된 {@code PUT/DELETE /deposits/:id} 와 {@code /krw-assets} 4종을 포함한다.
 */
@ExtendWith(MockitoExtension.class)
class AssetControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-01T15:30:00Z");

    @Mock
    private HoldingService holdingService;
    @Mock
    private DepositService depositService;
    @Mock
    private KrwAssetService krwAssetService;

    private final UUID userId = UUID.randomUUID();
    private final User user = User.createDemo("me@divurve.com", "나");

    private AssetController controller() {
        return new AssetController(holdingService, depositService, krwAssetService);
    }

    // --- 보유 종목 ---

    @Test
    void listHoldings_는_결과를_data_meta_로_래핑한다() {
        Holding holding = holding(UUID.randomUUID(), "AAPL", "USD");
        when(holdingService.list(userId)).thenReturn(List.of(holding));

        ApiResponse<List<HoldingResponse>> response = controller().listHoldings(userId);

        assertThat(response.meta()).isNotNull();
        assertThat(response.data()).singleElement().satisfies(dto -> {
            assertThat(dto.ticker()).isEqualTo("AAPL");
            assertThat(dto.purchaseFxRateKrw()).isEqualByComparingTo("1350");
            assertThat(dto.purchaseFxRateSource()).isEqualTo("ECOS");
        });
    }

    @Test
    void createHolding_은_요청을_그대로_서비스에_넘긴다() {
        UUID id = UUID.randomUUID();
        LocalDate purchasedAt = LocalDate.of(2026, 3, 10);
        when(holdingService.create(userId, "AAPL", "USD", 1.0, 100.0, purchasedAt, null))
                .thenReturn(holding(id, "AAPL", "USD"));

        HoldingResponse data = controller().createHolding(userId,
                new HoldingCreateRequest("AAPL", "USD", 1.0, 100.0, purchasedAt, null)).data();

        assertThat(data.id()).isEqualTo(id.toString());
    }

    @Test
    void updateHolding_은_수량과_평균단가만_넘긴다() {
        UUID id = UUID.randomUUID();
        when(holdingService.update(userId, id, 2.0, 200.0))
                .thenReturn(holding(id, "AAPL", "USD"));

        controller().updateHolding(userId, id.toString(), new HoldingUpdateRequest(2.0, 200.0));

        verify(holdingService).update(userId, id, 2.0, 200.0);
    }

    @Test
    void deleteHolding_은_data_가_null_이다() {
        UUID id = UUID.randomUUID();

        ApiResponse<Void> response = controller().deleteHolding(userId, id.toString());

        assertThat(response.data()).isNull();
        assertThat(response.meta()).isNotNull();
        verify(holdingService).delete(userId, id);
    }

    // --- 외화 예금 ---

    @Test
    void listDeposits_는_결과를_data_meta_로_래핑한다() {
        when(depositService.list(userId)).thenReturn(List.of(deposit(UUID.randomUUID())));

        ApiResponse<List<DepositResponse>> response = controller().listDeposits(userId);

        assertThat(response.meta()).isNotNull();
        assertThat(response.data()).singleElement()
                .satisfies(dto -> assertThat(dto.currencyCode()).isEqualTo("USD"));
    }

    @Test
    void createDeposit_은_요청을_그대로_서비스에_넘긴다() {
        UUID id = UUID.randomUUID();
        LocalDate purchasedAt = LocalDate.of(2026, 3, 10);
        when(depositService.create(userId, "USD", new BigDecimal("500"), purchasedAt, null))
                .thenReturn(deposit(id));

        DepositResponse data = controller().createDeposit(userId,
                new DepositCreateRequest("USD", new BigDecimal("500"), purchasedAt, null)).data();

        assertThat(data.id()).isEqualTo(id.toString());
    }

    @Test
    @DisplayName("PUT /deposits/:id — v2 에서 추가된 잔액 수정")
    void updateDeposit_은_잔액만_넘긴다() {
        UUID id = UUID.randomUUID();
        when(depositService.update(userId, id, new BigDecimal("800"))).thenReturn(deposit(id));

        DepositResponse data = controller()
                .updateDeposit(userId, id.toString(), new DepositUpdateRequest(new BigDecimal("800")))
                .data();

        assertThat(data.id()).isEqualTo(id.toString());
        verify(depositService).update(userId, id, new BigDecimal("800"));
    }

    @Test
    @DisplayName("DELETE /deposits/:id — v2 에서 추가")
    void deleteDeposit_은_data_가_null_이다() {
        UUID id = UUID.randomUUID();

        ApiResponse<Void> response = controller().deleteDeposit(userId, id.toString());

        assertThat(response.data()).isNull();
        verify(depositService).delete(userId, id);
    }

    // --- 원화 자산 (외화 비중의 분모) ---

    @Test
    @DisplayName("GET /krw-assets — v2 에서 추가. 외화 비중의 분모다")
    void listKrwAssets_는_결과를_data_meta_로_래핑한다() {
        when(krwAssetService.list(userId)).thenReturn(List.of(krwAsset(UUID.randomUUID())));

        ApiResponse<List<KrwAssetResponse>> response = controller().listKrwAssets(userId);

        assertThat(response.meta()).isNotNull();
        assertThat(response.data()).singleElement().satisfies(dto -> {
            assertThat(dto.kind()).isEqualTo("cash");
            assertThat(dto.label()).isEqualTo("주거래 통장");
            assertThat(dto.amountKrw()).isEqualTo(43_680_000L);
        });
    }

    @Test
    @DisplayName("POST /krw-assets — 요청을 그대로 서비스에 넘긴다")
    void createKrwAsset_은_요청을_그대로_넘긴다() {
        UUID id = UUID.randomUUID();
        when(krwAssetService.create(userId, "cash", "주거래 통장", 43_680_000L))
                .thenReturn(krwAsset(id));

        KrwAssetResponse data = controller().createKrwAsset(userId,
                new KrwAssetCreateRequest("cash", "주거래 통장", 43_680_000L)).data();

        assertThat(data.id()).isEqualTo(id.toString());
        assertThat(data.amountKrw()).isEqualTo(43_680_000L);
    }

    @Test
    @DisplayName("PUT /krw-assets/:id")
    void updateKrwAsset_은_요청을_그대로_넘긴다() {
        UUID id = UUID.randomUUID();
        when(krwAssetService.update(userId, id, "deposit", "적금", 50_000_000L))
                .thenReturn(krwAsset(id));

        controller().updateKrwAsset(userId, id.toString(),
                new KrwAssetUpdateRequest("deposit", "적금", 50_000_000L));

        verify(krwAssetService).update(userId, id, "deposit", "적금", 50_000_000L);
    }

    @Test
    @DisplayName("DELETE /krw-assets/:id")
    void deleteKrwAsset_은_data_가_null_이다() {
        UUID id = UUID.randomUUID();

        ApiResponse<Void> response = controller().deleteKrwAsset(userId, id.toString());

        assertThat(response.data()).isNull();
        verify(krwAssetService).delete(userId, id);
    }

    // --- fixtures ---

    private Holding holding(UUID id, String ticker, String currencyCode) {
        Holding holding = Holding.create(user, ticker, currencyCode, 1, 100);
        holding.assignPurchaseContext(LocalDate.of(2026, 3, 10),
                new PurchaseFxRate(new BigDecimal("1350"), "ECOS", LocalDate.of(2026, 3, 9)));
        setField(holding, "id", id);
        return holding;
    }

    private Deposit deposit(UUID id) {
        Deposit deposit = Deposit.create(user, "USD", new BigDecimal("500"));
        setField(deposit, "id", id);
        return deposit;
    }

    private KrwAsset krwAsset(UUID id) {
        KrwAsset asset = KrwAsset.create(user, "cash", "주거래 통장", 43_680_000L, NOW);
        setField(asset, "id", id);
        return asset;
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
