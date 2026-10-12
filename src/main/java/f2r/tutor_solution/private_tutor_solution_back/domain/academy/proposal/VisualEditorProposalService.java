package f2r.tutor_solution.private_tutor_solution_back.domain.academy.proposal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.VisualEditorDraftRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.VisualEditorProposalPort;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 시각 편집기 AI 제안의 생성·조회·거절과, 저장 트랜잭션 안에서의 적용({@link VisualEditorProposalPort}).
 *
 * <p>모든 제안 접근은 academyId를 조건으로 한다. 다른 학원의 제안은 없는 것과 같은 404다.
 */
@Service
@RequiredArgsConstructor
public class VisualEditorProposalService implements VisualEditorProposalPort {

  public static final int MAX_PENDING = 5;
  public static final int TTL_DAYS = 14;
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<List<ProposalChange>> CHANGES_TYPE = new TypeReference<>() {
  };

  private final VisualEditorProposalRepository repository;
  private final VisualEditorDraftRepository draftRepository;

  /** 편집기·Agent 응답용 제안 뷰. */
  public record ProposalView(Long proposalId, ProposalStatus status, String summary,
      String createdByKind, LocalDateTime createdAt, LocalDateTime expiresAt, Long baseRevision,
      List<ProposalChange> changes, boolean applicable, String staleReason) {
  }

  /** 제안을 만든다. 입력은 {@link ProposalChangeValidator}를 통과해야 한다(위반은 IllegalArgumentException). */
  @Transactional
  public ProposalView create(Long academyId, String kind, String ref, Long baseRevision,
      String summary, List<ProposalChange> rawChanges) {
    String cleanSummary = summary == null ? "" : summary.trim();
    if (cleanSummary.isEmpty() || cleanSummary.length() > 500) {
      throw new IllegalArgumentException("summary는 1~500자여야 합니다.");
    }
    List<ProposalChange> changes = ProposalChangeValidator.validate(rawChanges);
    LocalDateTime now = LocalDateTime.now();
    expireDue(academyId, now);
    if (repository.countByAcademyIdAndStatus(academyId, ProposalStatus.PENDING) >= MAX_PENDING) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "대기 중인 제안이 최대 " + MAX_PENDING + "건입니다. 기존 제안을 승인하거나 거절한 뒤 다시 시도하세요.");
    }
    VisualEditorProposal saved = repository.save(new VisualEditorProposal(
        academyId, baseRevision, cleanSummary, toJson(changes), kind, ref, now.plusDays(TTL_DAYS)));
    return view(saved, true);
  }

  /**
   * 편집기 목록(PENDING·STALE). 조회 시점에 만료를 반영하고, 대상 컴포넌트가 현재 초안에 없는 제안만
   * STALE로 바꾼다(revision 차이만으로는 STALE이 아니다).
   */
  @Transactional
  public List<ProposalView> listForEditor(Long academyId) {
    LocalDateTime now = LocalDateTime.now();
    expireDue(academyId, now);
    String projectData = draftRepository.findByAcademyId(academyId)
        .map(d -> d.getProjectData()).orElse(null);
    List<VisualEditorProposal> rows = repository.findByAcademyIdAndStatusInOrderByCreatedAtDesc(
        academyId, List.of(ProposalStatus.PENDING, ProposalStatus.STALE));
    return rows.stream().map(p -> {
      if (p.getStatus() == ProposalStatus.PENDING) {
        // 저장된 초안이 있고 그 안에 대상이 없을 때만 STALE. 초안이 없거나 해석되지 않으면(PROJECT_DATA_MISSING)
        // 판단 근거가 없으므로 PENDING으로 둔다 — 적용 가능 여부의 최종 판정은 저장 시 검증이 한다.
        List<String> targetFailures = ProposalVerifier.verifyTargetsOnly(projectData, fromJson(p.getChangesJson()))
            .stream().filter(f -> f.startsWith(ProposalVerifier.TARGET_NOT_FOUND)
                || f.startsWith(ProposalVerifier.TARGET_AMBIGUOUS)).toList();
        if (!targetFailures.isEmpty()) {
          p.markStale(String.join(",", targetFailures), now);
        }
      }
      return view(p, p.getStatus() == ProposalStatus.PENDING);
    }).toList();
  }

  @Transactional(readOnly = true)
  public ProposalView get(Long academyId, Long proposalId) {
    return view(find(academyId, proposalId), true);
  }

  @Transactional
  public ProposalView reject(Long academyId, Long proposalId) {
    VisualEditorProposal p = repository.lockByProposalIdAndAcademyId(proposalId, academyId)
        .orElseThrow(VisualEditorProposalService::notFound);
    if (p.getStatus() != ProposalStatus.PENDING) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리되었거나 만료된 제안입니다.");
    }
    p.markRejected(LocalDateTime.now());
    return view(p, false);
  }

  /** {@inheritDoc} — 계약: docs/BUILDIT_AI_AGENT_PROPOSAL_PORT_CONTRACT.md */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public ProposalApplyResult applyInSaveTransaction(
      long academyId, long userId, long proposalId, long savedRevision, String sanitizedProjectData) {
    VisualEditorProposal p = repository.lockByProposalIdAndAcademyId(proposalId, academyId)
        .orElseThrow(ProposalException::notFound);
    LocalDateTime now = LocalDateTime.now();
    if (p.getStatus() != ProposalStatus.PENDING || !p.getExpiresAt().isAfter(now)) {
      throw ProposalException.notPending();
    }
    List<String> failures = ProposalVerifier.verify(sanitizedProjectData, fromJson(p.getChangesJson()));
    if (!failures.isEmpty()) {
      throw ProposalException.verificationFailed(failures);
    }
    p.markApplied(savedRevision, userId, now);
    return new ProposalApplyResult(proposalId, ProposalStatus.APPLIED.name(), savedRevision);
  }

  private void expireDue(Long academyId, LocalDateTime now) {
    repository.findByAcademyIdAndStatusAndExpiresAtBefore(academyId, ProposalStatus.PENDING, now)
        .forEach(p -> p.markExpired(now));
  }

  private VisualEditorProposal find(Long academyId, Long proposalId) {
    return repository.findByProposalIdAndAcademyId(proposalId, academyId)
        .orElseThrow(VisualEditorProposalService::notFound);
  }

  private static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "제안을 찾을 수 없습니다.");
  }

  private ProposalView view(VisualEditorProposal p, boolean applicable) {
    return new ProposalView(p.getProposalId(), p.getStatus(), p.getSummary(), p.getCreatedByKind(),
        p.getCreatedAt(), p.getExpiresAt(), p.getBaseRevision(), fromJson(p.getChangesJson()),
        applicable, p.getStaleReason());
  }

  private static String toJson(List<ProposalChange> changes) {
    try {
      return MAPPER.writeValueAsString(changes);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static List<ProposalChange> fromJson(String json) {
    try {
      return MAPPER.readValue(json, CHANGES_TYPE);
    } catch (JsonProcessingException e) {
      // 저장된 값은 검증을 통과한 JSON이므로 여기에 올 수 없다. 오면 빈 목록 → 검증에서 NO_CHANGES로 실패(fail-closed).
      return List.of();
    }
  }
}
