package com.divurve.api.dto.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@link SignupRequest} Bean Validation 제약 검증 (이슈 #59).
 *
 * <p>{@code onboarding_purpose} 제거 후 남은 필수 필드(email·password·name)가 비어 있으면
 * {@code @NotBlank} 가 위반을 일으켜야 한다 — {@code AuthController.signup} 의 {@code @Valid} 가
 * 이 위반을 {@code MethodArgumentNotValidException} 으로 바꾸고, {@code GlobalExceptionHandler} 가
 * 400 {@code VALIDATION_FAILED} 로 변환한다({@code GlobalExceptionHandlerTest} 가 그 변환을 검증한다).
 * 이 값이 서비스 계층의 {@code Objects.requireNonNull} 까지 내려가 500 으로 새는 것을 컨트롤러
 * 경계에서 막는다.
 */
class SignupRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void 세_필드가_모두_채워지면_위반이_없다() {
        SignupRequest request = new SignupRequest("user@example.com", "password123", "User Name");

        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void 이메일이_비어_있으면_위반이_발생한다() {
        SignupRequest request = new SignupRequest("", "password123", "User Name");

        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("email");
    }

    @Test
    void 이메일이_없으면_null_도_위반이_발생한다() {
        SignupRequest request = new SignupRequest(null, "password123", "User Name");

        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("email");
    }

    @Test
    void 비밀번호가_비어_있으면_위반이_발생한다() {
        SignupRequest request = new SignupRequest("user@example.com", " ", "User Name");

        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("password");
    }

    @Test
    void 이름이_없으면_위반이_발생한다() {
        SignupRequest request = new SignupRequest("user@example.com", "password123", null);

        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("name");
    }
}
