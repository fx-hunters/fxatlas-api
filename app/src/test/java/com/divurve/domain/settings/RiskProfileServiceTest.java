package com.divurve.domain.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.divurve.common.exception.InvalidRequestException;
import com.divurve.common.exception.NotFoundException;
import com.divurve.domain.settings.RiskProfileService.AnswerCommand;
import com.divurve.domain.settings.entity.RiskAnswer;
import com.divurve.domain.settings.entity.RiskProfile;
import com.divurve.domain.user.UserRepository;
import com.divurve.domain.user.entity.User;
import com.divurve.engine.riskprofile.RiskProfileScorer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link RiskProfileService} 단위 테스트 — 조회·(재)진단·검증 흐름. 등급 계산은 실제 engine({@link RiskProfileScorer})으로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class RiskProfileServiceTest {

    @Mock
    private RiskProfileRepository riskProfileRepository;
    @Mock
    private UserRepository userRepository;

    private final UUID userId = UUID.randomUUID();
    private final User user = User.createDemo("me@divurve.com", "나");

    private RiskProfileService service() {
        return new RiskProfileService(riskProfileRepository, userRepository, new RiskProfileScorer());
    }

    @Test
    void getRiskProfile_은_현재_프로필과_응답이력을_반환한다() {
        RiskProfile profile = RiskProfile.create(
                user, "balanced", 6, List.of(RiskAnswer.of("Q1", 1), RiskAnswer.of("Q2", 2), RiskAnswer.of("Q3", 3)));
        when(riskProfileRepository.findByOwner_Id(userId)).thenReturn(Optional.of(profile));

        RiskProfileView view = service().getRiskProfile(userId);

        assertThat(view.riskType()).isEqualTo("balanced");
        assertThat(view.score()).isEqualTo(6);
        assertThat(view.answers()).extracting(RiskProfileView.Answer::questionCode)
                .containsExactly("Q1", "Q2", "Q3");
    }

    @Test
    void getRiskProfile_은_진단_이력이_없으면_404() {
        when(riskProfileRepository.findByOwner_Id(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getRiskProfile(userId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void reassess_는_처음이면_사용자를_찾아_새_프로필을_만든다() {
        when(riskProfileRepository.findByOwner_Id(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(riskProfileRepository.save(any(RiskProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        RiskProfileView view = service().reassess(
                userId, List.of(new AnswerCommand("Q1", 1), new AnswerCommand("Q2", 2), new AnswerCommand("Q3", 3)));

        // 합계 6 → aggressive
        assertThat(view.riskType()).isEqualTo("aggressive");
        assertThat(view.score()).isEqualTo(6);

        ArgumentCaptor<RiskProfile> captor = ArgumentCaptor.forClass(RiskProfile.class);
        verify(riskProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getAnswers()).hasSize(3);
        assertThat(captor.getValue().getScore()).isEqualTo(6);
    }

    @Test
    void reassess_는_기존_프로필이_있으면_덮어쓴다() {
        RiskProfile existing = RiskProfile.create(user, "stable", 3, List.of(RiskAnswer.of("Q1", 1)));
        when(riskProfileRepository.findByOwner_Id(userId)).thenReturn(Optional.of(existing));
        when(riskProfileRepository.save(any(RiskProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        RiskProfileView view = service().reassess(
                userId, List.of(new AnswerCommand("Q1", 3), new AnswerCommand("Q2", 3), new AnswerCommand("Q3", 3)));

        assertThat(view.riskType()).isEqualTo("challenging");
        assertThat(view.score()).isEqualTo(9);
        assertThat(existing.getRiskType()).isEqualTo("challenging");
        // 기존 프로필을 재사용하므로 사용자 재조회는 하지 않는다.
        verify(userRepository, never()).findById(any());
    }

    @Test
    void reassess_는_응답이_비면_400() {
        assertThatThrownBy(() -> service().reassess(userId, List.of()))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service().reassess(userId, null))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(riskProfileRepository, userRepository);
    }

    @Test
    void reassess_는_선택값이_계약을_위반하면_400() {
        assertThatThrownBy(() -> service().reassess(userId, List.of(new AnswerCommand("Q1", 9))))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(riskProfileRepository, userRepository);
    }

    @Test
    void reassess_는_사용자를_찾지_못하면_404() {
        when(riskProfileRepository.findByOwner_Id(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().reassess(userId, List.of(new AnswerCommand("Q1", 2))))
                .isInstanceOf(NotFoundException.class);
    }
}
