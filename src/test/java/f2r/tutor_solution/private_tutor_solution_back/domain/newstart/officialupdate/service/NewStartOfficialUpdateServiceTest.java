package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.dto.NewStartOfficialUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.Category;
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
        LocalDateTime.of(2026, 9, 21, 9, 0), null, null, Category.NOTICE, "URL:https://ccrs.or.kr/a");
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

  private static NewStartOfficialUpdate review() {
    return NewStartOfficialUpdate.review(
        SourceType.FSC, "금융위원회", "제도 변경", "요약", null, "DOC-1",
        LocalDateTime.of(2026, 9, 21, 10, 0), null, null, Category.POLICY, "DOC:FSC:doc-1");
  }
}
