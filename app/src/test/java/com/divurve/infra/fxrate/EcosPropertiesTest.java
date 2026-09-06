package com.divurve.infra.fxrate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EcosPropertiesTest {

    @Test
    void applies_defaults_when_fields_are_null_or_blank() {
        EcosProperties p = new EcosProperties(null, "KEY", null, null);

        assertThat(p.baseUrl()).isEqualTo("https://ecos.bok.or.kr/api");
        assertThat(p.statCode()).isEqualTo("731Y001");
        assertThat(p.itemCodes()).isEmpty();
    }

    @Test
    void applies_defaults_when_fields_are_blank_strings() {
        EcosProperties p = new EcosProperties("", "KEY", "", Map.of("USD_KRW", "0000001"));

        assertThat(p.baseUrl()).isEqualTo("https://ecos.bok.or.kr/api");
        assertThat(p.statCode()).isEqualTo("731Y001");
        assertThat(p.itemCodes()).containsEntry("USD_KRW", "0000001");
    }

    @Test
    void keeps_provided_values() {
        EcosProperties p = new EcosProperties("http://x", "KEY", "999X999", Map.of("A_B", "1"));

        assertThat(p.baseUrl()).isEqualTo("http://x");
        assertThat(p.statCode()).isEqualTo("999X999");
        assertThat(p.itemCodes()).containsEntry("A_B", "1");
    }
}
