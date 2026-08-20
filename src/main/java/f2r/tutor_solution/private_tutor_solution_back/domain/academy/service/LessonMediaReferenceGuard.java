package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.LessonMediaUsageProjection;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.LessonProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 수업상품이 대표 미디어 또는 OT 영상으로 참조 중인 영상을 조회한다.
 *
 * <p>영상 삭제 차단(400)과 강사 영상 목록의 "사용 중" 배지가 같은 판정을 쓰도록 한 곳에 모았다.
 *
 * <p>DB FK는 {@code ON DELETE SET NULL}이라 참조를 막지 못한다 — 막았다간 학원 삭제와 회원 탈퇴가
 * errno 1451로 깨진다(V934 주석 참고). 따라서 사용자 대면 차단은 전적으로 이 guard에 달려 있고,
 * 그래서 {@link #lessonNamesUsingForUpdate}는 락을 잡는 쿼리를 쓴다.
 */
@Component
@RequiredArgsConstructor
public class LessonMediaReferenceGuard {

  private final LessonProductRepository lessonProductRepository;

  /**
   * 삭제 판정용. 참조 행에 락을 걸어 판정과 삭제 사이에 새 참조가 끼어들지 못하게 한다.
   *
   * <p>반드시 트랜잭션 안에서 부를 것. 트랜잭션이 없으면 락이 즉시 풀려 경쟁이 그대로 남는다.
   */
  public List<String> lessonNamesUsingForUpdate(Long videoId) {
    if (videoId == null) {
      return List.of();
    }
    return lessonProductRepository.findNamesForUpdateByMediaVideoId(videoId);
  }

  /** 목록 배지용 배치 조회. videoId -> 사용 중인 수업상품 이름들. */
  public Map<Long, List<String>> lessonNamesUsing(Collection<Long> videoIds) {
    if (videoIds == null || videoIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, List<String>> byVideoId = new LinkedHashMap<>();
    addUsages(byVideoId, lessonProductRepository.findMediaUsages(videoIds));
    addUsages(byVideoId, lessonProductRepository.findOtVideoUsages(videoIds));
    return byVideoId;
  }

  private void addUsages(
      Map<Long, List<String>> byVideoId,
      List<LessonMediaUsageProjection> usages) {
    for (LessonMediaUsageProjection usage : usages) {
      List<String> lessonNames = byVideoId.computeIfAbsent(
          usage.getVideoId(), key -> new java.util.ArrayList<>());
      if (!lessonNames.contains(usage.getLessonName())) {
        lessonNames.add(usage.getLessonName());
      }
    }
  }
}
