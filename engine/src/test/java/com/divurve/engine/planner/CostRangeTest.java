package com.divurve.engine.planner;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link CostRange} 테스트 — 비용 범위의 방향 (명세 §9.3).
 */
@DisplayName("CostRange")
class CostRangeTest {

    @Test
    @DisplayName("세 값을 그대로 담는다")
    void holdsValues() {
        CostRange range = new CostRange(1_338_257L, 1_367_258L, 1_396_882L);

        assertThat(range.lowKrw()).isEqualTo(1_338_257L);
        assertThat(range.baseKrw()).isEqualTo(1_367_258L);
        assertThat(range.highKrw()).isEqualTo(1_396_882L);
    }

    @Test
    @DisplayName("lowKrw 는 환율 하단에서 나온다 — 확보 외화와 방향이 반대다")
    void lowComesFromLowRate() {
        // 비용은 환율에 비례한다. AcquisitionRange 는 반대로 환율 상단에서 low 가 나온다.
        CostRange cost = new CostRange(1_338_257L, 1_367_258L, 1_396_882L);

        assertThat(cost.lowKrw()).isLessThan(cost.highKrw());
    }
}
