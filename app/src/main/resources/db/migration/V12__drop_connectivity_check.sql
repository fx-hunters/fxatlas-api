-- 이슈 #63: connectivity_check 는 프론트·DB 연동 확인용 스캐폴딩 테이블로,
-- docs/03-api-spec-v2.md 명세에 존재하지 않고 owner 컬럼이 없어 무인증·전체 노출 상태였다.
-- 관련 엔드포인트(/api/v1/connectivity-checks)와 도메인 코드를 제거하며 테이블도 함께 드롭한다.
-- (V1__create_connectivity_check.sql 은 Flyway 체크섬 보존을 위해 수정하지 않는다.)
drop table if exists connectivity_check;
