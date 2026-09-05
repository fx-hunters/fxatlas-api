package com.divurve.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 아직 로직이 구현되지 않은 스텁 엔드포인트가 던지는 예외.
 * {@code @ResponseStatus} 로 인해 Spring 이 자동으로 501 Not Implemented 로 매핑한다.
 *
 * <p>명세서 1.3 의 {@code {error:{code,...}}} 엔벨로프는 로직 단계에서 전역 예외 핸들러로 도입한다.
 */
@ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
public class NotImplementedException extends RuntimeException {

    public NotImplementedException() {
        super("아직 구현되지 않은 엔드포인트입니다.");
    }
}
