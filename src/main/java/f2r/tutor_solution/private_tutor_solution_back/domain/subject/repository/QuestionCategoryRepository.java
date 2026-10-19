package f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.QuestionCategory;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestionCategoryRepository extends JpaRepository<QuestionCategory, Long> {

  List<QuestionCategory> findBySubject(Subject subject);

  Optional<QuestionCategory> findByCategoryNameKo(String categoryNameKo);

  List<QuestionCategory> findBySubject_SubjectId(Integer subjectId);

  /**
   * Claude가 응답한 category_name_en을 lookup 키로 조회 (자동 분류 매핑용).
   * UNIQUE 제약이라 1건만 매칭됨.
   */
  Optional<QuestionCategory> findByCategoryNameEn(String categoryNameEn);
}