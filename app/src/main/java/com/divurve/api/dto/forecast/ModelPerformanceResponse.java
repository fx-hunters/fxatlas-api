package com.divurve.api.dto.forecast;

/** 모델 성적표 응답 (GET /forecast/model-performance, 명세 3.6). */
public record ModelPerformanceResponse(
        String pairCode,
        int horizonDays,
        Model model,
        RandomWalk randomWalk,
        Validation validation,
        String note) {

    /** 모델 성능 지표. */
    public record Model(double hitRate, double mae, double coverage80, double avgWidth) {
    }

    /** 랜덤워크 벤치마크. */
    public record RandomWalk(double hitRate, double mae) {
    }

    /** 검증 방법. */
    public record Validation(String method, int folds, boolean leakageGuard) {
    }
}
