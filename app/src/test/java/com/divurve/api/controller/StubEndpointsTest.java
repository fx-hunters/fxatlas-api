package com.divurve.api.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.divurve.common.exception.NotImplementedException;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * 스텁 엔드포인트 검증 — 모든 컨트롤러 메서드가 {@link NotImplementedException}(→ 501)을 던지는지 확인한다.
 *
 * <p>로직이 붙기 전까지 JaCoCo 100% 게이트를 통과시키는 역할도 한다. 컨트롤러를 직접 {@code new} 로
 * 생성해 메서드를 호출하므로 Spring 컨텍스트·MockMvc 가 필요 없다(기존 ConnectivityCheckControllerTest 방식).
 * 각 메서드 호출로 본문의 {@code throw} 라인과 예외 생성자가 실행되어 커버된다.
 */
class StubEndpointsTest {

    // signup/login/refresh 스텁은 authDemoService 를 건드리지 않으므로 null 로 충분하다.
    // demo() 는 실구현되어 이 스텁 목록에서 제외되고, 매핑은 AuthControllerTest 가 검증한다.
    private final AuthController auth = new AuthController(null);
    // risk-profile/settings 는 실구현되어 이 스텁 목록에서 제외 — MeControllerTest 가 매핑을 검증한다.
    // 남은 스텁(getProfile/updateProfile/updateNotifications)은 서비스를 건드리지 않으므로 null 로 충분하다.
    private final MeController me = new MeController(null, null);
    // holdings/deposits 는 실구현되어 이 스텁 목록에서 제외 — AssetControllerTest 가 매핑을 검증한다.
    private final XrayController xray = new XrayController();
    private final FitController fit = new FitController();
    // forecast 는 실구현되어 이 스텁 목록에서 제외 — ForecastControllerTest 가 매핑을 검증한다.
    private final GoalController goal = new GoalController();
    private final PlanController plan = new PlanController();
    // currencies/fx-terms 는 실구현되어 이 스텁 목록에서 제외 — MasterControllerTest 가 매핑을 검증한다.
    private final SystemController system = new SystemController();
    private final AiController ai = new AiController();

    @TestFactory
    List<DynamicTest> 모든_스텁_엔드포인트는_501_NotImplemented_를_던진다() {
        List<ThrowingCallable> calls = List.of(
                // Auth
                () -> auth.signup(null),
                () -> auth.login(null),
                () -> auth.refresh(null),
                // MyPage
                me::getProfile,
                () -> me.updateProfile(null),
                () -> me.updateNotifications(null),
                // X-ray
                xray::getXray,
                () -> xray.getAttribution(null, null),
                () -> xray.applyStress(null),
                // Fit
                fit::getConcentration,
                () -> fit.simulate(null),
                // Goal
                goal::listGoals,
                () -> goal.createGoal(null),
                () -> goal.getGoal("id"),
                () -> goal.updateGoal("id", null),
                () -> goal.deleteGoal("id"),
                // Plan
                () -> plan.preview(null),
                () -> plan.createPlan("id", null),
                () -> plan.listPlanVersions("id"),
                () -> plan.getActivePlan("id"),
                () -> plan.completeStep("id", 1, null),
                () -> plan.skipStep("id", 1),
                // System
                system::getHomeSummary,
                system::getSafeMode,
                system::listNotifications,
                // AI
                () -> ai.parseGoal(null),
                () -> ai.explain(null));

        return calls.stream()
                .map(call -> DynamicTest.dynamicTest(
                        "501 을 던진다",
                        () -> assertThatThrownBy(call).isInstanceOf(NotImplementedException.class)))
                .toList();
    }
}
