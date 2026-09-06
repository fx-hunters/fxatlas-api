package com.divurve.domain.home;

import com.divurve.common.architecture.UseCase;
import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.holding.DepositService;
import com.divurve.domain.holding.HoldingService;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 요약 정보 유스케이스 (이슈 #21, FR-HM-01~07).
 * 오늘의 행동·외화현황·주의필요·주간변화·시장요약을 집계한다.
 * 각 영역은 기존 도메인 서비스 결과를 조합하되, 새로운 계산은 engine 모듈에서만 수행한다.
 */
@UseCase
public class HomeSummaryService {

    private final UserRepository userRepository;
    private final HoldingService holdingService;
    private final DepositService depositService;

    public HomeSummaryService(
            UserRepository userRepository, HoldingService holdingService, DepositService depositService) {
        this.userRepository = userRepository;
        this.holdingService = holdingService;
        this.depositService = depositService;
    }

    /**
     * 사용자의 홈 요약 정보를 조회한다.
     *
     * @param userId 사용자 ID
     * @return 홈 요약 정보
     * @throws NotFoundException 사용자를 찾을 수 없는 경우
     */
    @Transactional(readOnly = true)
    public HomeSummaryView getSummary(UUID userId) {
        // 사용자 존재 확인
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));

        // 현재 보유 자산 조회
        var holdings = holdingService.list(userId);
        var deposits = depositService.list(userId);

        // 기본 요약 정보 구성
        // 실제 계산(히어로 숫자, 변화, 시장 요약)은 이후 engine 모듈과 연계
        return new HomeSummaryView(
                new TodayAction(null), // 이번주 확보액 히어로 (추후 구현)
                new CurrencyStatus(holdings.size() + deposits.size()), // 외화 자산 개수
                new Notice("특이사항 없음"), // 주의필요 (추후 구현)
                new WeeklyChange(null), // 주간 변화 (추후 구현)
                new MarketSummary(null), // 시장 요약 (추후 구현)
                Instant.now());
    }

    /** 홈 화면 요약 정보. */
    public record HomeSummaryView(
            TodayAction todayAction,
            CurrencyStatus currencyStatus,
            Notice notice,
            WeeklyChange weeklyChange,
            MarketSummary marketSummary,
            Instant referenceTime) {
    }

    /** 오늘의 행동 — 이번주 확보액 히어로 숫자. */
    public record TodayAction(String heroAmount) {
    }

    /** 내 외화현황 — 보유 외화 자산 현황. */
    public record CurrencyStatus(int totalAssets) {
    }

    /** 주의필요 — 특이사항 또는 조치 권장사항. */
    public record Notice(String message) {
    }

    /** 주간변화 — 주간 변동 요약. */
    public record WeeklyChange(String summary) {
    }

    /** 시장요약 — 시장 정보 요약. */
    public record MarketSummary(String summary) {
    }
}
