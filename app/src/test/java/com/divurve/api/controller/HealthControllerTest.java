package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.divurve.common.response.ApiResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HealthControllerTest {

    private final HealthController controller = new HealthController();

    @Test
    void ping_은_상태_ok_를_data_meta_로_감싸_반환한다() {
        ApiResponse<Map<String, String>> response = controller.ping();

        assertThat(response.data()).containsEntry("status", "ok");
        assertThat(response.meta()).isNotNull();
    }
}
