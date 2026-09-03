package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.service;

import f2r.tutor_solution.private_tutor_solution_back.config.AuditorContextHolder;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.dto.WikiAnalysisMaterialResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.entity.WikiAnalysisMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.repository.WikiAnalysisMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.service.WikiAnalysisMaterialService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.service.WikiAnalysisMaterialService.DownloadFile;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.entity.WikiExamOriginal;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.repository.WikiExamOriginalRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.DocumentUploadValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 문항(Question Wiki) 읽기 + 전체 Context 조립 + (ADMIN) 학습자료 연결/업로드.
 *
 * <p>읽기는 전부 공개(비로그인 허용). {@code principal} 은 {@code mine}/{@code canManage} 계산에만 쓴다.
 * 향후 MCP {@code get_question_wiki_context(questionWikiId)} 는 {@link #getContext} 를 그대로 감싸면 된다.
 *
 * <p>학습자료는 기존 Wiki 자료실({@code wiki_exam_originals}/{@code wiki_analysis_materials})을 재사용한다.
 * "직접 올리기"도 {@link WikiAnalysisMaterialService} 의 저장/검증을 그대로 쓰고 링크만 새로 건다 —
 * Question Wiki 전용 파일 시스템을 만들지 않는다.
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
  private final WikiExamOriginalRepository wikiExamOriginalRepository;
  private final WikiAnalysisMaterialRepository wikiAnalysisMaterialRepository;
  private final WikiAnalysisMaterialService wikiAnalysisMaterialService;

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

    List<QuestionWikiMaterialLinkResponse> materials = new ArrayList<>();
    for (QuestionWikiMaterialLink link
        : materialLinkRepository.findByQuestionWiki_QuestionWikiIdOrderByLinkIdAsc(questionWikiId)) {
      resolveMaterial(link).ifPresent(materials::add);
    }

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

  /* ── ADMIN: 학습자료 ─────────────────────────────────────────── */

  /** "Wiki 자료에서 찾기" — 기존 Wiki 자료실에서 연결할 자료 후보를 검색한다. */
  public List<LinkableMaterialResponse> searchLinkableMaterials(
      JwtTokenProvider.AuthenticatedPrincipal principal, String query) {
    requireAdmin(principal);
    String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

    List<LinkableMaterialResponse> out = new ArrayList<>();
    for (WikiExamOriginal o : wikiExamOriginalRepository.findAdminList()) {
      String meta = "고" + o.getGrade() + " · " + o.getExamLabel() + " · " + o.getDocType().label();
      if (matches(needle, o.getTitle(), o.getExamLabel(), o.getOriginalFileName())) {
        out.add(new LinkableMaterialResponse(
            QuestionWikiMaterialKind.EXAM_ORIGINAL.name(),
            QuestionWikiMaterialLinkResponse.kindLabel(QuestionWikiMaterialKind.EXAM_ORIGINAL),
            o.getWikiExamOriginalId(), o.getTitle(), meta));
      }
    }
    for (WikiAnalysisMaterial m : wikiAnalysisMaterialRepository.findAdminList()) {
      String meta = String.join(" · ", stripBlank(
          m.getSchoolName(), "고" + m.getGrade(), m.getYear() + " " + m.getExamType(), m.getSubject()));
      if (matches(needle, m.getTitle(), m.getSchoolName(), m.getExamType(), m.getOriginalFileName())) {
        out.add(new LinkableMaterialResponse(
            QuestionWikiMaterialKind.ANALYSIS_MATERIAL.name(),
            QuestionWikiMaterialLinkResponse.kindLabel(QuestionWikiMaterialKind.ANALYSIS_MATERIAL),
            m.getWikiAnalysisMaterialId(), m.getTitle(), meta));
      }
    }
    return out.stream().limit(60).toList();
  }

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
    ensureMaterialExists(request.materialKind(), request.materialId());
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
      return resolveMaterial(link).orElseThrow(
          () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "연결한 자료를 찾을 수 없습니다."));
    } finally {
      AuditorContextHolder.clear();
    }
  }

  /** "직접 올리기" — 파일을 Wiki 학습자료로 저장(기존 검증/스토리지 재사용)하고 문항에 연결한다. */
  @Transactional
  public QuestionWikiMaterialLinkResponse uploadAndLinkMaterial(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Long questionWikiId,
      String title,
      String description,
      String docLabel,
      MultipartFile file) {
    requireAdmin(principal);
    QuestionWiki q = loadVisible(questionWikiId);
    String subject = "ENGLISH".equalsIgnoreCase(q.getSubject()) ? "영어" : q.getSubject();
    try {
      AuditorContextHolder.set(principal.loginId());
      // 이 경로에서만 문서 + HTML 을 허용한다(전역 자료실 정책 불변).
      WikiAnalysisMaterialResponse created = wikiAnalysisMaterialService.create(
          blankToNull(title) == null ? defaultTitle(q, docLabel) : title.trim(),
          blankToNull(description),
          "전국연합",
          q.getGrade(),
          q.getExamYear(),
          "공통",
          truncate(q.getExamLabel(), 40),
          subject,
          true,
          file,
          DocumentUploadValidator.DOCUMENT_WITH_HTML_EXTENSIONS);
      QuestionWikiMaterialLink link = materialLinkRepository.save(QuestionWikiMaterialLink.builder()
          .questionWiki(q)
          .materialKind(QuestionWikiMaterialKind.ANALYSIS_MATERIAL)
          .materialId(created.wikiAnalysisMaterialId())
          .label(blankToNull(docLabel))
          .build());
      return QuestionWikiMaterialLinkResponse.of(
          link, created.title(), created.description(), created.originalFileName());
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

  /** HTML 학습자료 격리 미리보기용 원본. 공개(카드의 [보기]와 동일 대상). HTML 링크가 아니면 404. */
  public HtmlPreview getHtmlPreview(Long linkId) {
    QuestionWikiMaterialLink link = materialLinkRepository.findById(linkId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "자료를 찾을 수 없습니다."));
    if (link.getMaterialKind() != QuestionWikiMaterialKind.ANALYSIS_MATERIAL) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "미리보기를 지원하지 않는 자료입니다.");
    }
    DownloadFile file = wikiAnalysisMaterialService.getPublicDownload(link.getMaterialId());
    if (!QuestionWikiMaterialLinkResponse.isHtml(file.fileName())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "HTML 자료가 아닙니다.");
    }
    return new HtmlPreview(file.bytes(), file.fileName());
  }

  public record HtmlPreview(byte[] bytes, String fileName) {}

  /* ── helpers ──────────────────────────────────────────────────── */

  QuestionWiki loadVisible(Long questionWikiId) {
    if (questionWikiId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "문항 ID가 필요합니다.");
    }
    return questionWikiRepository.findVisibleById(questionWikiId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문항을 찾을 수 없습니다."));
  }

  private java.util.Optional<QuestionWikiMaterialLinkResponse> resolveMaterial(QuestionWikiMaterialLink link) {
    return switch (link.getMaterialKind()) {
      case EXAM_ORIGINAL -> wikiExamOriginalRepository.findById(link.getMaterialId())
          .filter(o -> !o.isDeleted())
          .map(o -> QuestionWikiMaterialLinkResponse.of(link, o.getTitle(), null, o.getOriginalFileName()));
      case ANALYSIS_MATERIAL -> wikiAnalysisMaterialRepository.findById(link.getMaterialId())
          .filter(m -> !m.isDeleted())
          .map(m -> QuestionWikiMaterialLinkResponse.of(
              link, m.getTitle(), m.getDescription(), m.getOriginalFileName()));
    };
  }

  private void ensureMaterialExists(QuestionWikiMaterialKind kind, Long materialId) {
    boolean exists = switch (kind) {
      case EXAM_ORIGINAL -> wikiExamOriginalRepository.findById(materialId)
          .filter(o -> !o.isDeleted()).isPresent();
      case ANALYSIS_MATERIAL -> wikiAnalysisMaterialRepository.findById(materialId)
          .filter(m -> !m.isDeleted()).isPresent();
    };
    if (!exists) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "연결할 자료를 찾을 수 없습니다.");
    }
  }

  private boolean matches(String needle, String... fields) {
    if (needle.isEmpty()) {
      return true;
    }
    for (String f : fields) {
      if (f != null && f.toLowerCase(Locale.ROOT).contains(needle)) {
        return true;
      }
    }
    return false;
  }

  private List<String> stripBlank(String... values) {
    List<String> out = new ArrayList<>();
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        out.add(v.trim());
      }
    }
    return out;
  }

  private String defaultTitle(QuestionWiki q, String docLabel) {
    String base = q.getExamYear() + " 고" + q.getGrade() + " " + q.getExamLabel() + " " + q.getQuestionNumber() + "번";
    return blankToNull(docLabel) == null ? base : base + " " + docLabel.trim();
  }

  private String truncate(String value, int max) {
    if (value == null) {
      return null;
    }
    return value.length() > max ? value.substring(0, max) : value;
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
