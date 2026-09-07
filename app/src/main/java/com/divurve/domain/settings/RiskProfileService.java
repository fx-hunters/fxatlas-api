package com.divurve.domain.settings;

import com.divurve.common.architecture.UseCase;
import com.divurve.common.exception.InvalidRequestException;
import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.settings.entity.RiskProfile;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import com.divurve.engine.riskprofile.DetailDiagnosisMapper;
import com.divurve.engine.riskprofile.RiskAssessment;
import com.divurve.engine.riskprofile.RiskProfileScorer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 위험성향 진단 유스케이스 (API 명세 v2 §5.1·§5.2, 요구사항 v2 §4.3 FR-DG).
 *
 * <p>규칙 세 가지가 이 서비스의 전부다.
 * <ol>
 *   <li><b>Q1~Q3 만 점수·유형을 만든다.</b> 하나라도 미응답이면 {@code not_measured} 이고 유형은 {@code null} 이다
 *       — 임의의 기본 성향을 채워 넣지 않는다(FR-DG-02, FR-IS-06).</li>
 *   <li><b>Q4~Q6 은 점수를 바꾸지 않는다.</b> 제목 수식어와 설명 선호에만 반영된다(FR-DG-05).</li>
 *   <li><b>중단하면 저장하고 재개한다.</b> 부분 제출을 그대로 저장하고 첫 미응답 문항을 커서로 돌려준다(FR-DG-04).</li>
 * </ol>
 *
 * <p>수치(등급·점수·기준선)는 {@link RiskProfileScorer}(결정론적 순수 함수)만 만든다 — 이 서비스는 조회·영속화·검증만 한다.
 */
@UseCase
public class RiskProfileService {

    /** 진단 상태 — 미측정 (ERD diagnosis_status). */
    public static final String STATUS_NOT_MEASURED = "not_measured";
    /** 진단 상태 — 간편 진단 완료. */
    public static final String STATUS_SIMPLE_DONE = "simple_done";
    /** 진단 상태 — 상세 진단까지 완료. */
    public static final String STATUS_DETAIL_DONE = "detail_done";

    /** MVP 가설 한계 고지 (명세 §5.1 {@code limitation_note}). */
    public static final String LIMITATION_NOTE =
            "이 판정은 해커톤 MVP용 가설이며 통계적으로 검증된 금융회사 표준 진단이 아닙니다.";

    /** 설명 선호를 정하는 상세 문항. */
    public static final String QUESTION_EXPLAIN_LEVEL = "q5";
    /** 익숙한 설명 분야를 정하는 상세 문항. */
    public static final String QUESTION_EXPLAIN_DOMAIN = "q6";

    /** 기준선 컬럼 스케일 (ERD numeric(5,4)). */
    private static final int RATIO_SCALE = 4;

    private final RiskProfileRepository riskProfileRepository;
    private final UserRepository userRepository;
    private final UserSettingsService userSettingsService;
    private final RiskProfileScorer riskProfileScorer;
    private final DetailDiagnosisMapper detailDiagnosisMapper;
    private final Clock clock;

    public RiskProfileService(
            RiskProfileRepository riskProfileRepository,
            UserRepository userRepository,
            UserSettingsService userSettingsService,
            RiskProfileScorer riskProfileScorer,
            DetailDiagnosisMapper detailDiagnosisMapper,
            Clock clock) {
        this.riskProfileRepository = riskProfileRepository;
        this.userRepository = userRepository;
        this.userSettingsService = userSettingsService;
        this.riskProfileScorer = riskProfileScorer;
        this.detailDiagnosisMapper = detailDiagnosisMapper;
        this.clock = clock;
    }

    /**
     * 현재 진단 상태와 근거를 조회한다. <b>미진단이어도 예외를 던지지 않는다</b> —
     * 명세 §5.1 대로 200 + {@code status=not_measured} + {@code riskType=null} 로 응답한다.
     */
    @Transactional(readOnly = true)
    public RiskProfileView getRiskProfile(UUID userId) {
        return riskProfileRepository.findByOwner_Id(userId)
                .map(this::toView)
                .orElseGet(this::notMeasuredView);
    }

