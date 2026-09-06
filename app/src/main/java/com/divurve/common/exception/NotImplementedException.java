package com.divurve.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 아직 로직이 구현되지 않은 스텁 엔드포인트가 던지는 예외.
 * 전역 예외 핸들러가 501 Not Implemented + 에러 엔벨로프(명세 1.3)로 매핑한다.
 */
public class NotImplementedException extends ApiException {

    public NotImplementedException() {
        super(HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED", "아직 구현되지 않은 엔드포인트입니다.", null);
    }
}
