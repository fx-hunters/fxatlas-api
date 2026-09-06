package com.divurve.domain.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.divurve.domain.stress.entity.StressScenario;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("StressScenarioService")
class StressScenarioServiceTest {

    @Mock
    private StressScenarioRepository scenarioRepository;

    @InjectMocks
    private StressScenarioService service;

    @Test
    @DisplayName("노출 순서를 서버가 정한다 — 마스터의 sort_order 를 그대로 옮긴다")
    void listsInSortOrder() {
        when(scenarioRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(
                scenario("equity_down_krw_weak", "주가 하락 + 원화 약세", "-0.2000", "0.1000", (short) 1),
                scenario("equity_down_krw_strong", "주가 하락 + 원화 강세", "-0.2000", "-0.1000", (short) 2)));

        List<StressScenarioService.ScenarioView> views = service.listScenarios();

        assertEquals(2, views.size());
        assertEquals("equity_down_krw_weak", views.get(0).scenarioCode());
        assertEquals("주가 하락 + 원화 약세", views.get(0).nameKo());
        assertEquals(-0.20, views.get(0).equityShockPct());
        assertEquals(0.10, views.get(0).fxShockPct());
        assertEquals(-0.10, views.get(1).fxShockPct());
        assertEquals((short) 2, views.get(1).sortOrder());
        assertTrue(views.get(0).isDefault());
        assertEquals("참고 사건", views.get(0).referenceEvent());
        assertEquals("가정 설명", views.get(0).assumptionNote());
    }

    @Test
    @DisplayName("데이터가 없으면 빈 목록 — 오류가 아니다")
    void emptyIsNotAnError() {
        when(scenarioRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of());

        assertTrue(service.listScenarios().isEmpty());
    }

    private static StressScenario scenario(
            String code, String nameKo, String equityShock, String fxShock, short sortOrder) {
        StressScenario scenario;
        try {
            var constructor = StressScenario.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            scenario = constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        set(scenario, "scenarioCode", code);
        set(scenario, "nameKo", nameKo);
        set(scenario, "equityShockPct", new BigDecimal(equityShock));
        set(scenario, "fxShockPct", new BigDecimal(fxShock));
        set(scenario, "referenceEvent", "참고 사건");
        set(scenario, "assumptionNote", "가정 설명");
        set(scenario, "isDefault", true);
        set(scenario, "sortOrder", sortOrder);
        return scenario;
    }

    private static void set(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