    /**
     * 간편 진단(Q1~Q3) 응답을 제출한다. 부분 제출이 허용되며 기존 응답 위에 병합된다(FR-DG-01·FR-DG-04).
     * Q1~Q3 이 모두 채워졌을 때만 점수·유형·기준선이 산출된다.
     *
     * @param answers 문항 코드({@code q1}~{@code q3}) → 선택지 코드({@code A}~{@code D})
     * @throws InvalidRequestException 응답이 비었거나, 문항/선택지 코드가 계약을 벗어난 경우
     * @throws NotFoundException       사용자를 찾을 수 없는 경우
     */
    @Transactional
    public RiskProfileView submitSimple(UUID userId, Map<String, String> answers) {
        Map<String, String> submitted = require(answers, RiskProfileScorer.SIMPLE_QUESTIONS, "간편 진단");

        RiskProfile profile = loadOrStart(userId);
        Map<String, String> merged = merge(profile.getAnswers(), submitted);
        Optional<RiskAssessment> assessment = assess(merged);

        profile.applySimple(
                merged,
                resolveStatus(assessment.isPresent(), detailDiagnosisMapper.isComplete(profile.detailAnswered())),
                assessment.map(RiskAssessment::riskType).orElse(null),
                assessment.map(RiskAssessment::score).orElse(null),
                assessment.map(a -> ratio(a.concentrationThreshold())).orElse(null),
                assessment.map(a -> ratio(a.safeRatioAdjust())).orElse(null),
                assessment.isPresent() ? LocalDate.now(clock) : null);

        return toView(riskProfileRepository.save(profile));
    }

    /**
     * 상세 진단(Q4~Q6) 응답을 제출한다. Q1~Q3 은 다시 묻지 않으며 <b>점수·유형은 어떤 경우에도 변하지 않는다</b>
     * (FR-DG-03·FR-DG-05). 중단하면 응답이 저장돼 다음 호출에서 이어진다(FR-DG-04).
     *
     * <p>Q5 는 {@code user_settings.explain_level}, Q6 는 {@code user_settings.explain_domain} 에 즉시 반영된다.
     * 두 값은 문구·비유·설명 밀도에만 쓰이고 계산에는 들어가지 않는다(FR-MY-03).
     *
     * @param answers 문항 코드({@code q4}~{@code q6}) → 응답값. q4 는 {@code A}~{@code D},
     *                q5 는 {@code simple/standard/detailed}, q6 는 {@code finance/dev/marketing/plain}
     * @throws InvalidRequestException 응답이 비었거나, 문항/응답값이 계약을 벗어난 경우
     * @throws NotFoundException       사용자를 찾을 수 없는 경우
     */
    @Transactional
    public DetailSubmission submitDetail(UUID userId, Map<String, String> answers) {
        Map<String, String> submitted = require(answers, DetailDiagnosisMapper.DETAIL_QUESTIONS, "상세 진단");

        RiskProfile profile = loadOrStart(userId);
        Map<String, String> merged = merge(profile.detailAnswered(), submitted);
        validateDetailValues(merged);

        boolean completed = detailDiagnosisMapper.isComplete(merged);
        profile.applyDetail(merged, completed, resolveStatus(profile.getRiskType() != null, completed));
        RiskProfileView view = toView(riskProfileRepository.save(profile));

        String explainLevel = submitted.get(QUESTION_EXPLAIN_LEVEL);
        String explainDomain = submitted.get(QUESTION_EXPLAIN_DOMAIN);
        SettingsView settings = userSettingsService.updateSettings(userId, null, null, explainLevel, explainDomain);
        return new DetailSubmission(view, settings.explainLevel(), settings.explainDomain());
    }

    private RiskProfile loadOrStart(UUID userId) {
        return riskProfileRepository.findByOwner_Id(userId)
                .orElseGet(() -> {
                    User owner = userRepository.findById(userId)
                            .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
                    return RiskProfile.start(owner, STATUS_NOT_MEASURED);
                });
    }

    private Optional<RiskAssessment> assess(Map<String, String> answers) {
        try {
            return riskProfileScorer.assess(answers);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(e.getMessage());
        }
    }

    /** 제출 응답을 검증한다 — 비어 있거나 허용 문항 밖 키가 있으면 400. */
    private Map<String, String> require(Map<String, String> answers, List<String> allowed, String label) {
        if (answers == null || answers.isEmpty()) {
            throw new InvalidRequestException(label + " 응답이 비어 있습니다.");
        }
        Map<String, String> submitted = new LinkedHashMap<>();
        answers.forEach((question, choice) -> {
            if (!allowed.contains(question)) {
                throw new InvalidRequestException(
                        label + " 문항은 " + allowed + " 만 허용합니다 (입력 " + question + ").");
            }
            if (choice == null || choice.isBlank()) {
                throw new InvalidRequestException(label + " " + question + " 응답이 비어 있습니다.");
            }
            submitted.put(question, choice);
        });
        return submitted;
    }

