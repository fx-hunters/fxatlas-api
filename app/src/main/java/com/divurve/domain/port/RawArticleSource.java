package com.divurve.domain.port;

import java.util.List;

/**
 * 크롤링 등으로 확보한 최근 원문을 공급하는 포트 (이슈 #74).
 *
 * <p>이 포트는 원문 텍스트 확보만 담당한다(grounding source, AI 아님). 실제 크롤러·RSS·API 연동은
 * {@code infra} 어댑터가 구현하고, 도메인은 이 인터페이스에만 의존한다(DIP).
 */
public interface RawArticleSource {

    /**
     * 최근 수집된 원문 전체를 돌려준다.
     *
     * @return 원문 목록. 비어 있을 수 있다.
     */
    List<EconEventExtractor.RawArticle> fetchRecent();
}
