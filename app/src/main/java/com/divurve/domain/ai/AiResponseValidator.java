package com.divurve.domain.ai;

import com.divurve.common.architecture.UseCase;
import java.util.Map;

/**
 * AI 응답의 수치 필드를 엔진 계산 결과와 대조한다 (NFR-AI-02).
 * 불일치 시 응답을 폐기하고 재생성을 권고한다.
 * AI 는 산술을 하지 않으므로, 엔진 결과가 유일한 진실의 근원이다.
 */
@UseCase
public class AiResponseValidator {

    private static final double TOLERANCE = 0.01; // 1% 허용 오차

    /**
     * narrative 에서 숫자 패턴을 추출하여 metrics 와 대조한다.
     * @param narrative AI 가 생성한 서술
     * @param metrics 엔진이 계산한 수치
     * @return 일치 여부. false 면 narrative 를 폐기하고 재생성해야 한다.
     */
    public boolean validateNarrative(String narrative, Map<String, Object> metrics) {
        if (narrative == null || narrative.isBlank()) {
            return false;
        }

        for (Map.Entry<String, Object> entry : metrics.entrySet()) {
            if (entry.getValue() instanceof Number number) {
                if (!containsNumberWithTolerance(narrative, number.doubleValue())) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * narrative 에 수치가 포함되어 있는지 확인한다.
     * @param narrative 서술 문장
     * @param expectedNumber 기대 수치
     * @return 수치가 포함되어 있으면 true
     */
    private boolean containsNumberWithTolerance(String narrative, double expectedNumber) {
        // 쉼표를 먼저 제거한 후 나머지 비숫자 문자를 공백으로 변환
        String withoutCommas = narrative.replaceAll(",", "");
        String normalizedText = withoutCommas.replaceAll("[^0-9.%]", " ");
        String[] tokens = normalizedText.split("\\s+");

        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }

            try {
                String cleanedToken = token.replaceAll("%", "");
                double extractedNumber = Double.parseDouble(cleanedToken);

                if (isWithinTolerance(extractedNumber, expectedNumber)) {
                    return true;
                }
            } catch (NumberFormatException e) {
                // 토큰이 숫자가 아니면 계속
            }
        }

        return false;
    }

    /**
     * 두 수치가 허용 오차 범위 내에 있는지 확인한다.
     * @param actual 실제값
     * @param expected 기대값
     * @return 허용 오차 범위 내이면 true
     */
    private boolean isWithinTolerance(double actual, double expected) {
        if (expected == 0) {
            return actual == 0;
        }

        double percentDifference = Math.abs(actual - expected) / Math.abs(expected);
        return percentDifference <= TOLERANCE;
    }
}
