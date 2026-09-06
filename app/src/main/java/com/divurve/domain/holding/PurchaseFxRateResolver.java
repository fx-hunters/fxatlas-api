package com.divurve.domain.holding;

import com.divurve.common.architecture.UseCase;
import com.divurve.common.exception.InvalidRequestException;
import com.divurve.domain.holding.entity.PurchaseFxRate;
import com.divurve.domain.port.FxRateProvider;
import com.divurve.domain.port.RateSnapshot;
import com.divurve.engine.weight.QuoteUnitNormalizer;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 매입 시점 원화 환율을 해석한다 (FR-ON-04).
 *
 * <p>규칙
 * <ul>
 *   <li>KRW 자산 → 환율 컨텍스트를 만들지 않는다({@code null}).</li>
 *   <li>{@code purchasedAt == null} → 환율 컨텍스트 없음.</li>
 *   <li>클라이언트가 폴백 값을 넘겼으면 그대로 {@code source="manual"} 로 기록한다.</li>
 *   <li>그 외에는 {@link FxRateProvider}(예: ECOS)로 조회. 실패 시 {@link InvalidRequestException}
 *       (400 {@code VALIDATION_FAILED})으로 표면화해 프론트가 수동 입력 UI 로 유도하게 한다.</li>
 * </ul>
 *
 * <p>참고: 현재 {@link FxRateProvider} 는 최신 종가만 제공하므로 매입일이 과거여도 최신 종가를 채운다.
 * 역사 시세 조회는 후속 확장 지점이며, 어댑터 교체만으로 대응 가능하도록 도메인은 매입일을 그대로 넘기는 구조로 둔다.
 */
@UseCase
public class PurchaseFxRateResolver {

    private static final Logger log = LoggerFactory.getLogger(PurchaseFxRateResolver.class);

    /** 원화 자산은 환율 근거가 필요 없다. */
    public static final String KRW = "KRW";
    /** 수동 입력임을 표기할 때 사용하는 출처 식별자. */
    public static final String SOURCE_MANUAL = "manual";
    /**
     * 자동조회 실패를 프론트가 수동 입력 UI 로 유도할 수 있게 알려주는 에러 필드명 (FR-ON-04).
     * 에러코드는 명세 §1.3 의 6종 닫힌 집합을 지켜 {@code VALIDATION_FAILED} 하나이므로,
     * 어떤 입력을 채워야 하는지는 {@code field} 로 구분한다.
     */
    public static final String FIELD_PURCHASE_FX_RATE_KRW = "purchase_fx_rate_krw";

    private final FxRateProvider fxRateProvider;
    private final QuoteUnitNormalizer quoteUnitNormalizer;

    public PurchaseFxRateResolver(
            FxRateProvider fxRateProvider, QuoteUnitNormalizer quoteUnitNormalizer) {
        this.fxRateProvider = fxRateProvider;
        this.quoteUnitNormalizer = quoteUnitNormalizer;
    }

    /**
     * 매입 환율 컨텍스트를 해석해 반환한다. 결과가 {@code null} 이면 저장할 환율이 없음을 뜻한다.
     *
     * @param currencyCode  자산 통화 (KRW 이면 무조건 {@code null})
     * @param purchasedAt   매입일 ({@code null} 이면 자동/수동 모두 스킵)
     * @param fallbackKrw   자동 조회 실패 시 클라이언트가 넘긴 수동 입력값 (선택)
     * @throws InvalidRequestException 조회 실패했고 폴백도 없을 때
     *                                (HTTP 400 {@code VALIDATION_FAILED}, field {@link #FIELD_PURCHASE_FX_RATE_KRW})
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
            BigDecimal perUnitRate = quoteUnitNormalizer.toPerUnitRate(currencyCode, snapshot.rate());
            return new PurchaseFxRate(perUnitRate, snapshot.source(), snapshot.asOf());
        } catch (RuntimeException ex) {
            // 외부(ECOS) 원문 메시지에는 엔드포인트 URL·API 키가 실릴 수 있어 응답에 넘기지 않고 로그로만 남긴다.
            log.warn("매입 환율 자동조회 실패: pair={}", pairCode, ex);
            throw new InvalidRequestException(
                    "매입 환율 자동조회에 실패했습니다. purchase_fx_rate_krw 를 직접 입력해주세요.",
                    FIELD_PURCHASE_FX_RATE_KRW);
        }
    }
}
