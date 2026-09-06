package com.divurve.domain.fx;

import com.divurve.common.architecture.UseCase;
import com.divurve.domain.port.FxRateProvider;
import com.divurve.domain.port.RateSnapshot;
import com.divurve.engine.weight.QuoteUnitNormalizer;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 통화 하나의 <b>1단위당 원화 환율</b>을 구하는 단일 창구 (이슈 #57).
 *
 * <p>이 조회는 서로 다른 세 곳에 <b>같은 두 줄이 그대로 복사</b>돼 있었다 —
 * {@code FxAssetValuator} · {@code ForecastService} · {@code StressRunService}.
 * 그래서 "ECOS 가 고시하지 않는 통화는 빼고 계산한다"는 수정이 {@code FxAssetValuator} 에만 적용됐고,
 * 같은 GBP 보유 상태에서 {@code /xray} 는 200, {@code /stress/runs} 는 400 이 되는
 * <b>기능마다 다른 동작</b>이 생겼다. 조회를 여기 하나로 모아 그 편차를 없앤다.
 *
 * <p>정규화까지 여기서 끝낸다 — ECOS 는 JPY 를 원/100엔으로 고시하므로
 * {@link QuoteUnitNormalizer} 로 1단위 환율에 접은 값만 도메인으로 내보낸다(명세 §1.4, ERD §4.1).
 */
@UseCase
public class PerUnitFxRates {

    private static final Logger log = LoggerFactory.getLogger(PerUnitFxRates.class);

    /** 원화 표시 고시. 외부 어댑터는 {@code <통화>_KRW} 형태의 통화쌍만 안다. */
    private static final String QUOTE_CURRENCY = "KRW";

    private final FxRateProvider fxRateProvider;
    private final QuoteUnitNormalizer quoteUnitNormalizer;

    public PerUnitFxRates(FxRateProvider fxRateProvider, QuoteUnitNormalizer quoteUnitNormalizer) {
        this.fxRateProvider = Objects.requireNonNull(fxRateProvider, "fxRateProvider");
        this.quoteUnitNormalizer =
                Objects.requireNonNull(quoteUnitNormalizer, "quoteUnitNormalizer");
    }

    /**
     * 1통화 단위당 원화 환율. 조회에 실패하면 예외가 그대로 올라간다.
     *
     * <p>환율이 없으면 계산 자체가 성립하지 않는 자리에서만 쓴다 — 예를 들어
     * 사용자가 명시적으로 요청한 통화쌍의 기준 환율.
     *
     * @param currencyCode 통화코드 (예 {@code USD})
     * @return 1단위당 원화 환율 (JPY 는 100엔 고시를 접은 값)
     */
    public BigDecimal require(String currencyCode) {
        Objects.requireNonNull(currencyCode, "currencyCode");
        RateSnapshot snapshot = fxRateProvider.fetchLatest(currencyCode + "_" + QUOTE_CURRENCY);
        if (snapshot == null) {
            throw new IllegalStateException("환율 조회 결과가 없습니다: " + currencyCode);
        }
        return quoteUnitNormalizer.toPerUnitRate(currencyCode, snapshot.rate());
    }

    /**
     * 1통화 단위당 원화 환율. 조회에 실패하면 <b>빈 값</b>을 돌려준다.
     *
     * <p>사용자 보유 자산을 훑는 자리에서 쓴다. 값을 지어내지 않고 그 통화만 빼되(FR-CM-10),
     * 통화 하나 때문에 나머지 통화의 화면까지 막지는 않는다(FR-SF-01).
     * 외부 시스템 메시지는 로그에만 남긴다 — ECOS URL 경로에 API 키가 들어 있다.
     *
     * @param currencyCode 통화코드
     * @return 1단위당 원화 환율, 조회 실패 시 {@link Optional#empty()}
     */
    public Optional<BigDecimal> find(String currencyCode) {
        try {
            return Optional.of(require(currencyCode));
        } catch (RuntimeException e) {
            log.warn("환율 조회 실패로 계산에서 제외한 통화: {}", currencyCode, e);
            return Optional.empty();
        }
    }
}
