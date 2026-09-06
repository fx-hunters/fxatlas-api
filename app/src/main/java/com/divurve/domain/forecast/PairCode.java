package com.divurve.domain.forecast;

import com.divurve.common.exception.InvalidRequestException;
import java.util.Locale;

/**
 * 통화쌍 코드 표기 통일 (API 명세 v2 §4 "저장 통화쌍 USDKRW · USDJPY · EURUSD").
 *
 * <p>같은 통화쌍이 세 가지 표기로 돌아다니고 있었다 — 명세·ERD 는 {@code USDKRW}, 기존 컨트롤러
 * 파라미터 예시는 {@code USD_KRW}, ECOS 설정 키({@code app.external.ecos.item-codes})도 {@code USD_KRW}.
 * 클라이언트가 어느 쪽을 보내야 하는지가 화면마다 달라지는 것을 막기 위해, <b>API 는 명세 표기
 * ({@code USDKRW})만 노출</b>하고 외부 데이터 어댑터 호출 직전에만 {@code USD_KRW} 로 바꾼다.
 *
 * <p>계산이 아니라 표기 변환이므로 engine 이 아니라 domain 에 둔다.
 */
public final class PairCode {

    private static final int CURRENCY_CODE_LENGTH = 3;

    private final String base;
    private final String quote;

    private PairCode(String base, String quote) {
        this.base = base;
        this.quote = quote;
    }

    /**
     * 클라이언트가 보낸 {@code pair_code} 를 파싱한다. {@code USDKRW} 와 {@code USD_KRW} 를 모두 받고,
     * 소문자도 허용한다 — 표기 차이로 400 을 내는 것은 사용자에게 아무 정보도 주지 않기 때문이다.
     *
     * @param raw 원본 문자열
     * @return 통화쌍
     * @throws InvalidRequestException 비어 있거나 통화코드 6자리로 해석되지 않는 경우
     */
    public static PairCode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidRequestException("pair_code 는 필수입니다.");
        }
        String compact = raw.replace("_", "").replace("/", "").trim().toUpperCase(Locale.ROOT);
        if (compact.length() != CURRENCY_CODE_LENGTH * 2 || !compact.chars().allMatch(Character::isLetter)) {
            throw new InvalidRequestException(
                    "pair_code 는 통화코드 6자리여야 합니다 (예 USDKRW). 입력: " + raw);
        }
        return new PairCode(
                compact.substring(0, CURRENCY_CODE_LENGTH),
                compact.substring(CURRENCY_CODE_LENGTH));
    }

    /** @return 기준통화 (예 {@code USD}) */
    public String base() {
        return base;
    }

    /** @return 표시통화 (예 {@code KRW}) */
    public String quote() {
        return quote;
    }

    /** @return API 응답·ERD 표기 (예 {@code USDKRW}) */
    public String canonical() {
        return base + quote;
    }

    /** @return 외부 데이터 어댑터(ECOS item-codes) 표기 (예 {@code USD_KRW}) */
    public String providerCode() {
        return base + "_" + quote;
    }

    @Override
    public String toString() {
        return canonical();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PairCode that
                && base.equals(that.base)
                && quote.equals(that.quote);
    }

    @Override
    public int hashCode() {
        return canonical().hashCode();
    }
}
