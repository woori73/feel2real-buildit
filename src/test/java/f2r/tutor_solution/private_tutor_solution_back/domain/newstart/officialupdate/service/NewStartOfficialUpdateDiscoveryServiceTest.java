package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery.DiscoverySourceException;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery.OfficialUpdateDiscoveryAdapter;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery.OfficialUpdateDiscoveryAdapter.DiscoveredCandidate;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.dto.NewStartOfficialUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialDiscoveryRun;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialDiscoveryRun.RunStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.Category;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.RelevancePriority;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.repository.NewStartOfficialDiscoveryRunRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.repository.NewStartOfficialUpdateRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service.NewStartOfficialUpdateDiscoveryService.DiscoveryResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NewStartOfficialUpdateDiscoveryServiceTest {

  private final NewStartOfficialUpdateRepository updateRepository = mock(NewStartOfficialUpdateRepository.class);
  private final NewStartOfficialDiscoveryRunRepository runRepository = mock(NewStartOfficialDiscoveryRunRepository.class);
  private final NewStartOfficialUpdateService updateService = mock(NewStartOfficialUpdateService.class);

  private static DiscoveredCandidate candidate(String docId, String title) {
    return new DiscoveredCandidate(title, title, "https://official.example/" + docId, docId,
        LocalDateTime.of(2026, 9, 22, 0, 0), null, null, Category.NOTICE, null);
  }

  private class StubAdapter implements OfficialUpdateDiscoveryAdapter {
    private final SourceType sourceType;
    private final String name;
    private final List<DiscoveredCandidate> result;
    private final RuntimeException failure;
    private RelevancePriority priority = RelevancePriority.NORMAL;

    StubAdapter(SourceType sourceType, String name, List<DiscoveredCandidate> result, RuntimeException failure) {
      this.sourceType = sourceType;
      this.name = name;
      this.result = result;
      this.failure = failure;
    }

    StubAdapter withPriority(RelevancePriority priority) {
      this.priority = priority;
      return this;
    }

    @Override
    public SourceType getSourceType() {
      return sourceType;
    }

    @Override
    public String getSourceName() {
      return name;
    }

    @Override
    public List<DiscoveredCandidate> discover(LocalDateTime since) {
      if (failure != null) {
        throw failure;
      }
      return result;
    }

    @Override
    public DiscoveredCandidate enrich(DiscoveredCandidate candidate) {
      return candidate;
    }

    @Override
    public RelevancePriority evaluateRelevance(DiscoveredCandidate candidate) {
      return priority;
    }

    @Override
    public String buildDedupKey(DiscoveredCandidate candidate) {
      return "DOC:" + sourceType.name() + ":" + candidate.officialDocumentId();
    }
  }

  private NewStartOfficialUpdateDiscoveryService service(OfficialUpdateDiscoveryAdapter... adapters) {
    return new NewStartOfficialUpdateDiscoveryService(List.of(adapters), updateRepository, runRepository, updateService);
  }

  private NewStartOfficialDiscoveryRun lastRun() {
    ArgumentCaptor<NewStartOfficialDiscoveryRun> captor = ArgumentCaptor.forClass(NewStartOfficialDiscoveryRun.class);
    verify(runRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
    return captor.getValue();
  }

  @Test
  void parseFailureOfAdapterMarksRunFailedAndRegistersNothing() {
    when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    StubAdapter failing = new StubAdapter(SourceType.CCRS, "신용회복위원회", null,
        new DiscoverySourceException("신용회복위원회", DiscoverySourceException.Reason.PARSE_EMPTY_WITH_ITEMS,
            "행 13건인데 파싱 0건"));

    DiscoveryResult result = service(failing).runDiscoveryForSource(SourceType.CCRS, null);

    assertThat(result.isSuccess()).isFalse();
    assertThat(lastRun().getStatus()).isEqualTo(RunStatus.FAILED);
    assertThat(lastRun().getErrorMessage()).contains("PARSE_EMPTY_WITH_ITEMS");
    verify(updateService, never()).createPublished(any(), any(), any());
  }

  @Test
  void successfulDiscoveryAutoPublishes() {
    when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(updateRepository.findByDedupKey(anyString())).thenReturn(Optional.empty());
    StubAdapter ok = new StubAdapter(SourceType.NEWSTART_FUND, "새출발기금",
        List.of(candidate("NSF-B-56", "개편 안내")), null);

    DiscoveryResult result = service(ok).runDiscoveryForSource(SourceType.NEWSTART_FUND, null);

    assertThat(result.isSuccess()).isTrue();
    verify(updateService).createPublished(any(), any(), any());
    assertThat(lastRun().getStatus()).isEqualTo(RunStatus.COMPLETED);
    assertThat(lastRun().getDiscoveredCount()).isEqualTo(1);
    assertThat(lastRun().getNewReviewCount()).isEqualTo(1);
  }

  @Test
  void failingFaqAdapterDoesNotBlockBoardAdapterButSourceStillReportsFailure() {
    when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(updateRepository.findByDedupKey(anyString())).thenReturn(Optional.empty());
    StubAdapter board = new StubAdapter(SourceType.NEWSTART_FUND, "새출발기금",
        List.of(candidate("NSF-B-56", "개편 안내")), null);
    StubAdapter faq = new StubAdapter(SourceType.NEWSTART_FUND, "새출발기금 공식 FAQ", null,
        new DiscoverySourceException("새출발기금 공식 FAQ", DiscoverySourceException.Reason.FETCH_FAILED, "503"));

    DiscoveryResult result = service(board, faq).runDiscoveryForSource(SourceType.NEWSTART_FUND, null);

    assertThat(result.isSuccess()).isFalse();
    verify(updateService).createPublished(any(), any(), any());
    assertThat(lastRun().getStatus()).isEqualTo(RunStatus.FAILED);
    assertThat(lastRun().getDiscoveredCount()).isEqualTo(1);
    assertThat(lastRun().getNewReviewCount()).isEqualTo(1);
  }

  @Test
  void oneSourceFailureIsIsolatedFromOtherSources() {
    when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(updateRepository.findByDedupKey(anyString())).thenReturn(Optional.empty());
    StubAdapter failingHope = new StubAdapter(SourceType.HOPE_RETURN_PACKAGE, "희망리턴패키지", null,
        new DiscoverySourceException("희망리턴패키지", DiscoverySourceException.Reason.NOT_IMPLEMENTED, "SPA 이전"));
    StubAdapter okFund = new StubAdapter(SourceType.NEWSTART_FUND, "새출발기금",
        List.of(candidate("NSF-B-56", "개편 안내")), null);
    NewStartOfficialUpdateDiscoveryService svc = service(failingHope, okFund);

    DiscoveryResult combined = svc.runDiscoveryAllSources(null);

    assertThat(combined.isSuccess()).isFalse();
    assertThat(combined.getSubResults()).hasSize(2);
    assertThat(combined.getSubResults().get(0).isSuccess()).isFalse();
    assertThat(combined.getSubResults().get(1).isSuccess()).isTrue();
    verify(updateService).createPublished(any(), any(), any());
  }

  @Test
  void revisedOfficialDocumentWithSameDedupKeyCountsAsDuplicate() {
    when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(updateRepository.findByDedupKey("DOC:NEWSTART_FUND:NSF-FAQ-30")).thenReturn(Optional.empty());
    StubAdapter faq = new StubAdapter(SourceType.NEWSTART_FUND, "새출발기금 공식 FAQ",
        List.of(candidate("NSF-FAQ-30", "제도 안내 (개정 답변)")), null);
    NewStartOfficialUpdateDiscoveryService svc = service(faq);

    DiscoveryResult first = svc.runDiscoveryForSource(SourceType.NEWSTART_FUND, null);

    // 첫 등록 후 같은 문서(답변 개정으로 내용만 변경, dedupKey 동일)를 다시 발견된 경우
    when(updateRepository.findByDedupKey("DOC:NEWSTART_FUND:NSF-FAQ-30"))
        .thenReturn(Optional.of(f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate
            .review(SourceType.NEWSTART_FUND, "새출발기금 공식 FAQ", "제도 안내", "기존 답변",
                "https://official.example/NSF-FAQ-30", "NSF-FAQ-30", null, null, null,
                Category.ELIGIBILITY, RelevancePriority.NORMAL, "DOC:NEWSTART_FUND:NSF-FAQ-30", null)));

    DiscoveryResult second = svc.runDiscoveryForSource(SourceType.NEWSTART_FUND, null);

    assertThat(first.isSuccess()).isTrue();
    assertThat(second.isSuccess()).isTrue();
    assertThat(second.getDuplicates()).isEqualTo(1);
    assertThat(second.getNewReviews()).isZero();
    verify(updateService, org.mockito.Mockito.times(1)).createPublished(any(), any(), any());
  }

  @Test
  void repeatedScheduledAndManualRunsOfSameSourceNeverCreateDuplicateRows() {
    when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    // dedupKey 저장을 시뮬레이션: 자동 게시가 실제로 저장하는 것과 동일하게 unique 키를 채운다.
    java.util.Set<String> store = new java.util.HashSet<>();
    when(updateRepository.findByDedupKey(anyString())).thenAnswer(inv ->
        store.contains(inv.getArgument(0)) ? Optional.of(
            f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate
                .review(SourceType.NEWSTART_FUND, "새출발기금", "t", "s",
                    "https://official.example/NSF-B-56", "NSF-B-56", null, null, null,
                    Category.NOTICE, RelevancePriority.NORMAL, inv.getArgument(0), null))
            : Optional.empty());
    org.mockito.Mockito.doAnswer(inv -> {
      NewStartOfficialUpdateRequest req = inv.getArgument(0);
      store.add("DOC:NEWSTART_FUND:" + req.officialDocumentId());
      return null;
    }).when(updateService).createPublished(any(), any(), any());

    StubAdapter adapter = new StubAdapter(SourceType.NEWSTART_FUND, "새출발기금",
        List.of(candidate("NSF-B-56", "개편 안내")), null);
    NewStartOfficialUpdateDiscoveryService svc = service(adapter);

    // 11:00 run / 21:00 run / 수동 run 과 동일 패턴의 반복 실행
    DiscoveryResult first = svc.runDiscoveryForSource(SourceType.NEWSTART_FUND, null);
    DiscoveryResult second = svc.runDiscoveryForSource(SourceType.NEWSTART_FUND, null);
    DiscoveryResult third = svc.runDiscoveryForSource(SourceType.NEWSTART_FUND, null);

    assertThat(first.getNewReviews()).isEqualTo(1);
    assertThat(second.getNewReviews()).isZero();
    assertThat(second.getDuplicates()).isEqualTo(1);
    assertThat(third.getNewReviews()).isZero();
    assertThat(third.getDuplicates()).isEqualTo(1);
    verify(updateService, org.mockito.Mockito.times(1)).createPublished(any(), any(), any());
  }

  @Test
  void lowRelevanceOfficialItemIsStillPublished() {
    when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(updateRepository.findByDedupKey(anyString())).thenReturn(Optional.empty());
    StubAdapter irrelevant = new StubAdapter(SourceType.NEWSTART_FUND, "새출발기금",
        List.of(candidate("NSF-B-70", "강사모집 공고")), null).withPriority(RelevancePriority.IRRELEVANT);

    DiscoveryResult result = service(irrelevant).runDiscoveryForSource(SourceType.NEWSTART_FUND, null);

    // 자동 게시 정책: 관련성 판정은 필터가 아니라 메타데이터 — IRRELEVANT 도 게시 대상.
    assertThat(result.isSuccess()).isTrue();
    ArgumentCaptor<RelevancePriority> priorityCaptor = ArgumentCaptor.forClass(RelevancePriority.class);
    verify(updateService).createPublished(any(), priorityCaptor.capture(), any());
    assertThat(priorityCaptor.getValue()).isEqualTo(RelevancePriority.IRRELEVANT);
    assertThat(result.getIrrelevant()).isZero();
  }

  @Test
  void itemMissingTitleOrUrlIsTechnicallyBlockedFromPublishing() {
    when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(updateRepository.findByDedupKey(anyString())).thenReturn(Optional.empty());
    DiscoveredCandidate noTitle = new DiscoveredCandidate(" ", "s", "https://official.example/NSF-B-90",
        "NSF-B-90", null, null, null, Category.NOTICE, null);
    DiscoveredCandidate noUrl = new DiscoveredCandidate("제목 없음 항목", "s", null,
        "NSF-B-91", null, null, null, Category.NOTICE, null);
    StubAdapter adapter = new StubAdapter(SourceType.NEWSTART_FUND, "새출발기금",
        List.of(noTitle, noUrl), null);

    DiscoveryResult result = service(adapter).runDiscoveryForSource(SourceType.NEWSTART_FUND, null);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getNewReviews()).isZero();
    assertThat(result.getIrrelevant()).isEqualTo(2);
    verify(updateService, never()).createPublished(any(), any(), any());
  }
}
