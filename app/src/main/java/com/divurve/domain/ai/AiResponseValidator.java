package com.divurve.domain.ai;

import com.divurve.common.architecture.UseCase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 서술의 수치를 엔진이 만든 {@code facts} 와 대조한다 (FR-AI-05, NFR-AI-02).
 * AI 는 산술을 하지 않으므로, {@code facts} 가 유일한 진실의 근원이다.
 *
 * <p><b>대조 방향을 뒤집었다</b>(리뷰 B H3). v1 은 "{@code facts} 의 숫자가 서술에 있는지"만 봐서
 * (a) 대조 대상(Number 값)이 하나도 없으면 무조건 {@code true} 를 반환했고, (b) 서술이 {@code facts} 에
 * 없는 숫자를 날조해도 잡지 못했다. 이제 <b>서술에 등장한 모든 숫자가 {@code facts} 어딘가에 있는지</b>를
 * 검사한다 — 날조된 숫자가 하나라도 있으면 실패한다.
 *
 * <p>비율·퍼센트 표기 정규화(리뷰 B M5): {@code facts} 의 {@code 0.72} 를 서술이 "72%"로 써도 같은
 * 값으로 인정한다 — {@code %} 로 끝나는 토큰은 100 으로 나눠 비교한다.
 */
@UseCase
public class AiResponseValidator {

    /** 허용 오차 — 상대 오차(값이 클 때)와 절대 오차(비율처럼 작은 값일 때) 중 큰 쪽을 쓴다. */
    private static final double RELATIVE_TOLERANCE = 0.01;
    private static final double ABSOLUTE_TOLERANCE = 0.005;

    /** 사소한 개수 표현(예: "네 문장")까지 날조로 잡지 않기 위한 최소 자릿수 컷오프. */
    private static final double TRIVIAL_COUNT_CUTOFF = 10.0;

    private static final Pattern NUMBER_TOKEN = Pattern.compile("[0-9][0-9,]*(?:\\.[0-9]+)?%?");

    /** {@code interval_80} 의 80 처럼 키 이름에 박힌 숫자. */
    private static final Pattern KEY_NUMBER = Pattern.compile("[0-9]+");

    /**
     * 서술 문장들에 등장하는 모든 숫자가 {@code facts} 안의 값과 (허용 오차 내에서) 일치하는지 확인한다.
     *
     * @param sentences AI 가 생성한 서술 문장들
     * @param facts     엔진이 계산한 검증된 사실 (중첩 Map·List 를 재귀적으로 펼쳐 비교한다)
     * @return 서술의 모든 숫자가 {@code facts} 로 설명되면 {@code true}. 날조된 숫자가 있으면 {@code false}
     */
    public boolean verify(List<String> sentences, Map<String, Object> facts) {
        List<Double> acceptable = new ArrayList<>();
        collect(facts, acceptable);

        String joined = String.join(" ", sentences == null ? List.<String>of() : sentences);
        Matcher matcher = NUMBER_TOKEN.matcher(joined);
        while (matcher.find()) {
            String token = matcher.group();
            double value = parseToken(token);
            if (isTrivialCount(token, value)) {
                continue;
            }
            if (!matchesAny(value, acceptable)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTrivialCount(String token, double value) {
        return !token.contains(".") && !token.contains("%") && Math.abs(value) < TRIVIAL_COUNT_CUTOFF;
    }

    private static double parseToken(String token) {
        boolean percent = token.endsWith("%");
        String digits = token.replace(",", "").replace("%", "");
        double value = Double.parseDouble(digits);
        return percent ? value / 100.0 : value;
    }

    private static boolean matchesAny(double value, List<Double> acceptable) {
        for (double candidate : acceptable) {
            double tolerance = Math.max(ABSOLUTE_TOLERANCE, Math.abs(candidate) * RELATIVE_TOLERANCE);
            if (Math.abs(value - candidate) <= tolerance) {
                return true;
            }
        }
        return false;
    }

    private static void collect(Object node, List<Double> out) {
        if (node instanceof Number number) {
            out.add(number.doubleValue());
        } else if (node instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                collectFromKey(String.valueOf(key), out);
                collect(value, out);
            });
        } else if (node instanceof List<?> list) {
            list.forEach(value -> collect(value, out));
        }
        // 문자열·불리언 값은 수치 그라운딩 대상이 아니다.
    }

    /**
     * 키 이름에 박힌 숫자도 허용값으로 받아들인다 (이슈 #73).
     *
     * <p><b>왜 필요한가</b> — {@code interval_80} 의 80(신뢰수준)은 값이 아니라 키에만 있다.
     * 그런데 이 구간을 설명하는 문장은 반드시 "80% 구간"이라고 써야 뜻이 통한다. 키를 보지 않으면
     * 그 80 이 날조로 잡혀 <b>모든 forecast_summary 서술이 폴백으로 떨어진다</b> — Mock 템플릿까지
     * 포함해서다(기존 템플릿도 "80% 구간은 …" 이라고 쓴다). 실 LLM 을 붙이고 나서야 드러났다.
     *
     * <p>키의 숫자도 결국 엔진이 만들어 프롬프트에 넣어 준 입력이므로, "서술의 숫자가 facts
     * 어딘가에 있는가"라는 이 검증기의 기준을 그대로 만족한다 — 완화가 아니라 누락을 메우는 것이다.
     * {@code %} 정규화 덕분에 키의 80 은 문장의 "80%"(0.8)와도 대응된다.
     */
    private static void collectFromKey(String key, List<Double> out) {
        Matcher matcher = KEY_NUMBER.matcher(key);
        while (matcher.find()) {
            double value = Double.parseDouble(matcher.group());
            out.add(value);
            out.add(value / 100.0);
        }
    }
}
