package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.service;

import f2r.tutor_solution.private_tutor_solution_back.config.AuditorContextHolder;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 문항(Question Wiki) 읽기 + 전체 Context 조립 + (ADMIN) 자료 링크 관리.
 *
 * <p>읽기는 전부 공개(비로그인 허용). {@code principal} 은 {@code mine}/{@code canManage} 계산에만 쓴다.
 * 향후 MCP {@code get_question_wiki_context(questionWikiId)} 는 {@link #getContext} 를 그대로 감싸면 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionWikiService {

  private final QuestionWikiRepository questionWikiRepository;
  private final QuestionThreadPostRepository threadPostRepository;
  private final QuestionThreadVariantDetailRepository variantRepository;
  private final QuestionThreadExamEvidenceRepository examEvidenceRepository;
  private final QuestionThreadAttachmentRepository attachmentRepository;
  private final QuestionWikiMaterialLinkRepository materialLinkRepository;
  private final QuestionAiSummaryRepository aiSummaryRepository;

  public List<QuestionWikiSummaryResponse> listNav(
      String subject, Integer grade, Integer examYear, String examLabel) {
    return questionWikiRepository.findVisibleNav(blankToNull(subject), grade, examYear, blankToNull(examLabel))
        .stream()
        .map(q -> QuestionWikiSummaryResponse.of(
            q, threadPostRepository.countByQuestionWiki_QuestionWikiIdAndVisibleTrue(q.getQuestionWikiId())))
        .toList();
  }

  public QuestionWikiSummaryResponse getSummary(Long questionWikiId) {
    QuestionWiki q = loadVisible(questionWikiId);
    return QuestionWikiSummaryResponse.of(
        q, threadPostRepository.countByQuestionWiki_QuestionWikiIdAndVisibleTrue(questionWikiId));
  }

  /** 문항 전체 Context. 상세 화면과 AI 종합이 공유한다. */
  public QuestionWikiContextResponse getContext(
      Long questionWikiId, JwtTokenProvider.AuthenticatedPrincipal principal) {
    QuestionWiki q = loadVisible(questionWikiId);

    List<QuestionThreadPost> posts = threadPostRepository.findVisibleByQuestionWikiId(questionWikiId);

    Map<Long, QuestionThreadVariantResponse> variantByPost = new LinkedHashMap<>();
    variantRepository.findVisibleByQuestionWikiId(questionWikiId)
        .forEach(v -> variantByPost.put(v.getPost().getPostId(), QuestionThreadVariantResponse.from(v)));
    Map<Long, QuestionThreadExamEvidenceResponse> evidenceByPost = new LinkedHashMap<>();
    examEvidenceRepository.findVisibleByQuestionWikiId(questionWikiId)
        .forEach(e -> evidenceByPost.put(e.getPost().getPostId(),
            QuestionThreadExamEvidenceResponse.from(e)));

    // 답글을 parentId 별로 모은다.
    Map<Long, List<QuestionThreadPost>> repliesByParent = new LinkedHashMap<>();
    List<QuestionThreadPost> roots = new ArrayList<>();
    for (QuestionThreadPost p : posts) {
      if (p.getParentPost() == null) {
        roots.add(p);
      } else {
        repliesByParent.computeIfAbsent(p.getParentPost().getPostId(), k -> new ArrayList<>()).add(p);
      }
    }

    List<QuestionThreadPostResponse> threads = new ArrayList<>();
    List<QuestionThreadPostResponse> variants = new ArrayList<>();
    List<QuestionThreadPostResponse> examEvidence = new ArrayList<>();
    long discussionCount = 0;
    for (QuestionThreadPost root : roots) {
      List<QuestionThreadPostResponse> replyResponses = new ArrayList<>();
      for (QuestionThreadPost reply : repliesByParent.getOrDefault(root.getPostId(), List.of())) {
        replyResponses.add(toResponse(reply, principal, variantByPost, evidenceByPost, List.of()));
      }
      QuestionThreadPostResponse rootResponse =
          toResponse(root, principal, variantByPost, evidenceByPost, replyResponses);
      switch (root.getDataKind()) {
        case GENERATED_PRACTICE -> variants.add(rootResponse);
        case EXAM_EVIDENCE -> examEvidence.add(rootResponse);
        default -> {
          threads.add(rootResponse);
          discussionCount++;
        }
      }
    }

    List<QuestionWikiMaterialLinkResponse> materials =
        materialLinkRepository.findByQuestionWiki_QuestionWikiIdOrderByLinkIdAsc(questionWikiId)
            .stream().map(QuestionWikiMaterialLinkResponse::from).toList();

    QuestionAiSummaryResponse aiSummary = aiSummaryRepository
        .findFirstByQuestionWiki_QuestionWikiIdAndStatusOrderByVersionDesc(
            questionWikiId, QuestionAiSummaryStatus.APPROVED)
        .map(QuestionAiSummaryResponse::from)
        .orElse(null);

    List<Integer> groupNumbers = q.getPassageGroupKey() == null ? List.of()
        : questionWikiRepository.findVisibleByPassageGroupKey(q.getPassageGroupKey())
            .stream().map(QuestionWiki::getQuestionNumber).toList();

    QuestionWikiContextResponse.Question questionInfo = new QuestionWikiContextResponse.Question(
        q.getQuestionWikiId(), q.getSubject(), q.getGrade(), q.getExamYear(), q.getExamLabel(),
        q.getQuestionNumber(), q.getPassageGroupKey(), groupNumbers, q.getTitle(), q.displayContext());

    QuestionWikiContextResponse.Counts counts = new QuestionWikiContextResponse.Counts(
        roots.size() + repliesByParent.values().stream().mapToLong(List::size).sum(),
        discussionCount, variants.size(), examEvidence.size());

    return new QuestionWikiContextResponse(
        questionInfo, materials, threads, variants, examEvidence, aiSummary, counts);
  }

  /* ── ADMIN: 자료 링크 ─────────────────────────────────────────── */

  @Transactional
  public QuestionWikiMaterialLinkResponse addMaterialLink(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Long questionWikiId,
      QuestionWikiMaterialLinkRequest request) {
    requireAdmin(principal);
    QuestionWiki q = loadVisible(questionWikiId);
    if (request == null || request.materialKind() == null || request.materialId() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자료 종류와 ID가 필요합니다.");
    }
    materialLinkRepository
        .findByQuestionWiki_QuestionWikiIdAndMaterialKindAndMaterialId(
            questionWikiId, request.materialKind(), request.materialId())
        .ifPresent(existing -> {
          throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 연결된 자료입니다.");
        });
    try {
      AuditorContextHolder.set(principal.loginId());
      QuestionWikiMaterialLink link = materialLinkRepository.save(QuestionWikiMaterialLink.builder()
          .questionWiki(q)
          .materialKind(request.materialKind())
          .materialId(request.materialId())
          .label(blankToNull(request.label()))
          .build());
      return QuestionWikiMaterialLinkResponse.from(link);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  @Transactional
  public void removeMaterialLink(JwtTokenProvider.AuthenticatedPrincipal principal, Long linkId) {
    requireAdmin(principal);
    QuestionWikiMaterialLink link = materialLinkRepository.findById(linkId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "자료 링크를 찾을 수 없습니다."));
    materialLinkRepository.delete(link);
  }

  /* ── helpers ──────────────────────────────────────────────────── */

  QuestionWiki loadVisible(Long questionWikiId) {
    if (questionWikiId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "문항 ID가 필요합니다.");
    }
    return questionWikiRepository.findVisibleById(questionWikiId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문항을 찾을 수 없습니다."));
  }

  private QuestionThreadPostResponse toResponse(
      QuestionThreadPost p,
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Map<Long, QuestionThreadVariantResponse> variantByPost,
      Map<Long, QuestionThreadExamEvidenceResponse> evidenceByPost,
      List<QuestionThreadPostResponse> replies) {
    List<QuestionThreadAttachmentResponse> attachments =
        attachmentRepository.findByPost_PostIdAndVisibleTrueOrderByAttachmentIdAsc(p.getPostId())
            .stream().map(QuestionThreadAttachmentResponse::from).toList();
    Long currentUserId = principal == null ? null : principal.userId();
    return QuestionThreadPostResponse.from(
        p,
        currentUserId,
        canManage(p, principal),
        attachments,
        variantByPost.get(p.getPostId()),
        evidenceByPost.get(p.getPostId()),
        replies);
  }

  private boolean canManage(QuestionThreadPost p, JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (principal == null) {
      return false;
    }
    return p.getAuthor().getUserId().equals(principal.userId())
        || Role.ADMIN.name().equals(principal.role());
  }

  private void requireAdmin(JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (principal == null || !Role.ADMIN.name().equals(principal.role())) {
      throw new AccessDeniedException("관리자 권한이 필요합니다.");
    }
  }

  private String blankToNull(String v) {
    return v == null || v.isBlank() ? null : v.trim();
  }
}
