package com.divurve.api.controller;

import com.divurve.api.dto.market.MarketRegimeResponse;
import com.divurve.common.architecture.WebAdapter;
import com.divurve.common.response.ApiResponse;
import com.divurve.common.response.Meta;
import com.divurve.domain.market.MarketRegimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시장 상태 엔드포인트 (명세 v2 §5.10, FR-SF-01~06).
 *
 * <p>v1 의 {@code GET /system/safe-mode}({@code SystemController})를 대체한다.
 * v1 은 조건 6개를 평가해 <b>응답을 차단</b>했고({@code 503 SAFE_MODE_ACTIVE}), v2 는
 * 같은 정보를 <b>배지와 판정 근거로 알리기만</b> 한다. "안전모드" 어휘는 명세 v2 §0.1 에서 삭제됐다.
 */
@WebAdapter
@RestController
@RequestMapping("/api/v1/market")
// Swagger 로 나가는 설명에는 v1 어휘("안전모드")를 쓰지 않는다 — 그 기능은 명세 v2 §0.1 에서 삭제됐다.
@Tag(name = "Market", description = "시장 상태 배지와 판정 근거. 어떤 상태에서도 응답을 막지 않는다.")
public class MarketController {

    private final MarketRegimeService marketRegimeService;

    public MarketController(MarketRegimeService marketRegimeService) {
        this.marketRegimeService = Objects.requireNonNull(marketRegimeService, "marketRegimeService");
    }

    /**
     * 시장 상태 조회.
     *
     * <p>{@code meta.regime} 에 대표 국면을 함께 싣는다 — 시장 수치를 동반하는 응답의 규칙이다(FR-SF-02).
     * {@code model_version} 은 싣지 않는다. 예측 모델을 쓰지 않는 응답이기 때문이다.
     */
    @Operation(
            summary = "시장 상태 배지와 판정 근거",
            description = "통화쌍별 국면(calm/normal/elevated/stress)과 대표 배지(normal/caution/turbulent), "
                    + "판정 근거(checks), 클라이언트 표시 안내(guidance)를 반환한다. "
                    + "국면 → 배지 매핑 책임은 서버에 있고 클라이언트는 badge 를 그대로 그린다(명세 §2). "
                    + "guidance.keep_serving_forecast 는 항상 true 다 — 어떤 상태에서도 전망 산출을 "
                    + "멈추는 경로를 두지 않는다(FR-SF-01).")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "시장 상태. 급변 상태에서도 200 이다."))
    @GetMapping("/regime")
    public ApiResponse<MarketRegimeResponse> getRegime() {
        MarketRegimeService.MarketRegimeView view = marketRegimeService.getRegime();
        return ApiResponse.of(
                MarketRegimeResponse.from(view),
                Meta.mock(Instant.now()).withRegime(view.regime()));
    }
}
