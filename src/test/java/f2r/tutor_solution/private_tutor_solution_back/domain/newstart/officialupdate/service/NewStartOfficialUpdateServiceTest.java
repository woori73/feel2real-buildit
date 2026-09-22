package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.dto.NewStartOfficialUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.Category;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.RelevancePriority;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.ReviewStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.repository.NewStartOfficialUpdateRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewStartOfficialUpdateServiceTest {

  @Mock
  private NewStartOfficialUpdateRepository repository;

  @Test
  void createAlwaysStartsInReviewAndNormalizesUrlForDeduplication() {
    NewStartOfficialUpdateService service = new NewStartOfficialUpdateService(repository);
    NewStartOfficialUpdateRequest request = request("https://FSC.GO.KR/notice/1/");
    when(repository.findByDedupKey("URL:https://fsc.go.kr/notice/1")).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var response = service.createReview(request);

    assertThat(response.reviewStatus()).isEqualTo("REVIEW");
    assertThat(response.originalUrl()).isEqualTo("https://fsc.go.kr/notice/1");
    assertThat(response.featured()).isFalse();
  }

  @Test
  void duplicateDocumentIsRejectedBeforeSave() {
    NewStartOfficialUpdateService service = new NewStartOfficialUpdateService(repository);
    NewStartOfficialUpdateRequest request = new NewStartOfficialUpdateRequest(
        SourceType.FSC, "금융위원회", "제도 변경", "요약", null, "DOC-1",
        LocalDateTime.of(2026, 9, 21, 10, 0), null, null, Category.POLICY);
    when(repository.findByDedupKey("DOC:FSC:doc-1")).thenReturn(Optional.of(review()));

    assertThatThrownBy(() -> service.createReview(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("이미 등록");
  }

  @Test
  void publishWithoutUrlIsRejected() {
    NewStartOfficialUpdateService service = new NewStartOfficialUpdateService(repository);
    NewStartOfficialUpdate update = review();
    when(repository.findById(1L)).thenReturn(Optional.of(update));

    assertThatThrownBy(() -> service.publish(1L, "admin"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("URL");
  }

  @Test
  void publicFeedUsesPublishedOnlyAndSourceFilter() {
    NewStartOfficialUpdateService service = new NewStartOfficialUpdateService(repository);
    NewStartOfficialUpdate published = NewStartOfficialUpdate.review(
        SourceType.CCRS, "신용회복위원회", "공식 공지", "요약", "https://ccrs.or.kr/a", null,
        LocalDateTime.of(2026, 9, 21, 9, 0), null, null, Category.NOTICE,
        RelevancePriority.NORMAL, "URL:https://ccrs.or.kr.a", LocalDateTime.now());
    published.publish("admin");
    when(repository.findByReviewStatusAndSourceTypeOrderByFeaturedDescPublishedAtDescUpdateIdDesc(
        ReviewStatus.PUBLISHED, SourceType.CCRS)).thenReturn(List.of(published));

    assertThat(service.publicUpdates(SourceType.CCRS)).hasSize(1);
    assertThat(service.publicUpdates(SourceType.CCRS).get(0).reviewStatus()).isEqualTo("PUBLISHED");
  }

  private static NewStartOfficialUpdateRequest request(String url) {
    return new NewStartOfficialUpdateRequest(
        SourceType.FSC, "금융위원회", "제도 변경", "요약", url, null,
        LocalDateTime.of(2026, 9, 21, 10, 0), null, null, Category.POLICY);
  }

  @Test
  void createPublishedStoresPublishedRowWithAutoDiscoveryMarker() {
    NewStartOfficialUpdateService service = new NewStartOfficialUpdateService(repository);
    when(repository.findByDedupKey("URL:https://fsc.go.kr/notice/1")).thenReturn(Optional.empty());
    java.util.concurrent.atomic.AtomicReference<NewStartOfficialUpdate> saved =
        new java.util.concurrent.atomic.AtomicReference<>();
    when(repository.save(any())).thenAnswer(invocation -> {
      NewStartOfficialUpdate entity = invocation.getArgument(0);
      org.springframework.test.util.ReflectionTestUtils.setField(entity, "updateId", 1L);
      saved.set(entity);
      return entity;
    });
    when(repository.findById(1L)).thenAnswer(inv -> Optional.of(saved.get()));

    var response = service.createPublished(request("https://fsc.go.kr/notice/1/"),
        RelevancePriority.NORMAL, LocalDateTime.now());

    assertThat(response.reviewStatus()).isEqualTo("PUBLISHED");
    assertThat(saved.get().getReviewedBy()).isEqualTo(NewStartOfficialUpdateService.AUTO_PUBLISHED_BY);
  }

  @Test
  void adminArchiveAfterAutoPublishRemovesItemFromPublicFeed() {
    NewStartOfficialUpdateService service = new NewStartOfficialUpdateService(repository);
    NewStartOfficialUpdate autoPublished = NewStartOfficialUpdate.review(
        SourceType.FSC, "금융위원회", "제도 변경", "요약", "https://fsc.go.kr/notice/2", null,
        LocalDateTime.of(2026, 9, 22, 10, 0), null, null, Category.POLICY,
        RelevancePriority.NORMAL, "URL:https://fsc.go.kr/notice/2", LocalDateTime.now());
    autoPublished.publish(NewStartOfficialUpdateService.AUTO_PUBLISHED_BY);
    java.util.List<NewStartOfficialUpdate> table = new java.util.ArrayList<>(List.of(autoPublished));
    when(repository.findByReviewStatusOrderByFeaturedDescPublishedAtDescUpdateIdDesc(ReviewStatus.PUBLISHED))
        .thenAnswer(inv -> table.stream().filter(u -> u.getReviewStatus() == ReviewStatus.PUBLISHED).toList());
    when(repository.findById(1L)).thenReturn(Optional.of(autoPublished));
    org.springframework.test.util.ReflectionTestUtils.setField(autoPublished, "updateId", 1L);

    assertThat(service.publicUpdates(null)).hasSize(1);

    service.archive(1L, "admin");

    assertThat(service.publicUpdates(null)).isEmpty();
    assertThat(autoPublished.getReviewStatus()).isEqualTo(ReviewStatus.ARCHIVED);
  }

  @Test
  void autoPublishPathIsFullySeparatedFromKnowledgeRag() {
    // 뉴스 자동 게시와 Knowledge ACTIVE/OFFICIAL_CURRENT 승격은 완전히 분리되어야 한다:
    // officialupdate 게재 경로의 모든 의존 타입(제네릭 포함)은 officialupdate 패키지 내에 머문다.
    for (java.lang.reflect.Type type :
        NewStartOfficialUpdateService.class.getDeclaredConstructors()[0].getGenericParameterTypes()) {
      assertOfficialUpdateDependency(type);
    }
    for (java.lang.reflect.Type type :
        NewStartOfficialUpdateDiscoveryService.class.getDeclaredConstructors()[0].getGenericParameterTypes()) {
      assertOfficialUpdateDependency(type);
    }
  }

  private static void assertOfficialUpdateDependency(java.lang.reflect.Type type) {
    if (type instanceof java.lang.reflect.ParameterizedType parameterized) {
      for (java.lang.reflect.Type arg : parameterized.getActualTypeArguments()) {
        assertOfficialUpdateDependency(arg);
      }
      return;
    }
    assertThat(type.getTypeName()).contains(".officialupdate.");
  }

  private static NewStartOfficialUpdate review() {
    return NewStartOfficialUpdate.review(
        SourceType.FSC, "금융위원회", "제도 변경", "요약", null, "DOC-1",
        LocalDateTime.of(2026, 9, 21, 10, 0), null, null, Category.POLICY,
        RelevancePriority.NORMAL, "DOC:FSC:doc-1", LocalDateTime.now());
  }
}
