package f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.repository;

import f2r.tutor_solution.private_tutor_solution_back.domain.lessoncommunity.entity.LessonCommunityCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LessonCommunityCategoryRepository extends JpaRepository<LessonCommunityCategory, Long> {
  List<LessonCommunityCategory> findByLesson_LessonIdAndVisibleTrueOrderBySortOrderAscNameAsc(Long lessonId);
  Optional<LessonCommunityCategory> findByCategoryIdAndLesson_LessonIdAndVisibleTrue(Long categoryId, Long lessonId);
  Optional<LessonCommunityCategory> findByCategoryIdAndLesson_LessonId(Long categoryId, Long lessonId);
  boolean existsByLesson_LessonIdAndName(Long lessonId, String name);
}
