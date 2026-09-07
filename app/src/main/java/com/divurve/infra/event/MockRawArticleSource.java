package com.divurve.infra.event;

import com.divurve.common.architecture.ExternalAdapter;
import com.divurve.domain.port.EconEventExtractor.RawArticle;
import com.divurve.domain.port.RawArticleSource;
import java.time.Instant;
import java.util.List;

/**
 * 원문(비정형 뉴스) 수집 Mock 소스 (이슈 #74).
 *
 * <p><b>시연용 예시 데이터다.</b> 크롤러 도입은 이 이슈 범위 밖이다(대상 뉴스 매체·수집 주기가
 * 팀 결정 전 — 이슈 #74 "선행 조건"). 실 크롤러/RSS/API 연동이 붙으면 이 클래스를 교체한다.
 *
 * <p><b>출처 날조 금지</b>(FR-CM-10) — {@code sourceUrl} 은 실제 존재하는 뉴스 URL 인 것처럼
 * 꾸미지 않고 {@code demo://sample/...} 형태로 시연용임을 명시한다. 원문 텍스트는 실제 보도문이
 * 아니라 이 클래스가 직접 작성한 예시 문장이며, 검증기가 "원문에 등장한 날짜만 신뢰한다"는
 * 규약(이슈 #74 제약 2)을 테스트할 수 있도록 각 원문에 구체적인 날짜 문자열을 포함한다.
 */
@ExternalAdapter
public class MockRawArticleSource implements RawArticleSource {

    private static final String SAMPLE_SOURCE_PREFIX = "demo://sample/";

    @Override
    public List<RawArticle> fetchRecent() {
        Instant fetchedAt = Instant.now();
        return List.of(
            new RawArticle(
                SAMPLE_SOURCE_PREFIX + "fed-fomc-meeting",
                "미국 연방준비제도(Fed)는 2026년 9월 17일 연방공개시장위원회(FOMC) 정례회의를 열고 "
                    + "기준금리 결정을 발표할 예정이다. 시장은 이번 회의에서 정책 성명서의 문구 변화를 "
                    + "주시하고 있다. (본 원문은 해커톤 시연용 예시 데이터로, 실제 보도 내용이 아니다.)",
                fetchedAt),
            new RawArticle(
                SAMPLE_SOURCE_PREFIX + "ecb-rate-decision",
                "유럽중앙은행(ECB)은 2026년 9월 24일 통화정책회의를 개최하고 기준금리 결정을 "
                    + "공개할 계획이다. 유로존 물가 지표 둔화 흐름이 이번 결정에 영향을 줄 것으로 "
                    + "예상된다. (본 원문은 해커톤 시연용 예시 데이터로, 실제 보도 내용이 아니다.)",
                fetchedAt),
            new RawArticle(
                SAMPLE_SOURCE_PREFIX + "boj-policy-meeting",
                "일본은행(BOJ)은 2026년 9월 19일 금융정책결정회의 결과를 발표한다. 엔화 약세가 "
                    + "이어지는 가운데 추가 정책 조정 여부에 관심이 쏠린다. (본 원문은 해커톤 시연용 "
                    + "예시 데이터로, 실제 보도 내용이 아니다.)",
                fetchedAt)
        );
    }
}
