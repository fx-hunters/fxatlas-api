package com.divurve.engine.bucket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BucketAllocatorTest {

    private BucketAllocator allocator;

    @BeforeEach
    void setUp() {
        allocator = new BucketAllocator();
    }

    @Test
    void getSafeRatioFloor_Stock_Returns035() {
        assertThat(allocator.getSafeRatioFloor("STOCK_ACCUMULATION")).isEqualTo(0.35);
    }

    @Test
    void getSafeRatioFloor_OneTime_Returns050() {
        assertThat(allocator.getSafeRatioFloor("ONE_TIME_PURCHASE")).isEqualTo(0.50);
    }

    @Test
    void getSafeRatioFloor_Travel_Returns070() {
        assertThat(allocator.getSafeRatioFloor("TRAVEL")).isEqualTo(0.70);
    }

    @Test
    void getSafeRatioFloor_Tuition_Returns090() {
        assertThat(allocator.getSafeRatioFloor("TUITION")).isEqualTo(0.90);
    }

    @Test
    void getSafeRatioFloor_Unknown_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> allocator.getSafeRatioFloor("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("알 수 없는 목적");
    }

    @Test
    void getSafeRatioFloor_Null_ThrowsNullPointerException() {
        assertThatThrownBy(() -> allocator.getSafeRatioFloor(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void isSafeRatioValid_AboveFloor_ReturnsTrue() {
        assertThat(allocator.isSafeRatioValid("STOCK_ACCUMULATION", 0.50)).isTrue();
    }

    @Test
    void isSafeRatioValid_AtFloor_ReturnsTrue() {
        assertThat(allocator.isSafeRatioValid("STOCK_ACCUMULATION", 0.35)).isTrue();
    }

    @Test
    void isSafeRatioValid_BelowFloor_ReturnsFalse() {
        assertThat(allocator.isSafeRatioValid("STOCK_ACCUMULATION", 0.30)).isFalse();
    }

    @Test
    void isSafeRatioValid_NegativeRatio_ReturnsFalse() {
        assertThat(allocator.isSafeRatioValid("STOCK_ACCUMULATION", -0.1)).isFalse();
    }

    @Test
    void isSafeRatioValid_GreaterThanOne_ReturnsFalse() {
        assertThat(allocator.isSafeRatioValid("STOCK_ACCUMULATION", 1.1)).isFalse();
    }

    @Test
    void getDefaultSafeRatio_Returns070() {
        assertThat(allocator.getDefaultSafeRatio()).isEqualTo(0.70);
    }
}
