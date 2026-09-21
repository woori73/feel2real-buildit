package f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto.NewstartKnowledgeItemCreateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto.NewstartKnowledgeItemUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto.NewstartReviewDetailResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.dto.NewstartReviewWarning;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.entity.NewstartKnowledgeStatusLog;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeRelationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstartknowledge.repository.NewstartKnowledgeStatusLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ADMIN Review Workflow 단위 테스트 (DB 없이 Mockito 기반).
 *
 * <p>검증 원칙:
 * <ul>
 *   <li>신규 Knowledge = REVIEW (자동 ACTIVE 금지)</li>
 *   <li>DRAFT → ACTIVE 직행 금지</li>
 *   <li>ACTIVE 전환은 명시적 승인 API만 허용</li>
 *   <li>경고 항목은 확인(confirmed) 없이 승인 불가</li>
 *   <li>상태 변경은 항상 이력(provenance)에 기록</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("부실차주 Wiki ADMIN 검토/승인 Workflow")
class NewstartKnowledgeReviewWorkflowTest {

    @Mock
    private NewstartKnowledgeItemRepository knowledgeRepository;
    @Mock
    private NewstartKnowledgeRelationRepository relationRepository;
    @Mock
    private NewstartKnowledgeStatusLogRepository statusLogRepository;

    private NewstartKnowledgeService service;

    @BeforeEach
    void setUp() {
        service = new NewstartKnowledgeService(knowledgeRepository, relationRepository, statusLogRepository);
    }

    private NewstartKnowledgeItem item(Long id, NewstartKnowledgeItem.KnowledgeStatus status) {
        return NewstartKnowledgeItem.builder()
                .knowledgeId(id)
                .sourceType(NewstartKnowledgeItem.SourceType.OFFICIAL_CURRENT)
                .category("부실차주신청")
                .title("테스트 지식")
                .summary("요약")
                .content("본문")
                .authority("새출발기금")
                .tags("신청,자격")
                .sourceUrl("https://www.newstartfund.or.kr/guide")
                .status(status)
                .confidence("HIGH")
                .build();
    }

    private void stubFind(NewstartKnowledgeItem item) {
        when(knowledgeRepository.findById(item.getKnowledgeId())).thenReturn(Optional.of(item));
    }

