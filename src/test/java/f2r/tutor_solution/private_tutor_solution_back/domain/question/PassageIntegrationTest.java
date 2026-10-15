package f2r.tutor_solution.private_tutor_solution_back.domain.question;

import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Passage;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.PassageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DisplayName("Passage 통합 테스트")
class PassageIntegrationTest {

    @Autowired PassageRepository passageRepository;
    @Autowired SubjectRepository subjectRepository;
    @Autowired EntityManager em;

    private Subject saveSubject(String nameEn, String nameKo) {
        return subjectRepository.save(Subject.builder()
            .nameEn(nameEn)
            .nameKo(nameKo)
            .build());
    }

    @Test
    @Transactional
    @DisplayName("Passage 저장 및 조회")
    void saveAndFind() {
        // given
        Subject subject = saveSubject("pas_Eng01", "pas_영어01");

        Passage passage = Passage.builder()
            .subject(subject)
            .title("2024 수능 영어 지문")
            .content("Once upon a time, a scientist discovered...")
            .sourceOrigin("2024학년도 수능")
            .build();

        // when
        Passage saved = passageRepository.save(passage);
        em.flush();
        em.clear();

        // then
        Passage found = passageRepository.findById(saved.getPassageId()).orElseThrow();
        assertThat(found.getTitle()).isEqualTo("2024 수능 영어 지문");
        assertThat(found.getContent()).isEqualTo("Once upon a time, a scientist discovered...");
        assertThat(found.getSourceOrigin()).isEqualTo("2024학년도 수능");
        assertThat(found.getSubject().getSubjectId()).isEqualTo(subject.getSubjectId());
    }

    @Test
    @Transactional
    @DisplayName("Passage - title, sourceOrigin 없이도 저장 가능 (nullable)")
    void saveWithoutOptionalFields() {
        // given
        Subject subject = saveSubject("pas_Math01", "pas_수학01");

        Passage passage = Passage.builder()
            .subject(subject)
            .content("지문 내용입니다.")
            // title, sourceOrigin 생략
            .build();

        // when & then
        assertThatCode(() -> {
            passageRepository.save(passage);
            em.flush();
        }).doesNotThrowAnyException();
    }

    @Test
    @Transactional
    @DisplayName("content null이면 저장 실패")
    void contentNotNull() {
        // given
        Subject subject = saveSubject("pas_Sci01", "pas_과학01");

        Passage passage = Passage.builder()
            .subject(subject)
            .title("타이틀만 있는 지문")
            // content 누락
            .build();

        // when & then
        assertThatThrownBy(() -> {
            passageRepository.save(passage);
            em.flush();
        }).isInstanceOf(Exception.class);
    }

    @Test
    @Transactional
    @DisplayName("updateContent - 지문 내용 수정 (title, content)")
    void updateContent() {
        // given
        Subject subject = saveSubject("pas_Eng02", "pas_영어02");

        Passage passage = passageRepository.save(Passage.builder()
            .subject(subject)
            .title("원래 제목")
            .content("원래 내용")
            .sourceOrigin("원래 출처")
            .build());

        // when
        passage.updateContent("수정된 제목", "수정된 내용");
        em.flush();
        em.clear();

        // then
        Passage found = passageRepository.findById(passage.getPassageId()).orElseThrow();
        assertThat(found.getTitle()).isEqualTo("수정된 제목");
        assertThat(found.getContent()).isEqualTo("수정된 내용");
    }
}
