package com.divurve.domain.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.divurve.common.exception.InvalidRequestException;
import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.settings.RiskProfileService.DetailSubmission;
import com.divurve.domain.settings.entity.RiskProfile;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import com.divurve.engine.riskprofile.DetailDiagnosisMapper;
import com.divurve.engine.riskprofile.RiskProfileScorer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link RiskProfileService} 단위 테스트 (API 명세 v2 §5.1·§5.2, FR-DG-01~FR-DG-07).
 *
 * <p>고정하는 계약.
 * <ul>
 *   <li>미진단은 예외가 아니다 — 200 + {@code not_measured} + {@code riskType() == null}.</li>
 *   <li>Q1~Q3 중 하나라도 미응답이면 유형을 만들지 않는다(임의 기본 성향 금지).</li>
 *   <li>상세 진단(Q4~Q6)은 점수·유형을 바꾸지 않는다.</li>
 *   <li>부분 제출은 저장되고 다음 호출에서 병합·재개된다.</li>
 * </ul>
 *
 * <p>등급·수식어 계산은 실제 engine({@link RiskProfileScorer}·{@link DetailDiagnosisMapper})으로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class RiskProfileServiceTest {

    @Mock
    private RiskProfileRepository riskProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSettingsService userSettingsService;

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 7);
    private static final Clock CLOCK =
            Clock.fixed(TODAY.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));

    private final UUID userId = UUID.randomUUID();
    private final User user = User.createDemo("me@divurve.com", "나");

    private RiskProfileService service() {
        return new RiskProfileService(
                riskProfileRepository, userRepository, userSettingsService,
                new RiskProfileScorer(), new DetailDiagnosisMapper(), CLOCK);
    }

    private void saveEchoes() {
        when(riskProfileRepository.save(any(RiskProfile.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** 간편 진단이 끝난 상태의 프로필을 만든다 (실제 서비스 경로로 만들어 상태를 일치시킨다). */
    private RiskProfile simpleDoneProfile() {
        RiskProfile profile = RiskProfile.start(user, RiskProfileService.STATUS_NOT_MEASURED);
        profile.applySimple(
                Map.of("q1", "B", "q2", "C", "q3", "B"),
                RiskProfileService.STATUS_SIMPLE_DONE,
                RiskProfileScorer.BALANCED,
                4,
                new java.math.BigDecimal("0.6000"),
                new java.math.BigDecimal("0.0000"),
                LocalDate.of(2026, 9, 1));
        return profile;
    }

    private SettingsView settingsView(String explainLevel, String explainDomain) {
        return new SettingsView(
                null, 0.0, explainLevel, explainDomain, 0.0175, 0.0175,
                true, true, true, false, true);
    }

    // --- 조회 ------------------------------------------------------------------------------------

    @Test
    void 미진단이면_404가_아니라_not_measured_를_돌려준다() {
        when(riskProfileRepository.findByOwner_Id(userId)).thenReturn(Optional.empty());

        RiskProfileView view = service().getRiskProfile(userId);

        assertThat(view.status()).isEqualTo(RiskProfileService.STATUS_NOT_MEASURED);
        assertThat(view.riskType()).isNull();
        assertThat(view.gradeLabel()).isNull();
        assertThat(view.score()).isNull();
        assertThat(view.diagnosedOn()).isNull();
        assertThat(view.concentrationThreshold()).isNull();
        assertThat(view.simple().answers()).isEmpty();
        assertThat(view.simple().rationale()).isEmpty();
        assertThat(view.simple().mixedResponseNote()).isNull();
        assertThat(view.detail().completed()).isFalse();
        assertThat(view.detail().nextQuestion()).isEqualTo("q4");
        assertThat(view.detail().titleModifier()).isNull();
        assertThat(view.limitationNote()).isEqualTo(RiskProfileService.LIMITATION_NOTE);
    }

    @Test
    void 진단된_프로필은_명세_예시대로_상태와_근거를_돌려준다() {
        RiskProfile profile = simpleDoneProfile();
        profile.applyDetail(Map.of("q4", "B"), false, RiskProfileService.STATUS_SIMPLE_DONE);
        when(riskProfileRepository.findByOwner_Id(userId)).thenReturn(Optional.of(profile));

        RiskProfileView view = service().getRiskProfile(userId);

        assertThat(view.status()).isEqualTo(RiskProfileService.STATUS_SIMPLE_DONE);
        assertThat(view.riskType()).isEqualTo(RiskProfileScorer.BALANCED);
        assertThat(view.gradeLabel()).isEqualTo("균형항로형");
        assertThat(view.score()).isEqualTo(4);
        assertThat(view.diagnosedOn()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(view.concentrationThreshold()).isEqualTo(0.6);
        assertThat(view.simple().rationale()).hasSize(3);
        assertThat(view.simple().mixedResponseNote()).isNull();
        assertThat(view.detail().completed()).isFalse();
        assertThat(view.detail().answered()).containsEntry("q4", "B");
        assertThat(view.detail().nextQuestion()).isEqualTo("q5");
        assertThat(view.detail().titleModifier()).isEqualTo("지출 균형을 함께 고려하는");
    }

    // --- 간편 진단 (Q1~Q3) ------------------------------------------------------------------------

    @Test
    void 간편_진단은_처음이면_사용자를_찾아_새_프로필을_만든다() {
        when(riskProfileRepository.findByOwner_Id(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        saveEchoes();

        RiskProfileView view = service().submitSimple(userId, Map.of("q1", "B", "q2", "C", "q3", "B"));

        assertThat(view.status()).isEqualTo(RiskProfileService.STATUS_SIMPLE_DONE);
        assertThat(view.riskType()).isEqualTo(RiskProfileScorer.BALANCED);
        assertThat(view.score()).isEqualTo(4);
        assertThat(view.diagnosedOn()).isEqualTo(TODAY);

        ArgumentCaptor<RiskProfile> captor = ArgumentCaptor.forClass(RiskProfile.class);
        verify(riskProfileRepository).save(captor.capture());
        RiskProfile saved = captor.getValue();
        assertThat(saved.getAnswers()).containsExactlyInAnyOrderEntriesOf(Map.of("q1", "B", "q2", "C", "q3", "B"));
        assertThat(saved.getConcentrationThreshold()).isEqualByComparingTo("0.6000");
        assertThat(saved.getSafeRatioAdjust()).isEqualByComparingTo("0.0000");
        assertThat(saved.isManual()).isFalse();
    }

    @Test
    void 간편_진단은_미응답_문항이_있으면_유형을_만들지_않는다() {
        when(riskProfileRepository.findByOwner_Id(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        saveEchoes();

        RiskProfileView view = service().submitSimple(userId, Map.of("q1", "B", "q2", "C"));

        assertThat(view.status()).isEqualTo(RiskProfileService.STATUS_NOT_MEASURED);
        assertThat(view.riskType()).isNull();
        assertThat(view.score()).isNull();
        assertThat(view.diagnosedOn()).isNull();
        assertThat(view.concentrationThreshold()).isNull();
        // 그래도 답한 내용은 저장돼 재개의 근거가 된다.
        assertThat(view.simple().answers()).containsExactlyInAnyOrderEntriesOf(Map.of("q1", "B", "q2", "C"));
    }

    @Test
    void 간편_진단은_중단된_응답_위에_병합돼_재개된다() {
        RiskProfile partial = RiskProfile.start(user, RiskProfileService.STATUS_NOT_MEASURED);
        partial.applySimple(
                Map.of("q1", "B", "q2", "C"), RiskProfileService.STATUS_NOT_MEASURED,
                null, null, null, null, null);
        when(riskProfileRepository.findByOwner_Id(userId)).thenReturn(Optional.of(partial));
        saveEchoes();

        RiskProfileView view = service().submitSimple(userId, Map.of("q3", "B"));

        assertThat(view.status()).isEqualTo(RiskProfileService.STATUS_SIMPLE_DONE);
        assertThat(view.score()).isEqualTo(4);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void 간편_재진단은_상세_완료_상태를_유지한다() {
        RiskProfile profile = simpleDoneProfile();
        profile.applyDetail(
                Map.of("q4", "B", "q5", "standard", "q6", "finance"),
                true,
                RiskProfileService.STATUS_DETAIL_DONE);
        when(riskProfileRepository.findByOwner_Id(userId)).thenReturn(Optional.of(profile));
        saveEchoes();

        RiskProfileView view = service().submitSimple(userId, Map.of("q1", "D", "q2", "D", "q3", "D"));

        assertThat(view.status()).isEqualTo(RiskProfileService.STATUS_DETAIL_DONE);
        assertThat(view.riskType()).isEqualTo(RiskProfileScorer.CHALLENGING);
        assertThat(view.score()).isEqualTo(9);
    }

    @Test
    void 간편_진단은_응답이_비면_400() {
        assertThatThrownBy(() -> service().submitSimple(userId, Map.of()))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service().submitSimple(userId, null))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(riskProfileRepository, userRepository, userSettingsService);
    }

    @Test
    void 간편_진단은_Q1에서_Q3_밖의_문항을_받지_않는다() {
        assertThatThrownBy(() -> service().submitSimple(userId, Map.of("q4", "B")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("q4");
        verifyNoInteractions(riskProfileRepository, userRepository);
    }

    @Test
    void 간편_진단은_빈_선택지를_받지_않는다() {
        Map<String, String> blank = new HashMap<>();
        blank.put("q1", "  ");
        assertThatThrownBy(() -> service().submitSimple(userId, blank))
                .isInstanceOf(InvalidRequestException.class);

        Map<String, String> nullChoice = new HashMap<>();
        nullChoice.put("q1", null);
        assertThatThrownBy(() -> service().submitSimple(userId, nullChoice))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(riskProfileRepository, userRepository);
    }

    @Test
    void 간편_진단은_선택지가_A에서_D_밖이면_400() {
        when(riskProfileRepository.findByOwner_Id(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service().submitSimple(userId, Map.of("q1", "Z", "q2", "B", "q3", "B")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("A~D");
        verify(riskProfileRepository, never()).save(any());
    }

    @Test
    void 간편_진단은_사용자를_찾지_못하면_404() {
        when(riskProfileRepository.findByOwner_Id(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().submitSimple(userId, Map.of("q1", "B")))
                .isInstanceOf(NotFoundException.class);
    }

    // --- 상세 진단 (Q4~Q6) ------------------------------------------------------------------------

    @Test
    void 상세_진단은_점수와_유형을_바꾸지_않는다() {
        RiskProfile profile = simpleDoneProfile();
        when(riskProfileRepository.findByOwner_Id(userId)).thenReturn(Optional.of(profile));
        saveEchoes();
        when(userSettingsService.updateSettings(eq(userId), isNull(), isNull(), eq("standard"), isNull()))
                .thenReturn(settingsView("standard", "plain"));

        DetailSubmission submission = service().submitDetail(userId, Map.of("q4", "B", "q5", "standard"));

        assertThat(submission.profile().riskType()).isEqualTo(RiskProfileScorer.BALANCED);
        assertThat(submission.profile().score()).isEqualTo(4);
        assertThat(submission.profile().status()).isEqualTo(RiskProfileService.STATUS_SIMPLE_DONE);
        assertThat(submission.profile().detail().completed()).isFalse();
        assertThat(submission.profile().detail().nextQuestion()).isEqualTo("q6");
        assertThat(submission.profile().detail().titleModifier()).isEqualTo("지출 균형을 함께 고려하는");
        assertThat(submission.explainLevel()).isEqualTo("standard");
        assertThat(submission.explainDomain()).isEqualTo("plain");

        // 중단분은 detail_progress 에 남고 detail_answers 는 비어 있다 (ERD §11).
        assertThat(profile.getDetailProgress()).containsExactlyInAnyOrderEntriesOf(
                Map.of("q4", "B", "q5", "standard"));
        assertThat(profile.getDetailAnswers()).isNull();
    }

    @Test
    void 상세_진단은_Q6까지_마치면_detail_done_이_되고_응답을_옮긴다() {
        RiskProfile profile = simpleDoneProfile();
        profile.applyDetail(Map.of("q4", "B", "q5", "standard"), false, RiskProfileService.STATUS_SIMPLE_DONE);
        when(riskProfileRepository.findByOwner_Id(userId)).thenReturn(Optional.of(profile));
        saveEchoes();
        when(userSettingsService.updateSettings(eq(userId), isNull(), isNull(), isNull(), eq("finance")))
                .thenReturn(settingsView("standard", "finance"));

        DetailSubmission submission = service().submitDetail(userId, Map.of("q6", "finance"));

        assertThat(submission.profile().status()).isEqualTo(RiskProfileService.STATUS_DETAIL_DONE);
        assertThat(submission.profile().detail().completed()).isTrue();
        assertThat(submission.profile().detail().nextQuestion()).isNull();
        assertThat(submission.explainDomain()).isEqualTo("finance");
        assertThat(profile.getDetailAnswers()).hasSize(3);
        assertThat(profile.getDetailProgress()).isNull();
    }

    @Test
    void 상세_진단은_간편_진단_전에도_받아두되_미측정을_유지한다() {
        when(riskProfileRepository.findByOwner_Id(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        saveEchoes();
        when(userSettingsService.updateSettings(eq(userId), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(settingsView("simple", "plain"));

        DetailSubmission submission = service().submitDetail(userId, Map.of("q4", "C"));

        assertThat(submission.profile().status()).isEqualTo(RiskProfileService.STATUS_NOT_MEASURED);
        assertThat(submission.profile().riskType()).isNull();
        assertThat(submission.profile().detail().titleModifier()).isEqualTo("생활자금을 따로 떼어 두는");
    }

    @Test
    void 상세_진단은_응답이_비면_400() {
        assertThatThrownBy(() -> service().submitDetail(userId, Map.of()))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service().submitDetail(userId, null))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(riskProfileRepository, userRepository, userSettingsService);
    }

    @Test
    void 상세_진단은_Q4에서_Q6_밖의_문항을_받지_않는다() {
        assertThatThrownBy(() -> service().submitDetail(userId, Map.of("q1", "B")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("q1");
        verifyNoInteractions(riskProfileRepository, userRepository, userSettingsService);
    }

    @Test
    void 상세_진단은_빈_선택지를_받지_않는다() {
        Map<String, String> blank = new HashMap<>();
        blank.put("q4", " ");
        assertThatThrownBy(() -> service().submitDetail(userId, blank))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(riskProfileRepository, userRepository, userSettingsService);
    }

    @Test
    void 상세_진단은_Q4_선택지가_A에서_D_밖이면_400() {
        RiskProfile profile = simpleDoneProfile();
        when(riskProfileRepository.findByOwner_Id(userId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service().submitDetail(userId, Map.of("q4", "Z")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("A~D");
        verify(riskProfileRepository, never()).save(any());
        verifyNoInteractions(userSettingsService);
    }

    @Test
    void 상세_진단은_Q5가_설명_선호_허용값이_아니면_400() {
        RiskProfile profile = simpleDoneProfile();
        when(riskProfileRepository.findByOwner_Id(userId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service().submitDetail(userId, Map.of("q5", "verbose")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Q5");
        verify(riskProfileRepository, never()).save(any());
    }

    @Test
    void 상세_진단은_Q6가_설명_분야_허용값이_아니면_400() {
        RiskProfile profile = simpleDoneProfile();
        when(riskProfileRepository.findByOwner_Id(userId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service().submitDetail(userId, Map.of("q6", "cooking")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Q6");
        verify(riskProfileRepository, never()).save(any());
    }

    @Test
    void 상세_진단은_사용자를_찾지_못하면_404() {
        when(riskProfileRepository.findByOwner_Id(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().submitDetail(userId, Map.of("q4", "B")))
                .isInstanceOf(NotFoundException.class);
    }
}
