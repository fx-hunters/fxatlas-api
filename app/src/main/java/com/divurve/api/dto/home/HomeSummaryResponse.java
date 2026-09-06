package com.divurve.api.dto.home;

import java.time.Instant;

/** 홈 요약 조회 응답 (GET /home/summary). */
public record HomeSummaryResponse(
        TodayActionDto todayAction,
        CurrencyStatusDto currencyStatus,
        NoticeDto notice,
        WeeklyChangeDto weeklyChange,
        MarketSummaryDto marketSummary,
        Instant referenceTime) {

    /** 오늘의 행동 — 이번주 확보액 히어로 숫자. */
    public record TodayActionDto(String heroAmount) {
    }

    /** 내 외화현황 — 보유 외화 자산 현황. */
    public record CurrencyStatusDto(int totalAssets) {
    }

    /** 주의필요 — 특이사항 또는 조치 권장사항. */
    public record NoticeDto(String message) {
    }

    /** 주간변화 — 주간 변동 요약. */
    public record WeeklyChangeDto(String summary) {
    }

    /** 시장요약 — 시장 정보 요약. */
    public record MarketSummaryDto(String summary) {
    }
}
