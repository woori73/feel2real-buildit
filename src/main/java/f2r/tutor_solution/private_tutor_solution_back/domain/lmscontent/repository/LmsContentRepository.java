package f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.entity.LmsContent;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.entity.LmsContentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LmsContentRepository extends JpaRepository<LmsContent, Long> {

  Optional<LmsContent> findByLmsContentIdAndAcademy_AcademyId(Long lmsContentId, Long academyId);

  List<LmsContent> findByLessonProduct_LessonIdAndStatusOrderByDisplayOrderAscLmsContentIdAsc(
      Long lessonId, LmsContentStatus status);

  List<LmsContent> findByAcademy_AcademyIdOrderByCreatedAtDesc(Long academyId);
}
