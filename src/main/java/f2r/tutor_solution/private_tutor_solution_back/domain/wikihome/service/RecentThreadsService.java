package f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionThreadPost;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.QuestionWiki;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.repository.QuestionThreadPostRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikihome.dto.WikiHomeFeedResponse.RecentThreadItem;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공개 Wiki 메인 "최근 문항 분석" — Wiki Thread(Question Wiki)에서 최근 활동이 있는 문항을 최신순으로.
 * 문항 단위로 중복을 제거해 상위 {@link #FEED_SIZE}개만 노출한다. 클릭 시 해당 Question Wiki Thread 로 이동.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecentThreadsService {

  static final int FEED_SIZE = 5;
  private static final int SCAN_SIZE = 40;

  private final QuestionThreadPostRepository threadPostRepository;

  public List<RecentThreadItem> recentThreads() {
    List<QuestionThreadPost> posts = threadPostRepository.findRecentActivity(PageRequest.of(0, SCAN_SIZE));
    Set<Long> seen = new LinkedHashSet<>();
    List<RecentThreadItem> items = new ArrayList<>();
    for (QuestionThreadPost post : posts) {
      QuestionWiki q = post.getQuestionWiki();
      if (!seen.add(q.getQuestionWikiId())) {
        continue;
      }
      items.add(new RecentThreadItem(
          q.getQuestionWikiId(),
          q.getGrade(),
          q.getQuestionNumber(),
          q.getExamLabel(),
          q.displayContext(),
          post.getCreatedAt(),
          "/wiki/questions/" + q.getQuestionWikiId()));
      if (items.size() >= FEED_SIZE) {
        break;
      }
    }
    return items;
  }
}