    /** Q4 는 선택지 코드, Q5·Q6 은 설정 허용값이어야 한다. */
    private void validateDetailValues(Map<String, String> merged) {
        try {
            detailDiagnosisMapper.titleModifier(merged.get("q4"));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(e.getMessage());
        }
        String explainLevel = merged.get(QUESTION_EXPLAIN_LEVEL);
        if (explainLevel != null && !UserSettingsService.ALLOWED_EXPLAIN_LEVELS.contains(explainLevel)) {
            throw new InvalidRequestException(
                    "Q5(설명 선호)는 " + UserSettingsService.ALLOWED_EXPLAIN_LEVELS + " 중 하나여야 합니다 (입력 "
                            + explainLevel + ").");
        }
        String explainDomain = merged.get(QUESTION_EXPLAIN_DOMAIN);
        if (explainDomain != null && !UserSettingsService.ALLOWED_EXPLAIN_DOMAINS.contains(explainDomain)) {
            throw new InvalidRequestException(
                    "Q6(익숙한 분야)은 " + UserSettingsService.ALLOWED_EXPLAIN_DOMAINS + " 중 하나여야 합니다 (입력 "
                            + explainDomain + ").");
        }
    }

    private String resolveStatus(boolean simpleMeasured, boolean detailCompleted) {
        if (!simpleMeasured) {
            return STATUS_NOT_MEASURED;
        }
        return detailCompleted ? STATUS_DETAIL_DONE : STATUS_SIMPLE_DONE;
    }

    private Map<String, String> merge(Map<String, String> stored, Map<String, String> submitted) {
        Map<String, String> merged = new LinkedHashMap<>(stored);
        merged.putAll(submitted);
        return merged;
    }

    private BigDecimal ratio(double value) {
        return BigDecimal.valueOf(value).setScale(RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private RiskProfileView notMeasuredView() {
        return new RiskProfileView(
                STATUS_NOT_MEASURED, null, null, null, null, null,
                new RiskProfileView.Simple(Map.of(), List.of(), null),
                new RiskProfileView.Detail(false, Map.of(), DetailDiagnosisMapper.DETAIL_QUESTIONS.get(0), null),
                LIMITATION_NOTE);
    }

    private RiskProfileView toView(RiskProfile profile) {
        Map<String, String> answers = profile.getAnswers();
        RiskAssessment assessment = riskProfileScorer.assess(answers).orElse(null);

        RiskProfileView.Simple simple = new RiskProfileView.Simple(
                answers,
                assessment == null ? List.of() : assessment.rationale().stream()
                        .map(r -> new RiskProfileView.Rationale(r.question(), r.choice(), r.points(), r.reading()))
                        .toList(),
                assessment == null ? null : assessment.mixedResponseNote());

        Map<String, String> detailAnswered = profile.detailAnswered();
        RiskProfileView.Detail detail = new RiskProfileView.Detail(
                detailDiagnosisMapper.isComplete(detailAnswered),
                detailAnswered,
                detailDiagnosisMapper.nextQuestion(detailAnswered),
                detailDiagnosisMapper.titleModifier(detailAnswered.get("q4")));

        return new RiskProfileView(
                profile.getStatus(),
                profile.getRiskType(),
                assessment == null ? null : assessment.gradeLabel(),
                profile.getScore(),
                profile.getDiagnosedOn(),
                profile.getConcentrationThreshold() == null ? null : profile.getConcentrationThreshold().doubleValue(),
                simple,
                detail,
                LIMITATION_NOTE);
    }

    /**
     * 상세 진단 제출 결과 (명세 §5.2). {@code applied} 블록의 원본이다.
     *
     * @param profile       반영 후 진단 상태 — 점수·유형은 변하지 않는다
     * @param explainLevel  반영된 설명 선호
     * @param explainDomain 반영된 익숙한 설명 분야
     */
    public record DetailSubmission(RiskProfileView profile, String explainLevel, String explainDomain) {
    }
}
