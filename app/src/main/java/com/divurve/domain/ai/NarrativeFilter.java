package com.divurve.domain.ai;

import java.util.regex.Pattern;

/**
 * AI 생성 서술에서 위험한 표현을 후처리로 차단한다 (NFR-AI-03).
 * 단정적 방향 표현("확실히 오를것", "반드시 내릴것")과
 * 투자 권유 표현("매수 추천", "투자하세요")을 마스킹한다.
 */
public class NarrativeFilter {

    // 단정적 방향 표현 (확실함, 필연성 함축)
    private static final Pattern DETERMINISTIC_PATTERN = Pattern.compile(
            "(반드시|확실(히)?|반드시|필연적으로|당연(히)?|무조건|확정적(으로)?|확실한|틀림없이|반드시)",
            Pattern.CASE_INSENSITIVE);

    // 투자 권유 표현 (매매 지시)
    private static final Pattern INVESTMENT_ADVICE_PATTERN = Pattern.compile(
            "(매수|매도|사세요|팔세요|사야|팔아야|추천|권고|투자하|구매하|추가로|모두|전량)",
            Pattern.CASE_INSENSITIVE);

    /**
     * narrative 에서 위험 표현을 찾아 마스킹한다.
     * @param narrative 원본 서술
     * @return 위험 표현이 마스킹된 서술 (또는 원본 그대로)
     */
    public String filter(String narrative) {
        if (narrative == null || narrative.isBlank()) {
            return narrative;
        }

        String filtered = narrative;
        filtered = maskDeterministicExpressions(filtered);
        filtered = maskInvestmentAdviceExpressions(filtered);

        return filtered;
    }

    /**
     * 단정적 표현을 마스킹한다.
     * @param narrative 원본 서술
     * @return 마스킹된 서술
     */
    private String maskDeterministicExpressions(String narrative) {
        return DETERMINISTIC_PATTERN.matcher(narrative)
                .replaceAll("***");
    }

    /**
     * 투자 권유 표현을 마스킹한다.
     * @param narrative 원본 서술
     * @return 마스킹된 서술
     */
    private String maskInvestmentAdviceExpressions(String narrative) {
        return INVESTMENT_ADVICE_PATTERN.matcher(narrative)
                .replaceAll("***");
    }
}
