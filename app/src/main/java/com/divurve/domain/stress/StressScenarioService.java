package com.divurve.domain.stress;

import com.divurve.common.architecture.UseCase;
import com.divurve.domain.stress.entity.StressScenario;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스트레스 시나리오 마스터 조회 UseCase ({@code GET /stress/scenarios}, 요구사항 FR-ST-01).
 *
 * <p>기본 2종(주가 하락 + 원화 약세 / 주가 하락 + 원화 강세)은 V11 마이그레이션의 시드 데이터다.
 * 목록 순서는 {@code sort_order} 로 서버가 고정한다 — 클라이언트가 재정렬하지 않는다(NFR-UI-01).
 */
@UseCase
public class StressScenarioService {

    private final StressScenarioRepository scenarioRepository;

    public StressScenarioService(StressScenarioRepository scenarioRepository) {
        this.scenarioRepository = Objects.requireNonNull(scenarioRepository, "scenarioRepository is null");
    }

    /**
     * 시나리오 목록을 노출 순서대로 조회한다.
     *
     * <p>데이터가 없으면 빈 목록이다 — 오류가 아니다(명세 §1.3 "데이터 없음 → 200 + 빈 배열").
     *
     * @return 시나리오 목록
     */
    @Transactional(readOnly = true)
    public List<ScenarioView> listScenarios() {
        return scenarioRepository.findAllByOrderBySortOrderAsc().stream()
                .map(StressScenarioService::toView)
                .toList();
    }

    private static ScenarioView toView(StressScenario scenario) {
        return new ScenarioView(
                scenario.getScenarioCode(),
                scenario.getNameKo(),
                scenario.getEquityShockPct().doubleValue(),
                scenario.getFxShockPct().doubleValue(),
                scenario.getReferenceEvent(),
                scenario.getAssumptionNote(),
                scenario.isDefault(),
                scenario.getSortOrder()
        );
    }

    /**
     * 시나리오 마스터 한 건 (명세 §5.9 {@code scenario} 블록과 같은 어휘).
     *
     * @param scenarioCode    시나리오 코드 (예 {@code equity_down_krw_weak})
     * @param nameKo          한국어 이름
     * @param equityShockPct  가정 주가 충격률 (음수 = 하락)
     * @param fxShockPct      가정 환율 충격률 (양수 = 원화 약세, FR-CM-05)
     * @param referenceEvent  참고한 실제 사건 (없으면 null)
     * @param assumptionNote  적용 순서·가정 설명 (없으면 null)
     * @param isDefault       기본 제공 시나리오 여부
     * @param sortOrder       화면 노출 순서
     */
    public record ScenarioView(
            String scenarioCode,
            String nameKo,
            double equityShockPct,
            double fxShockPct,
            String referenceEvent,
            String assumptionNote,
            boolean isDefault,
            short sortOrder
    ) {
    }
}
