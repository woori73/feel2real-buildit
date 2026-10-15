package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity.AdmissionsCounselingSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AdmissionsCounselingSessionHistoryDbTest {
    @Autowired AdmissionsCounselingSessionRepository repository;

    @Test
    void storesMultipleTurnsForSameSessionTokenInOrder() {
        repository.save(row("student-session", "첫 질문", "첫 답변"));
        repository.save(row("student-session", "두 번째 질문", "두 번째 답변"));
        List<AdmissionsCounselingSession> history =
                repository.findTop20BySessionTokenOrderByCreatedAtDesc("student-session");
        assertThat(history).hasSize(2);
        assertThat(history).extracting(AdmissionsCounselingSession::getQuestion)
                .containsExactlyInAnyOrder("첫 질문", "두 번째 질문");
    }

    private AdmissionsCounselingSession row(String token, String question, String answer) {
        return AdmissionsCounselingSession.builder()
                .sessionToken(token).question(question)
                .questionFingerprint(question).answer(answer)
                .studentProfile("학년: 고3").build();
    }
}
