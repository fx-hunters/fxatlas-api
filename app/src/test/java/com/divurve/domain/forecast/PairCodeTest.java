package com.divurve.domain.forecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.divurve.common.exception.InvalidRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PairCode — 통화쌍 표기 통일")
class PairCodeTest {

    @Test
    @DisplayName("명세 표기(USDKRW)와 어댑터 표기(USD_KRW)를 모두 받아 하나로 만든다")
    void parseBothNotations() {
        PairCode compact = PairCode.parse("USDKRW");
        PairCode underscored = PairCode.parse("USD_KRW");

        assertEquals(compact, underscored);
        assertEquals("USDKRW", compact.canonical());
        assertEquals("USD_KRW", compact.providerCode());
        assertEquals("USD", compact.base());
        assertEquals("KRW", compact.quote());
        assertEquals("USDKRW", compact.toString());
    }

    @Test
    @DisplayName("소문자·슬래시 표기도 받는다 — 표기 차이로 400 을 내지 않는다")
    void parseLenientForms() {
        assertEquals("EURUSD", PairCode.parse("eur/usd").canonical());
        assertEquals("USDJPY", PairCode.parse(" usd_jpy ").canonical());
    }

    @Test
    @DisplayName("비어 있으면 400")
    void parseBlank() {
        assertThrows(InvalidRequestException.class, () -> PairCode.parse(null));
        assertThrows(InvalidRequestException.class, () -> PairCode.parse("  "));
    }

    @Test
    @DisplayName("통화코드 6자리로 해석되지 않으면 400")
    void parseInvalid() {
        assertThrows(InvalidRequestException.class, () -> PairCode.parse("USD"));
        assertThrows(InvalidRequestException.class, () -> PairCode.parse("USD1KR"));
    }

    @Test
    @DisplayName("같은 통화쌍은 같은 값으로 취급된다")
    void equality() {
        PairCode usdKrw = PairCode.parse("USDKRW");

        assertEquals(usdKrw, PairCode.parse("USDKRW"));
        assertEquals(usdKrw.hashCode(), PairCode.parse("USD_KRW").hashCode());
        assertTrue(usdKrw.equals(usdKrw));
        assertNotEquals(usdKrw, PairCode.parse("EURKRW"));
        assertNotEquals(usdKrw, PairCode.parse("USDJPY"));
        assertFalse(usdKrw.equals("USDKRW"));
    }
}
