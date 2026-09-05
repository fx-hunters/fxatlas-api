package com.divurve.domain.settings;

import com.divurve.common.architecture.UseCase;
import com.divurve.common.exception.InvalidRequestException;
import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.settings.entity.RiskAnswer;
import com.divurve.domain.settings.entity.RiskProfile;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import com.divurve.engine.riskprofile.RiskAssessment;
import com.divurve.engine.riskprofile.RiskProfileScorer;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * 투자성향 진단·재진단 유스케이스 (이슈 #10, FR-ON-02·FR-MY-02). 문항 응답을 받아 engine 으로 등급을 산출하고
 * 사용자당 하나의 {@link RiskProfile} 에 저장한다. 등급값은 이후 집중도 기준선·버킷 비율의 입력이 된다.
 *
 * <p>수치(등급·점수)는 {@link RiskProfileScorer}(결정론적 순수 함수)만 만든다 — 이 서비스는 조회·영속화·검증만 한다.
 */
@UseCase
public class RiskProfileService {

    private final RiskProfileRepository riskProfileRepository;
    private final UserRepository userRepository;
    private final RiskProfileScorer riskProfileScorer;

    public RiskProfileService(
            RiskProfileRepository riskProfileRepository,
            UserRepository userRepository,
            RiskProfileScorer riskProfileScorer) {
        this.riskProfileRepository = riskProfileRepository;
        this.userRepository = userRepository;
        this.riskProfileScorer = riskProfileScorer;
    }

    /**
     * 현재 성향 프로필과 응답 이력을 조회한다.
     *
     * @throws NotFoundException 아직 진단하지 않은 경우
     */
    @Transactional(readOnly = true)
    public RiskProfileView getRiskProfile(UUID userId) {
        RiskProfile profile = riskProfileRepository.findByOwner_Id(userId)
                .orElseThrow(() -> new NotFoundException("아직 성향 진단을 하지 않았습니다."));
        return toView(profile);
    }

    /**
     * 문항 응답으로 성향을 (재)진단해 저장하고 결과를 반환한다.
     *
     * @throws NotFoundException     사용자를 찾을 수 없는 경우
     * @throws InvalidRequestException 응답이 비었거나 선택값이 계산 계약을 위반한 경우
     */
    @Transactional
    public RiskProfileView reassess(UUID userId, List<AnswerCommand> answers) {
        if (answers == null || answers.isEmpty()) {
            throw new InvalidRequestException("성향 진단 응답이 비어 있습니다.");
        }
        RiskAssessment assessment = score(answers);
        List<RiskAnswer> stored = answers.stream()
                .map(a -> RiskAnswer.of(a.questionCode(), a.choice()))
                .toList();

        RiskProfile profile = riskProfileRepository.findByOwner_Id(userId)
                .map(existing -> {
                    existing.reassess(assessment.riskType(), assessment.score(), stored);
                    return existing;
                })
                .orElseGet(() -> {
                    User owner = userRepository.findById(userId)
                            .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));
                    return RiskProfile.create(owner, assessment.riskType(), assessment.score(), stored);
                });
        return toView(riskProfileRepository.save(profile));
    }

    private RiskAssessment score(List<AnswerCommand> answers) {
        try {
            return riskProfileScorer.assess(answers.stream().map(AnswerCommand::choice).toList());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(e.getMessage());
        }
    }

    private RiskProfileView toView(RiskProfile profile) {
        List<RiskProfileView.Answer> answers = profile.getAnswers().stream()
                .map(a -> new RiskProfileView.Answer(a.getQuestionCode(), a.getChoice()))
                .toList();
        return new RiskProfileView(profile.getRiskType(), profile.getScore(), answers);
    }

    /** 성향 진단 문항 응답 입력. */
    public record AnswerCommand(String questionCode, int choice) {
    }
}
