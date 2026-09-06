package com.divurve.infra.config;

import com.divurve.common.architecture.ExternalAdapter;
import com.divurve.domain.port.DataSourceStatus;
import com.divurve.infra.fxrate.EcosProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 설정된 API 키로 라이브 여부를 판정하는 어댑터 (이슈 #57).
 *
 * <p>판정 기준은 <b>키가 채워져 있는가</b> 하나다. 키가 없으면 모든 외부 호출이 실패하므로
 * 그 상태에서 나가는 수치는 라이브가 아니다. 반대로 키가 있으면 실제 ECOS 종가로 계산하고 있으므로
 * {@code meta.data_state} 는 {@code live} 여야 한다.
 *
 * <p><b>출처 목록에 ECOS 만 담는 이유</b> — FRED 어댑터({@code MacroIndicatorProvider})는 현재
 * 호출처가 없는 데드 코드다. 키가 설정돼 있어도 어떤 응답 수치에도 기여하지 않으므로 출처로 밝히지
 * 않는다. 쓰이지 않는 출처를 적는 것은 없는 근거를 만드는 것과 같다(FR-CM-10).
 * 거시지표를 실제로 쓰기 시작하면 그때 {@code FRED} 를 더한다.
 */
@ExternalAdapter
public class ExternalDataStatus implements DataSourceStatus {

    /** 환율 1차 출처 (NFR-DT-02). */
    static final String SOURCE_ECOS = "ECOS";

    private final EcosProperties ecosProperties;

    public ExternalDataStatus(EcosProperties ecosProperties) {
        this.ecosProperties = Objects.requireNonNull(ecosProperties, "ecosProperties");
    }

    @Override
    public boolean isLive() {
        return isConfigured(ecosProperties.apiKey());
    }

    @Override
    public List<String> sources() {
        List<String> sources = new ArrayList<>();
        if (isConfigured(ecosProperties.apiKey())) {
            sources.add(SOURCE_ECOS);
        }
        return List.copyOf(sources);
    }

    private static boolean isConfigured(String apiKey) {
        return apiKey != null && !apiKey.isBlank();
    }
}
