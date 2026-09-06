package com.divurve.domain.ai;

import com.divurve.common.architecture.UseCase;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 생성 서술에서 금지 표현을 탐지한다 (FR-AI-07, NFR-RG-01).
 * 단정적 방향 표현("반드시", "확실히")과 투자 권유·수익 보장 표현("매수하세요", "수익을 보장")을 찾는다.
 *
 * <p><b>차단은 마스킹이 아니라 폐기다</b>(§5 4단계). v1 은 {@code ***} 로 치환해 "지금 *** 시점입니다"
 * 같은 훼손 문장을 그대로 사용자에게 보여줬다(리뷰 B M1). 이 클래스는 금지 표현이 있는지만 알리고,
 * {@link AiService} 가 그 결과를 보고 응답 전체를 고정 템플릿(fallback)으로 바꾼다.
 *
 * <p>패턴은 <b>완결된 표현</b>만 잡는다 — v1 의 {@code 투자하}·{@code 추천}·{@code 모두}·{@code 추가로}
 * 단일 형태소 매칭은 "투자하지 않습니다"·"다양하게 분산 추천됩니다" 같은 정상 서술까지 오탐했다
 * (리뷰 B M1). 금지어 사전을 정규식 대 분류기 중 무엇으로 확정할지는 문서 §8 미결정이다.
 */
@UseCase
public class NarrativeFilter {

    // 단정적 방향 표현 — 확실함·필연성을 함축한다.
    private static final Pattern ABSOLUTE_PATTERN = Pattern.compile(
            "(반드시|확실히|틀림없이|무조건|확정적으로|필연적으로)");

    // 수익 보장·원금 보장 표현 — 투자 권유 규제 대상(NFR-RG-01).
    private static final Pattern GUARANTEE_PATTERN = Pattern.compile(
            "(수익을?\\s*보장|원금을?\\s*보장|손실이?\\s*없|확정\\s*수익)");

    // 완결된 매매 지시 표현만 잡는다 — 활용형 일부("투자하지")는 걸리지 않는다.
    private static final Pattern ADVICE_PATTERN = Pattern.compile(
            "(매수하세요|매도하세요|매수하십시오|매도하십시오|사세요|파세요|투자하세요|구매하세요"
                    + "|매수를\\s*추천|매도를\\s*추천|지금\\s*매수|지금\\s*매도)");

    /**
     * 서술 문장에서 금지 표현을 찾는다.
     *
     * @param text 검사할 문장(들을 합친 텍스트)
     * @return 발견된 금지 표현 목록(중복 제거, 발견 순서). 없으면 빈 목록
     */
    public List<String> detect(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> found = new ArrayList<>();
        collect(ABSOLUTE_PATTERN, text, found);
        collect(GUARANTEE_PATTERN, text, found);
        collect(ADVICE_PATTERN, text, found);
        return found;
    }

    private static void collect(Pattern pattern, String text, List<String> found) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String phrase = matcher.group().trim();
            if (!found.contains(phrase)) {
                found.add(phrase);
            }
        }
    }
}
