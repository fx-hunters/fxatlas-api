package com.divurve.infra.ai;

/**
 * 모델 응답이 고정 스키마(§5 2단계)를 벗어났을 때 던진다 (이슈 #73).
 *
 * <p>{@code AiService} 가 잡아서 고정 템플릿으로 폴백한다 — 형식 위반은 서비스 실패가 아니다
 * (FR-AI-06, NFR-AI-03). 사용자에게 500 이 나가서는 안 된다.
 */
public class AiResponseFormatException extends RuntimeException {

    public AiResponseFormatException(String message) {
        super(message);
    }

    public AiResponseFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
