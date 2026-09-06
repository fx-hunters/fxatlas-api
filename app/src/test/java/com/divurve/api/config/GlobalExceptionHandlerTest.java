package com.divurve.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.divurve.common.exception.InvalidRequestException;
import com.divurve.common.exception.NotFoundException;
import com.divurve.common.exception.NotImplementedException;
import com.divurve.common.response.ErrorResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * {@link GlobalExceptionHandler} 단위 테스트 — 각 예외가 지정 상태코드 + {@code error} 엔벨로프로 변환되는지 확인한다.
 * Spring 컨텍스트 없이 핸들러를 직접 호출한다(기존 컨트롤러 단위 테스트 방식).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void ApiException_은_지정_상태코드와_에러코드로_변환된다() {
        ResponseEntity<ErrorResponse> response =
                handler.handleApiException(new NotFoundException("없습니다."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().error().message()).isEqualTo("없습니다.");
        assertThat(response.getBody().error().field()).isNull();
    }

    @Test
    void NotImplemented_는_501로_변환된다() {
        ResponseEntity<ErrorResponse> response =
                handler.handleApiException(new NotImplementedException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
        assertThat(response.getBody().error().code()).isEqualTo("NOT_IMPLEMENTED");
    }

    @Test
    void 상세코드_필드_상세를_담은_InvalidRequest_는_그대로_실린다() {
        ResponseEntity<ErrorResponse> response = handler.handleApiException(
                new InvalidRequestException(
                        "SAFE_RATIO_BELOW_FLOOR", "안전 버킷 하한 위반", "safe_ratio", Map.of("floor", 0.90)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error().code()).isEqualTo("SAFE_RATIO_BELOW_FLOOR");
        assertThat(response.getBody().error().field()).isEqualTo("safe_ratio");
        assertThat(response.getBody().error().detail()).isEqualTo(Map.of("floor", 0.90));
    }

    @Test
    void 예기치_못한_예외는_500_INTERNAL_ERROR로_변환된다() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUnexpected(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
        // 내부 메시지는 노출하지 않는다.
        assertThat(response.getBody().error().message()).doesNotContain("boom");
    }
}
