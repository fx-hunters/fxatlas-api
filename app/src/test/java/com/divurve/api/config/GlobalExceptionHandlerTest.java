package com.divurve.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.divurve.common.exception.DuplicateResourceException;
import com.divurve.common.exception.ForbiddenException;
import com.divurve.common.exception.InvalidRequestException;
import com.divurve.common.exception.NotFoundException;
import com.divurve.common.exception.NotImplementedException;
import com.divurve.common.exception.UnauthorizedException;
import com.divurve.common.response.ErrorResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpHeaders;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * {@link GlobalExceptionHandler} 단위 테스트 — 모든 예외가 명세 §1.3 의 <b>6종 에러코드</b>와
 * {@code error} 엔벨로프로 변환되는지 확인한다. Spring 컨텍스트 없이 핸들러를 직접 호출한다.
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
    void 명세_1_3_의_6종_에러코드가_그대로_매핑된다() {
        assertThat(codeAndStatus(new InvalidRequestException("잘못됨")))
                .containsExactly("VALIDATION_FAILED", "400");
        assertThat(codeAndStatus(new UnauthorizedException()))
                .containsExactly("UNAUTHORIZED", "401");
        assertThat(codeAndStatus(new ForbiddenException("타인 리소스")))
                .containsExactly("FORBIDDEN", "403");
        assertThat(codeAndStatus(new NotFoundException("없음")))
                .containsExactly("NOT_FOUND", "404");
        assertThat(codeAndStatus(new DuplicateResourceException("중복")))
                .containsExactly("DUPLICATE_RESOURCE", "409");
        assertThat(codeAndStatus(new NotImplementedException()))
                .containsExactly("NOT_IMPLEMENTED", "501");
    }

    @Test
    void field_가_있으면_에러_본문에_실린다() {
        ResponseEntity<ErrorResponse> response =
                handler.handleApiException(new InvalidRequestException("수량은 0보다 커야 합니다.", "quantity"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error().field()).isEqualTo("quantity");
    }

    @Test
    void 잘못된_요청_본문은_400_이고_파서_원문은_노출하지_않는다() {
        ResponseEntity<ErrorResponse> response = handler.handleUnreadableBody(
                new HttpMessageNotReadableException("Unexpected character at [Source: ...]", inputMessage()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().error().message()).doesNotContain("Source");
        assertThat(response.getBody().error().field()).isNull();
    }

    @Test
    void 필수_쿼리파라미터_누락은_400_이고_파라미터명을_field_로_알린다() {
        ResponseEntity<ErrorResponse> response = handler.handleMissingParameter(
                new MissingServletRequestParameterException("pair_code", "String"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().error().field()).isEqualTo("pair_code");
    }

    @Test
    void 파라미터_타입_불일치는_400_이다() {
        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(
                new MethodArgumentTypeMismatchException("not-a-uuid", java.util.UUID.class, "id", null, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().error().field()).isEqualTo("id");
    }

    @Test
    void Valid_검증_실패는_첫_위반_필드와_메시지를_담은_400_이다() {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "quantity", "수량은 0보다 커야 합니다."));

        ResponseEntity<ErrorResponse> response = handler.handleValidationFailure(
                new MethodArgumentNotValidException((MethodParameter) null, bindingResult));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().error().field()).isEqualTo("quantity");
        assertThat(response.getBody().error().message()).isEqualTo("수량은 0보다 커야 합니다.");
    }

    @Test
    void Valid_검증_실패에_필드_오류가_없으면_기본_문구를_쓴다() {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");

        ResponseEntity<ErrorResponse> response = handler.handleValidationFailure(
                new MethodArgumentNotValidException((MethodParameter) null, bindingResult));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error().field()).isNull();
        assertThat(response.getBody().error().message()).isEqualTo("요청 값이 올바르지 않습니다.");
    }

    @Test
    void 변환되지_않은_IllegalArgument_는_500_이_아니라_400_이다() {
        ResponseEntity<ErrorResponse> response =
                handler.handleIllegalArgument(new IllegalArgumentException("Invalid UUID string: abc"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().error().message()).doesNotContain("abc");
    }

    @Test
    void DB_무결성_위반은_409_DUPLICATE_RESOURCE_이고_SQL_원문은_숨긴다() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(
                new DataIntegrityViolationException("duplicate key value violates unique constraint \"uk_fx_deposits\""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error().code()).isEqualTo("DUPLICATE_RESOURCE");
        assertThat(response.getBody().error().message()).doesNotContain("uk_fx_deposits");
    }

    @Test
    void Content_Type_이_없거나_지원하지_않으면_400_VALIDATION_FAILED_이다() {
        ResponseEntity<ErrorResponse> response = handler.handleUnsupportedMediaType(
                new HttpMediaTypeNotSupportedException(
                        org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED,
                        List.of(org.springframework.http.MediaType.APPLICATION_JSON)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().error().field()).isNull();
    }

    @Test
    void 없는_경로와_지원하지_않는_메서드는_404_NOT_FOUND_로_모은다() throws Exception {
        ResponseEntity<ErrorResponse> noResource = handler.handleUnknownEndpoint(
                new NoResourceFoundException(HttpMethod.GET, "/api/v1/unknown"));
        ResponseEntity<ErrorResponse> badMethod = handler.handleUnknownEndpoint(
                new HttpRequestMethodNotSupportedException("PATCH", List.of("GET")));

        assertThat(noResource.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(noResource.getBody().error().code()).isEqualTo("NOT_FOUND");
        assertThat(badMethod.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(badMethod.getBody().error().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void 예기치_못한_예외는_500_INTERNAL_ERROR로_변환된다() {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(
                new RuntimeException("https://ecos.bok.or.kr/api/KEY/json 호출 실패"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
        // 외부 시스템 원문(엔드포인트 URL·API 키)은 절대 노출하지 않는다.
        assertThat(response.getBody().error().message()).isEqualTo("예상치 못한 오류가 발생했습니다.");
    }

    private List<String> codeAndStatus(com.divurve.common.exception.ApiException ex) {
        ResponseEntity<ErrorResponse> response = handler.handleApiException(ex);
        return List.of(response.getBody().error().code(), String.valueOf(response.getStatusCode().value()));
    }

    private HttpInputMessage inputMessage() {
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(new byte[0]);
            }

            @Override
            public HttpHeaders getHeaders() {
                return new HttpHeaders();
            }
        };
    }
}
