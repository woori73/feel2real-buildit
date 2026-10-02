package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem.KnowledgeStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeItemRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * NewStartAtozCaseService — given/when/then 단위테스트. A to Z 단계별 사례 그룹핑이
 * <b>결정적 키워드 매핑</b>이고 DB 원본을 바꾸지 않으며(§10), ACTIVE·비만료·사례 출처만
 * 노출하고, 항목이 없으면 빈 맵으로 안전 퇴피해 본문 렌더를 막지 않는지를 검증한다.
 */
@DisplayName("NewStartAtozCaseService — 운영 DB ACTIVE 사례의 읽기 전용 단계 그룹핑")
class NewStartAtozCaseServiceTest {

  private static NewstartKnowledgeItem item(long id, SourceType type, KnowledgeStatus status,
      String title, String summary, String tags, String category, String url, LocalDate effectiveTo) {
    return NewstartKnowledgeItem.builder()
        .knowledgeId(id).sourceType(type).status(status)
        .title(title).summary(summary).tags(tags).category(category).sourceUrl(url)
        .effectiveTo(effectiveTo).build();
  }

  private static NewStartAtozCaseService serviceWith(List<NewstartKnowledgeItem> active) {
    NewstartKnowledgeItemRepository repo = Mockito.mock(NewstartKnowledgeItemRepository.class);
    when(repo.findAllActive()).thenReturn(active);
    return new NewStartAtozCaseService(repo);
  }

  @Test
  @DisplayName("given ACTIVE 사례 when 키워드가 단계와 일치하면 then 해당 단계로 배정되고 기계 제목 접두는 벗겨진다")
  void groupsByDeterministicKeyword_andStripsMachinePrefix() {
    NewstartKnowledgeItem cafe = item(1L, SourceType.CAFE_ARTICLE, KnowledgeStatus.ACTIVE,
        "[사례] 6037 사업자 후순위 담보대출 문의드립니다.", "후순위 담보 접수", "담보,후순위", "사례",
        "https://cafe/6037", null);
    NewstartKnowledgeItem ebook = item(2L, SourceType.EBOOK, KnowledgeStatus.ACTIVE,
        "[전자책] EBK-9-1 부동산담보 처리 기준", "부동산 담보 처리", null, "전자책", null, null);

    Map<Integer, List<NewStartAtozCaseService.AtozCase>> byStep = serviceWith(List.of(cafe, ebook)).casesByStep();

    // both map to step 8 (부동산/담보/후순위)
    assertThat(byStep.get(8)).extracting(NewStartAtozCaseService.AtozCase::title)
        .contains("사업자 후순위 담보대출 문의드립니다.", "부동산담보 처리 기준");
    assertThat(byStep.get(8).get(0).title()).doesNotStartWith("[사례]");
  }

  @Test
  @DisplayName("given 비-ACTIVE·만료·비사례 출처 when grouping then 모두 제외된다")
  void excludesNonActiveExpiredAndNonCaseSources() {
    NewstartKnowledgeItem review = item(1L, SourceType.CASE, KnowledgeStatus.REVIEW,
        "추심 독촉 사례", "추심", null, "사례", null, null);
    NewstartKnowledgeItem expired = item(2L, SourceType.CASE, KnowledgeStatus.ACTIVE,
        "지급명령 이의신청 사례", "지급명령", null, "사례", null, LocalDate.now().minusDays(1));
    NewstartKnowledgeItem official = item(3L, SourceType.OFFICIAL_CURRENT, KnowledgeStatus.ACTIVE,
        "협약기관 공식 기준", "협약", null, "공식", null, null);

    Map<Integer, List<NewStartAtozCaseService.AtozCase>> byStep =
        serviceWith(List.of(review, expired, official)).casesByStep();

    assertThat(byStep).isEmpty();
  }

  @Test
  @DisplayName("given 어떤 단계 키워드와도 일치하지 않는 사례 when grouping then 어느 단계에도 걸치지 않는다")
  void unmatchedItemIsNotForcedIntoAStep() {
    NewstartKnowledgeItem odd = item(1L, SourceType.CASE, KnowledgeStatus.ACTIVE,
        "무관한 주제", "xyz", null, "기타", null, null);

    assertThat(NewStartAtozCaseService.matchStep(odd)).isEqualTo(-1);
    assertThat(serviceWith(List.of(odd)).casesByStep()).isEmpty();
  }

  @Test
  @DisplayName("given 한 단계에 많은 사례 when grouping then 상한까지만 노출한다(카드 과적 금지 §5)")
  void capsPerStep() {
    List<NewstartKnowledgeItem> many = new java.util.ArrayList<>();
    for (int i = 0; i < 10; i++) {
      many.add(item(i, SourceType.CASE, KnowledgeStatus.ACTIVE,
          "추심 독촉 사례 " + i, "추심 독촉 압류", null, "사례", null, null));
    }

    Map<Integer, List<NewStartAtozCaseService.AtozCase>> byStep = serviceWith(many).casesByStep();

    assertThat(byStep.get(1)).hasSizeLessThanOrEqualTo(NewStartAtozCaseService.MAX_PER_STEP);
  }

  @Test
  @DisplayName("given 저장소 장애 when grouping then 빈 맵으로 안전 퇴피해 A to Z 본문 렌더를 막지 않는다")
  void repositoryFailureDegradesToEmpty() {
    NewstartKnowledgeItemRepository repo = Mockito.mock(NewstartKnowledgeItemRepository.class);
    when(repo.findAllActive()).thenThrow(new IllegalStateException("db down"));

    assertThat(new NewStartAtozCaseService(repo).casesByStep()).isEmpty();
  }

  @Test
  @DisplayName("humanTitle은 기계 직렬화 접두([사례] id, [전자책] id ::)만 벗기고 본문 제목은 보존한다")
  void humanTitleStripsMachineSerialization() {
    assertThat(NewStartAtozCaseService.humanTitle("[사례] 5866 90% 탕감 사례")).isEqualTo("90% 탕감 사례");
    assertThat(NewStartAtozCaseService.humanTitle("[전자책] EBK-9-1 부동산담보 처리 기준"))
        .isEqualTo("부동산담보 처리 기준");
    assertThat(NewStartAtozCaseService.humanTitle("그냥 제목")).isEqualTo("그냥 제목");
    assertThat(NewStartAtozCaseService.humanTitle(null)).isEmpty();
  }
}