    private void stubSave() {
        when(knowledgeRepository.save(any(NewstartKnowledgeItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        stubLogSave();
    }

    private void stubLogSave() {
        when(statusLogRepository.save(any(NewstartKnowledgeStatusLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("신규 생성은 항상 DRAFT이다")
    void createAlwaysDraft() {
        when(knowledgeRepository.save(any(NewstartKnowledgeItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        NewstartKnowledgeItemCreateRequest req = NewstartKnowledgeItemCreateRequest.builder()
                .sourceType("OFFICIAL_CURRENT")
                .category("부실차주신청")
                .title("신규 지식")
                .build();

        assertThat(service.create(req, "admin").getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("DRAFT에서 ACTIVE로 직행할 수 없다")
    void draftToActiveForbidden() {
        NewstartKnowledgeItem draft = item(1L, NewstartKnowledgeItem.KnowledgeStatus.DRAFT);
        stubFind(draft);

        assertThatThrownBy(() -> service.changeStatus(1L, "ACTIVE", "admin", "사유"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(draft.getStatus()).isEqualTo(NewstartKnowledgeItem.KnowledgeStatus.DRAFT);
    }

    @Test
    @DisplayName("DRAFT에서 REVIEW로는 전이할 수 있다")
    void draftToReviewAllowed() {
        NewstartKnowledgeItem draft = item(1L, NewstartKnowledgeItem.KnowledgeStatus.DRAFT);
        stubFind(draft);
        stubSave();

        service.changeStatus(1L, "REVIEW", "admin", "검토 요청");

        assertThat(draft.getStatus()).isEqualTo(NewstartKnowledgeItem.KnowledgeStatus.REVIEW);
        ArgumentCaptor<NewstartKnowledgeStatusLog> log = ArgumentCaptor.forClass(NewstartKnowledgeStatusLog.class);
        verify(statusLogRepository).save(log.capture());
        assertThat(log.getValue().getFromStatus()).isEqualTo("DRAFT");
        assertThat(log.getValue().getToStatus()).isEqualTo("REVIEW");
    }

    @Test
    @DisplayName("경고 없는 REVIEW는 사유만으로 승인된다")
    void approveCleanReview() {
        NewstartKnowledgeItem review = item(2L, NewstartKnowledgeItem.KnowledgeStatus.REVIEW);
        stubFind(review);
        stubSave();

        assertThat(service.approveReview(2L, "admin", "공식 현행 확인", false).getStatus()).isEqualTo("ACTIVE");
        assertThat(review.getStatus()).isEqualTo(NewstartKnowledgeItem.KnowledgeStatus.ACTIVE);
    }

    @Test
    @DisplayName("경고 있는 REVIEW는 확인 없이 승인할 수 없다 (409 대상 예외)")
    void approveWarnedReviewRequiresConfirmation() {
        NewstartKnowledgeItem review = item(3L, NewstartKnowledgeItem.KnowledgeStatus.REVIEW);
        review.setSourceUrl(null); // MISSING_SOURCE 경고 유발
        stubFind(review);

        assertThatThrownBy(() -> service.approveReview(3L, "admin", "승인 시도", false))
                .isInstanceOf(NewstartKnowledgeService.ReviewConfirmationRequiredException.class)
                .satisfies(e -> assertThat(
                        ((NewstartKnowledgeService.ReviewConfirmationRequiredException) e).getWarnings())
                        .extracting(NewstartReviewWarning::getCode)
                        .contains("MISSING_SOURCE"));
        assertThat(review.getStatus()).isEqualTo(NewstartKnowledgeItem.KnowledgeStatus.REVIEW);
    }

    @Test
    @DisplayName("경고 있는 REVIEW도 명시적 확인과 사유가 있으면 승인된다")
    void approveWarnedReviewWithConfirmation() {
        NewstartKnowledgeItem review = item(4L, NewstartKnowledgeItem.KnowledgeStatus.REVIEW);
        review.setSourceUrl(null);
        stubFind(review);
        stubSave();

        assertThat(service.approveReview(4L, "admin", "원문 별도 확인 후 승인", true).getStatus())
                .isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("승인에는 사유가 필수이다")
    void approveRequiresReason() {
        NewstartKnowledgeItem review = item(5L, NewstartKnowledgeItem.KnowledgeStatus.REVIEW);
        stubFind(review);

        assertThatThrownBy(() -> service.approveReview(5L, "admin", " ", true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("REVIEW가 아니면 승인할 수 없다")
    void approveOnlyFromReview() {
        NewstartKnowledgeItem draft = item(6L, NewstartKnowledgeItem.KnowledgeStatus.DRAFT);
        stubFind(draft);

        assertThatThrownBy(() -> service.approveReview(6L, "admin", "사유", true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("일반 수정으로는 ACTIVE로 바꿀 수 없다")
    void updateCannotActivateDirectly() {
        NewstartKnowledgeItem review = item(7L, NewstartKnowledgeItem.KnowledgeStatus.REVIEW);
        stubFind(review);

        NewstartKnowledgeItemUpdateRequest req = new NewstartKnowledgeItemUpdateRequest();
        req.setStatus("ACTIVE");

        assertThatThrownBy(() -> service.update(7L, req, "admin"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("REVIEW 유지는 상태를 바꾸지 않고 이력만 남긴다")
    void keepReview() {
        NewstartKnowledgeItem review = item(8L, NewstartKnowledgeItem.KnowledgeStatus.REVIEW);
        stubFind(review);
        stubLogSave();

        assertThat(service.keepReview(8L, "admin", "근거 보강 대기").getStatus()).isEqualTo("REVIEW");
        ArgumentCaptor<NewstartKnowledgeStatusLog> log = ArgumentCaptor.forClass(NewstartKnowledgeStatusLog.class);
        verify(statusLogRepository).save(log.capture());
        assertThat(log.getValue().getFromStatus()).isEqualTo("REVIEW");
        assertThat(log.getValue().getToStatus()).isEqualTo("REVIEW");
    }

    @Test
    @DisplayName("REVIEW는 사유와 함께 ARCHIVED 처리할 수 있다")
    void archiveReview() {
        NewstartKnowledgeItem review = item(9L, NewstartKnowledgeItem.KnowledgeStatus.REVIEW);
        stubFind(review);
        stubSave();

        assertThat(service.archiveReview(9L, "admin", "공식 폐지 확인").getStatus()).isEqualTo("ARCHIVED");
    }

    @Test
    @DisplayName("보관에는 사유가 필수이다")
    void archiveRequiresReason() {
        NewstartKnowledgeItem review = item(10L, NewstartKnowledgeItem.KnowledgeStatus.REVIEW);
        stubFind(review);

        assertThatThrownBy(() -> service.archiveReview(10L, "admin", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("상세 조회는 경고와 이력을 함께 반환한다")
    void reviewDetailContainsWarningsAndHistory() {
        NewstartKnowledgeItem review = item(11L, NewstartKnowledgeItem.KnowledgeStatus.REVIEW);
        review.setSourceType(NewstartKnowledgeItem.SourceType.OFFICIAL_HISTORICAL);
        review.setTags("CONFLICT,10일,31일");
        when(knowledgeRepository.findById(11L)).thenReturn(Optional.of(review));
        when(statusLogRepository.findByKnowledgeIdOrderByCreatedAtDesc(11L)).thenReturn(List.of());

        NewstartReviewDetailResponse detail = service.getReviewDetail(11L);

        assertThat(detail.getWarnings()).extracting(NewstartReviewWarning::getCode)
                .contains("CONFLICT_RISK", "HISTORICAL");
        assertThat(detail.getHistory()).isNotNull();
        assertThat(detail.getItem().getSourceUrl()).isNotBlank();
    }

    @Test
    @DisplayName("CASE 기반 DERIVED_RULE과 authority 불명확에 경고한다")
    void warningsForCaseDerivedAndAuthority() {
        NewstartKnowledgeItem derived = item(12L, NewstartKnowledgeItem.KnowledgeStatus.REVIEW);
        derived.setSourceType(NewstartKnowledgeItem.SourceType.DERIVED_RULE);
        derived.setRelatedCaseIds("5700");
        derived.setAuthority(" ");

        assertThat(service.computeWarnings(derived)).extracting(NewstartReviewWarning::getCode)
                .contains("CASE_DERIVED", "UNCLEAR_AUTHORITY");
    }

    @Test
    @DisplayName("UNKNOWN 표기에 경고한다")
    void warningsForUnknown() {
        NewstartKnowledgeItem unknown = item(13L, NewstartKnowledgeItem.KnowledgeStatus.REVIEW);
        unknown.setContent("적용대상 UNKNOWN, 근거 미확인");
        unknown.setConfidence("LOW");

        assertThat(service.computeWarnings(unknown)).extracting(NewstartReviewWarning::getCode)
                .contains("UNKNOWN_RISK");
    }
}
