package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.VisualEditorDraft;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.VisualEditorDraftRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.VisualEditorPublishedRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.VisualEditorProposalPort.ProposalApplyResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.VisualEditorProposalPort.ProposalException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * saveDraftWithProposal의 fail-closed 동작과 포트 호출 인자를 검증한다(DB 없음).
 * 실제 트랜잭션 롤백은 VisualEditorProposalMySqlTest가 검증한다.
 */
@DisplayName("VisualEditorService.saveDraftWithProposal")
class VisualEditorProposalSaveTest {

  private VisualEditorDraftRepository draftRepository;
  private AcademyService academyService;
  private ObjectProvider<VisualEditorProposalPort> provider;
  private VisualEditorProposalPort port;
  private VisualEditorService service;
  private final JwtTokenProvider.AuthenticatedPrincipal principal =
      new JwtTokenProvider.AuthenticatedPrincipal(10L, "tutor1", "TUTOR");

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    draftRepository = mock(VisualEditorDraftRepository.class);
    academyService = mock(AcademyService.class);
    provider = mock(ObjectProvider.class);
    port = mock(VisualEditorProposalPort.class);
    service = new VisualEditorService(
        draftRepository, mock(VisualEditorPublishedRepository.class), academyService, provider);

    AcademyResponse academy = mock(AcademyResponse.class);
    when(academy.academyId()).thenReturn(20L);
    when(academyService.getMine(any())).thenReturn(Optional.of(academy));
  }

  private VisualEditorDraft existingDraft(long revision) {
    VisualEditorDraft d = new VisualEditorDraft(20L, "<p>old</p>", "", "{}", "10");
    ReflectionTestUtils.setField(d, "revision", revision);
    return d;
  }

  @Test
  @DisplayName("given 포트 구현체 없음 when 제안 저장 then NOT_AVAILABLE이고 초안은 조회·저장되지 않는다")
  void noPort_isRejectedBeforeAnyWrite() {
    when(provider.getIfAvailable()).thenReturn(null);

    assertThatThrownBy(() -> service.saveDraftWithProposal(
        principal, "<p>x</p>", "", "{}", 1L, 77L))
        .isInstanceOfSatisfying(ProposalException.class,
            e -> assertThat(e.getCode()).isEqualTo(ProposalException.NOT_AVAILABLE));
    verify(draftRepository, never()).findByAcademyId(any());
  }

  @Test
  @DisplayName("given 정상 저장 when 제안 저장 then 포트에 서버 확정 학원·DB revision·정제 projectData를 넘긴다")
  void port_receivesServerDerivedValues() {
    VisualEditorDraft draft = existingDraft(5L);
    when(draftRepository.findByAcademyId(20L)).thenReturn(Optional.of(draft));
    when(provider.getIfAvailable()).thenReturn(port);
    when(port.applyInSaveTransaction(20L, 10L, 77L, 5L, "{\"k\":1}"))
        .thenReturn(new ProposalApplyResult(77L, "APPLIED", 5L));

    VisualEditorService.SaveOutcome out = service.saveDraftWithProposal(
        principal, "<p>new</p>", "", "{\"k\":1}", 5L, 77L);

    assertThat(out.appliedProposal().status()).isEqualTo("APPLIED");
    assertThat(out.draft().getHtml()).isEqualTo("<p>new</p>");
    verify(port).applyInSaveTransaction(20L, 10L, 77L, 5L, "{\"k\":1}");
  }

  @Test
  @DisplayName("given 포트가 null 또는 APPLIED 아닌 결과 when 제안 저장 then 검증 실패로 거부(fail-closed)")
  void nonAppliedResult_isFailClosed() {
    when(draftRepository.findByAcademyId(20L)).thenReturn(Optional.of(existingDraft(5L)));
    when(provider.getIfAvailable()).thenReturn(port);

    when(port.applyInSaveTransaction(20L, 10L, 1L, 5L, "{}")).thenReturn(null);
    assertThatThrownBy(() -> service.saveDraftWithProposal(
        principal, "<p>a</p>", "", "{}", 5L, 1L))
        .isInstanceOfSatisfying(ProposalException.class,
            e -> assertThat(e.getCode()).isEqualTo(ProposalException.VERIFICATION_FAILED));

    when(port.applyInSaveTransaction(20L, 10L, 2L, 5L, "{}"))
        .thenReturn(new ProposalApplyResult(2L, "PENDING", 5L));
    assertThatThrownBy(() -> service.saveDraftWithProposal(
        principal, "<p>a</p>", "", "{}", 5L, 2L))
        .isInstanceOfSatisfying(ProposalException.class,
            e -> assertThat(e.getCode()).isEqualTo(ProposalException.VERIFICATION_FAILED));
  }

  @Test
  @DisplayName("given 포트가 예외 when 제안 저장 then 그대로 전파(트랜잭션 롤백 대상)")
  void portException_propagates() {
    when(draftRepository.findByAcademyId(20L)).thenReturn(Optional.of(existingDraft(5L)));
    when(provider.getIfAvailable()).thenReturn(port);
    when(port.applyInSaveTransaction(20L, 10L, 3L, 5L, "{}"))
        .thenThrow(ProposalException.notFound());

    assertThatThrownBy(() -> service.saveDraftWithProposal(
        principal, "<p>a</p>", "", "{}", 5L, 3L))
        .isInstanceOfSatisfying(ProposalException.class,
            e -> assertThat(e.getHttpStatus()).isEqualTo(404));
  }

  @Test
  @DisplayName("given revision 불일치 when 제안 저장 then 충돌 예외이고 포트는 호출되지 않는다")
  void revisionConflict_doesNotCallPort() {
    when(draftRepository.findByAcademyId(20L)).thenReturn(Optional.of(existingDraft(5L)));
    when(provider.getIfAvailable()).thenReturn(port);

    assertThatThrownBy(() -> service.saveDraftWithProposal(
        principal, "<p>a</p>", "", "{}", 4L, 3L))
        .isInstanceOf(VisualEditorService.DraftConflictException.class);
    verify(port, never()).applyInSaveTransaction(
        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), any());
  }
}
