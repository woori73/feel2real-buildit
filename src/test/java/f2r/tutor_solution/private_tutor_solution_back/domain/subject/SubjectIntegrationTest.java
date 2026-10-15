package f2r.tutor_solution.private_tutor_solution_back.domain.subject;

import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.QuestionCategory;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.QuestionCategoryRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DisplayName("Subject 통합 테스트")
class SubjectIntegrationTest {

  @Autowired
  SubjectRepository subjectRepository;
  @Autowired
  QuestionCategoryRepository questionCategoryRepository;
  @Autowired EntityManager em;

  // 테스트 과목명은 init.sql 시드 데이터(math/english/science)와 겹치지 않는 학교 과목으로 사용한다.
  // utf8mb4_unicode_ci collation은 case-insensitive이므로 'Math' vs 'math'도 충돌 발생.

  @Test
  @Transactional
  @DisplayName("Subject 저장 및 조회")
  void saveAndFind() {
    // given
    Subject subject = Subject.builder()
        .nameEn("music")
        .nameKo("음악")
        .build();

    // when
    Subject saved = subjectRepository.save(subject);

    // then
    Subject found = subjectRepository.findById(saved.getSubjectId()).orElseThrow();
    assertThat(found.getNameEn()).isEqualTo("music");
    assertThat(found.getNameKo()).isEqualTo("음악");
  }

  @Test
  @Transactional
  @DisplayName("nameKo 중복 테스트")
  void checkUniqueNameKo() {
    // given
    Subject subject = Subject.builder()
        .nameEn("art")
        .nameKo("미술")
        .build();
    // dup nameKo
    Subject subject2 = Subject.builder()
        .nameEn("art_alt")
        .nameKo("미술")
        .build();

    // when
    subjectRepository.save(subject);
    assertThatThrownBy(() -> subjectRepository.save(subject2))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @Transactional
  @DisplayName("nameEn 중복 테스트")
  void checkUniqueNameEn() {
    // given
    Subject subject = Subject.builder()
        .nameEn("social_studies")
        .nameKo("사회")
        .build();
    // dup nameEn
    Subject subject1 = Subject.builder()
        .nameEn("social_studies")
        .nameKo("사회과학")
        .build();

    // when
    subjectRepository.save(subject);
    assertThatThrownBy(() -> subjectRepository.save(subject1))
        .isInstanceOf(DataIntegrityViolationException.class);
  }


  @Test
  @Transactional
  @DisplayName("Auditing - createdAt / updatedAt 자동 세팅")
  void auditingFields() {
    // given
    Subject subject = Subject.builder()
        .nameEn("history")
        .nameKo("역사")
        .build();

    // when
    Subject saved = subjectRepository.save(subject);
    em.flush();
    em.clear();

    // then
    Subject found = subjectRepository.findById(saved.getSubjectId()).orElseThrow();
    assertThat(found.getCreatedAt()).isNotNull();
    assertThat(found.getUpdatedAt()).isNotNull();
  }

  @Test
  @Transactional
  @DisplayName("QuestionCategory - Subject 연관관계 저장")
  void categoryRelation() {
    // given
    Subject subject = subjectRepository.save(
        Subject.builder().nameEn("ethics").nameKo("도덕").build()
    );

    Subject foundSubject = subjectRepository.findById(subject.getSubjectId()).orElseThrow();

    // when
    QuestionCategory category = questionCategoryRepository.save(
        QuestionCategory.builder()
            .subject(foundSubject)
            .categoryNameKo("덕목")
            .categoryNameEn("Virtue")
            .build()
    );

    // then
    QuestionCategory found = questionCategoryRepository.findById(category.getCategoryId()).orElseThrow();
    assertThat(found.getCategoryNameKo()).isEqualTo("덕목");
    assertThat(found.getSubject().getSubjectId()).isEqualTo(subject.getSubjectId());
  }

  @Test
  @DisplayName("nameEn null이면 저장 실패")
  void nameEnNotNull() {
    // given
    Subject subject = Subject.builder()
        .nameKo("체육")
        // nameEn 누락
        .build();

    // when & then
    assertThatThrownBy(() -> {
      subjectRepository.save(subject);
    }).isInstanceOf(Exception.class);
  }
}