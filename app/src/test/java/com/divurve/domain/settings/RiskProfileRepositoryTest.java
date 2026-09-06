package com.divurve.domain.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.divurve.domain.RepositoryTestBase;
import com.divurve.domain.settings.entity.RiskProfile;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@link RiskProfileRepository} 통합 테스트 — Flyway V9 스키마와 엔티티 매핑 실연동 검증
 * ({@code ddl-auto=validate} 이므로 컬럼 불일치는 여기서 기동 실패로 드러난다).
 *
 * <p>특히 두 가지를 실 DB 로 확인한다.
 * <ul>
 *   <li>{@code risk_type}·{@code score} 가 NULL 인 <b>미측정 행</b>이 저장된다 — 부분 응답 재개의 근거다.</li>
 *   <li>{@code answers}·{@code detail_answers}·{@code detail_progress} JSONB 왕복.</li>
 * </ul>
 */
class RiskProfileRepositoryTest extends RepositoryTestBase {

    @Autowired
    private RiskProfileRepository riskProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 미측정_프로필은_유형과_점수가_NULL_인_채로_저장된다() {
        User owner = userRepository.save(User.createDemo("rp-a@divurve.com", "앨리스"));
        RiskProfile profile = RiskProfile.start(owner, RiskProfileService.STATUS_NOT_MEASURED);
        profile.applySimple(
                Map.of("q1", "B", "q2", "C"),
                RiskProfileService.STATUS_NOT_MEASURED,
                null, null, null, null, null);

        riskProfileRepository.saveAndFlush(profile);

        assertThat(riskProfileRepository.findByOwner_Id(owner.getId()))
                .get()
                .satisfies(p -> {
                    assertThat(p.getStatus()).isEqualTo(RiskProfileService.STATUS_NOT_MEASURED);
                    assertThat(p.getRiskType()).isNull();
                    assertThat(p.getScore()).isNull();
                    assertThat(p.getDiagnosedOn()).isNull();
                    assertThat(p.getAnswers()).containsExactlyInAnyOrderEntriesOf(Map.of("q1", "B", "q2", "C"));
                    assertThat(p.isManual()).isFalse();
                    assertThat(p.getCreatedAt()).isNotNull();
                    assertThat(p.getUpdatedAt()).isNotNull();
                });
    }

    @Test
    void 간편_진단_결과와_기준선을_저장하고_소유자로_조회한다() {
        User owner = userRepository.save(User.createDemo("rp-b@divurve.com", "밥"));
        RiskProfile profile = RiskProfile.start(owner, RiskProfileService.STATUS_NOT_MEASURED);
        profile.applySimple(
                Map.of("q1", "B", "q2", "C", "q3", "B"),
                RiskProfileService.STATUS_SIMPLE_DONE,
                "balanced",
                4,
                new BigDecimal("0.6000"),
                new BigDecimal("0.0000"),
                LocalDate.of(2026, 9, 1));

        riskProfileRepository.saveAndFlush(profile);

        assertThat(riskProfileRepository.findByOwner_Id(owner.getId()))
                .get()
                .satisfies(p -> {
                    assertThat(p.getRiskType()).isEqualTo("balanced");
                    assertThat(p.getScore()).isEqualTo(4);
                    assertThat(p.getConcentrationThreshold()).isEqualByComparingTo("0.6000");
                    assertThat(p.getSafeRatioAdjust()).isEqualByComparingTo("0.0000");
                    assertThat(p.getDiagnosedOn()).isEqualTo(LocalDate.of(2026, 9, 1));
                    assertThat(p.getAnswers()).hasSize(3);
                });
    }

    @Test
    void 상세_진단은_중단분과_완료분을_각각_JSONB_로_왕복한다() {
        User owner = userRepository.save(User.createDemo("rp-c@divurve.com", "캐럴"));
        RiskProfile profile = RiskProfile.start(owner, RiskProfileService.STATUS_NOT_MEASURED);
        profile.applySimple(
                Map.of("q1", "B", "q2", "C", "q3", "B"),
                RiskProfileService.STATUS_SIMPLE_DONE,
                "balanced", 4, new BigDecimal("0.6000"), new BigDecimal("0.0000"), LocalDate.of(2026, 9, 1));

        // 중단 — detail_progress 에 남는다.
        profile.applyDetail(Map.of("q4", "B"), false, RiskProfileService.STATUS_SIMPLE_DONE);
        riskProfileRepository.saveAndFlush(profile);
        assertThat(riskProfileRepository.findByOwner_Id(owner.getId()))
                .get()
                .satisfies(p -> {
                    assertThat(p.getDetailProgress()).containsExactlyInAnyOrderEntriesOf(Map.of("q4", "B"));
                    assertThat(p.getDetailAnswers()).isNull();
                    assertThat(p.detailAnswered()).containsExactlyInAnyOrderEntriesOf(Map.of("q4", "B"));
                });

        // 완료 — detail_answers 로 옮기고 detail_progress 를 비운다 (ERD §11).
        profile.applyDetail(
                Map.of("q4", "B", "q5", "standard", "q6", "finance"),
                true,
                RiskProfileService.STATUS_DETAIL_DONE);
        riskProfileRepository.saveAndFlush(profile);
        assertThat(riskProfileRepository.findByOwner_Id(owner.getId()))
                .get()
                .satisfies(p -> {
                    assertThat(p.getStatus()).isEqualTo(RiskProfileService.STATUS_DETAIL_DONE);
                    assertThat(p.getDetailAnswers()).hasSize(3);
                    assertThat(p.getDetailProgress()).isNull();
                    // 상세 진단이 점수·유형을 바꾸지 않았음을 실 DB 로 확인한다 (FR-DG-05).
                    assertThat(p.getRiskType()).isEqualTo("balanced");
                    assertThat(p.getScore()).isEqualTo(4);
                });
    }

    @Test
    void 상세_응답이_전혀_없으면_빈_맵을_돌려준다() {
        User owner = userRepository.save(User.createDemo("rp-d@divurve.com", "다니엘"));
        RiskProfile profile = riskProfileRepository.saveAndFlush(
                RiskProfile.start(owner, RiskProfileService.STATUS_NOT_MEASURED));

        assertThat(profile.detailAnswered()).isEmpty();
        assertThat(profile.getId()).isNotNull();
        assertThat(profile.getOwner().getId()).isEqualTo(owner.getId());
    }
}
