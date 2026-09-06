package com.divurve.domain.holding;

import com.divurve.common.architecture.UseCase;
import com.divurve.common.exception.InvalidRequestException;
import com.divurve.domain.holding.entity.PurchaseFxRate;
import com.divurve.domain.port.FxRateProvider;
import com.divurve.domain.port.RateSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * 매입 시점 원화 환율을 해석한다 (FR-ON-04).
 *
 * <p>규칙
 * <ul>
 *   <li>KRW 자산 → 환율 컨텍스트를 만들지 않는다({@code null}).</li>
 *   <li>{@code purchasedAt == null} → 환율 컨텍스트 없음.</li>
 *   <li>클라이언트가 폴백 값을 넘겼으면 그대로 {@code source="manual"} 로 기록한다.</li>
 *   <li>그 외에는 {@link FxRateProvider}(예: ECOS)로 조회. 실패 시 {@link InvalidRequestException}
 *       ({@code code=FX_RATE_LOOKUP_FAILED})으로 표면화해 프론트가 수동 입력 UI 로 유도하게 한다.</li>
 * </ul>
 *
 * <p>참고: 현재 {@link FxRateProvider} 는 최신 종가만 제공하므로 매입일이 과거여도 최신 종가를 채운다.
 * 역사 시세 조회는 후속 확장 지점이며, 어댑터 교체만으로 대응 가능하도록 도메인은 매입일을 그대로 넘기는 구조로 둔다.
 */
@UseCase
public class PurchaseFxRateResolver {

    /** 원화 자산은 환율 근거가 필요 없다. */
    public static final String KRW = "KRW";
    /** 수동 입력임을 표기할 때 사용하는 출처 식별자. */
    public static final String SOURCE_MANUAL = "manual";
    /** 자동조회 실패를 프론트가 특정 UI 로 처리할 수 있도록 하는 에러 코드 (FR-ON-04). */
    public static final String ERROR_LOOKUP_FAILED = "FX_RATE_LOOKUP_FAILED";

    /** 100 단위 환산이 필요한 통화(ECOS 는 원/100엔 등으로 응답). */
    private static final Set<String> PER_HUNDRED_UNITS = Set.of("JPY");

    private final FxRateProvider fxRateProvider;

    public PurchaseFxRateResolver(FxRateProvider fxRateProvider) {
        this.fxRateProvider = fxRateProvider;
    }

    /**
     * 매입 환율 컨텍스트를 해석해 반환한다. 결과가 {@code null} 이면 저장할 환율이 없음을 뜻한다.
     *
     * @param currencyCode  자산 통화 (KRW 이면 무조건 {@code null})
     * @param purchasedAt   매입일 ({@code null} 이면 자동/수동 모두 스킵)
     * @param fallbackKrw   자동 조회 실패 시 클라이언트가 넘긴 수동 입력값 (선택)
     * @throws InvalidRequestException 조회 실패했고 폴백도 없을 때(HTTP 400, {@link #ERROR_LOOKUP_FAILED})
     */
    public PurchaseFxRate resolve(String currencyCode, LocalDate purchasedAt, BigDecimal fallbackKrw) {
        if (purchasedAt == null || KRW.equalsIgnoreCase(currencyCode)) {
            return null;
        }
        if (fallbackKrw != null) {
            return new PurchaseFxRate(fallbackKrw, SOURCE_MANUAL, purchasedAt);
        }
        String pairCode = currencyCode.toUpperCase() + "_" + KRW;
        try {
            RateSnapshot snapshot = fxRateProvider.fetchLatest(pairCode);
            return new PurchaseFxRate(normalize(currencyCode, snapshot.rate()), snapshot.source(), snapshot.asOf());
        } catch (RuntimeException ex) {
            throw new InvalidRequestException(
                    ERROR_LOOKUP_FAILED,
                    "매입 환율 자동조회에 실패했습니다. purchase_fx_rate_krw 를 직접 입력해주세요.",
                    "purchase_fx_rate_krw",
                    ex.getMessage());
        }
    }

    /** ECOS 는 JPY 를 원/100엔으로 응답하므로 1단위 환율로 정규화한다. */
    private BigDecimal normalize(String currencyCode, BigDecimal raw) {
        if (PER_HUNDRED_UNITS.contains(currencyCode.toUpperCase())) {
            return raw.divide(new BigDecimal("100"));
        }
        return raw;
    }
}
