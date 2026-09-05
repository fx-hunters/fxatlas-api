package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.divurve.api.dto.ConnectivityCheckRequest;
import com.divurve.api.dto.ConnectivityCheckResponse;
import com.divurve.common.response.ApiResponse;
import com.divurve.domain.connectivity.ConnectivityCheckService;
import com.divurve.domain.connectivity.entity.ConnectivityCheck;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ConnectivityCheckControllerTest {

    private ConnectivityCheckService service;
    private ConnectivityCheckController controller;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(ConnectivityCheckService.class);
        controller = new ConnectivityCheckController(service);
    }

    @Test
    void ping_은_상태_ok_를_data_meta_로_감싸_반환한다() {
        ApiResponse<Map<String, String>> response = controller.ping();

        assertThat(response.data()).containsEntry("status", "ok");
        assertThat(response.meta()).isNotNull();
    }

    @Test
    void create_는_서비스가_저장한_행을_응답_DTO_로_감싸_반환한다() {
        when(service.create("hello")).thenReturn(ConnectivityCheck.create("hello"));

        ApiResponse<ConnectivityCheckResponse> response =
            controller.create(new ConnectivityCheckRequest("hello"));

        assertThat(response.data().message()).isEqualTo("hello");
        assertThat(response.meta()).isNotNull();
    }

    @Test
    void findAll_은_서비스_조회_결과를_DTO_리스트로_변환해_반환한다() {
        when(service.findAll())
            .thenReturn(List.of(ConnectivityCheck.create("a"), ConnectivityCheck.create("b")));

        ApiResponse<List<ConnectivityCheckResponse>> response = controller.findAll();

        assertThat(response.data()).hasSize(2);
        assertThat(response.data()).extracting(ConnectivityCheckResponse::message)
            .containsExactly("a", "b");
    }
}
