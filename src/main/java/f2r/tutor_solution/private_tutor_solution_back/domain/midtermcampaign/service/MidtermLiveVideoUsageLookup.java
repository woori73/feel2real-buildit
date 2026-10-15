package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermLiveVideoLink;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermLiveVideoLinkRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 강사 영상 관리 화면의 "LIVE 사용 중" 배지용 read-only 조회(Phase 4e).
 *
 * <p>{@code midterm_live_video_links} 를 배치 조회해 {@code videoId → ["신구중 · 2학년", ...]}
 * 라벨 목록으로 만든다. 새 관리 모델을 만들지 않고 기존 링크 테이블만 읽는다.
 */
@Component
@RequiredArgsConstructor
public class MidtermLiveVideoUsageLookup {

  private final MidtermLiveVideoLinkRepository linkRepository;

  /** N+1 없이 한 번의 IN 조회. 빈 입력이면 빈 map. */
  @Transactional(readOnly = true)
  public Map<Long, List<String>> labelsByVideoId(Collection<Long> videoIds) {
    if (videoIds == null || videoIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, List<String>> out = new LinkedHashMap<>();
    for (MidtermLiveVideoLink link : linkRepository.findByAcademyVideoIdIn(videoIds)) {
      String label = link.getSchoolName() + " · " + link.getGrade() + "학년";
      List<String> labels = out.computeIfAbsent(link.getAcademyVideoId(), k -> new java.util.ArrayList<>());
      if (!labels.contains(label)) {
        labels.add(label);
      }
    }
    return out;
  }
}
