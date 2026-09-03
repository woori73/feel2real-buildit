package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto.LinkableMaterialResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto.QuestionWikiContextResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto.QuestionWikiMaterialLinkResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.entity.WikiAnalysisMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.repository.WikiAnalysisMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.service.WikiAnalysisMaterialService;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.entity.WikiExamOriginal;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.entity.WikiExamOriginalDocType;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.repository.WikiExamOriginalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("QuestionWikiService — Context 조립 + 학습자료 검색/연결")
class QuestionWikiServiceTest {

  private QuestionWikiRepository questionWikiRepository;
  private QuestionThreadPostRepository threadPostRepository;
  private QuestionThreadVariantDetailRepository variantRepository;
  private QuestionThreadExamEvidenceRepository examEvidenceRepository;
  private QuestionThreadAttachmentRepository attachmentRepository;
  private QuestionWikiMaterialLinkRepository materialLinkRepository;
  private QuestionAiSummaryRepository aiSummaryRepository;
  private WikiExamOriginalRepository wikiExamOriginalRepository;
  private WikiAnalysisMaterialRepository wikiAnalysisMaterialRepository;
  private WikiAnalysisMaterialService wikiAnalysisMaterialService;
  private QuestionWikiService service;

  private QuestionWiki question;
  private User author;

  @BeforeEach
  void setUp() {
    questionWikiRepository = mock(QuestionWikiRepository.class);
    threadPostRepository = mock(QuestionThreadPostRepository.class);
    variantRepository = mock(QuestionThreadVariantDetailRepository.class);
    examEvidenceRepository = mock(QuestionThreadExamEvidenceRepository.class);
    attachmentRepository = mock(QuestionThreadAttachmentRepository.class);
    materialLinkRepository = mock(QuestionWikiMaterialLinkRepository.class);
    aiSummaryRepository = mock(QuestionAiSummaryRepository.class);
    wikiExamOriginalRepository = mock(WikiExamOriginalRepository.class);
    wikiAnalysisMaterialRepository = mock(WikiAnalysisMaterialRepository.class);
    wikiAnalysisMaterialService = mock(WikiAnalysisMaterialService.class);
    service = new QuestionWikiService(questionWikiRepository, threadPostRepository, variantRepository,
        examEvidenceRepository, attachmentRepository, materialLinkRepository, aiSummaryRepository,
        wikiExamOriginalRepository, wikiAnalysisMaterialRepository, wikiAnalysisMaterialService);

    question = QuestionWiki.builder().subject("ENGLISH").grade(2).examYear(2026)
        .examLabel("9월 전국연합학력평가").questionNumber(21).visible(true).build();
    ReflectionTestUtils.setField(question, "questionWikiId", 1L);
    author = User.builder().loginId("stu").password("pw").name("stu").role(Role.STUDENT).build();
    ReflectionTestUtils.setField(author, "userId", 10L);

    lenient().when(questionWikiRepository.findVisibleById(1L)).thenReturn(Optional.of(question));
    lenient().when(attachmentRepository.findByPost_PostIdAndVisibleTrueOrderByAttachmentIdAsc(anyLong()))
        .thenReturn(List.of());
    lenient().when(materialLinkRepository.findByQuestionWiki_QuestionWikiIdOrderByLinkIdAsc(1L))
        .thenReturn(List.of());
    lenient().when(threadPostRepository.findVisibleByQuestionWikiId(anyLong())).thenReturn(List.of());
    lenient().when(variantRepository.findVisibleByQuestionWikiId(anyLong())).thenReturn(List.of());
    lenient().when(examEvidenceRepository.findVisibleByQuestionWikiId(anyLong())).thenReturn(List.of());
    lenient().when(aiSummaryRepository
        .findFirstByQuestionWiki_QuestionWikiIdAndStatusOrderByVersionDesc(eq(1L), any()))
        .thenReturn(Optional.empty());
  }

  private QuestionThreadPost post(Long id, QuestionThreadDataKind kind, QuestionThreadPost parent) {
    QuestionThreadPost p = QuestionThreadPost.builder()
        .questionWiki(question).parentPost(parent).author(author)
        .category(QuestionThreadCategory.QUESTION).dataKind(kind).title(null).content("c" + id).build();
    ReflectionTestUtils.setField(p, "postId", id);
    return p;
  }

  private JwtTokenProvider.AuthenticatedPrincipal admin() {
    return new JwtTokenProvider.AuthenticatedPrincipal(99L, "admin", Role.ADMIN.name());
  }

  private JwtTokenProvider.AuthenticatedPrincipal student() {
    return new JwtTokenProvider.AuthenticatedPrincipal(10L, "stu", Role.STUDENT.name());
  }

  @Test
  @DisplayName("given 토론·변형문제·기출·답글이 섞인 THREAD when getContext then 성격별로 나뉘고 답글이 중첩된다")
  void getContext_separatesByDataKind() {
    QuestionThreadPost discRoot = post(1L, QuestionThreadDataKind.DISCUSSION, null);
    QuestionThreadPost reply = post(2L, QuestionThreadDataKind.DISCUSSION, discRoot);
    QuestionThreadPost variantRoot = post(3L, QuestionThreadDataKind.GENERATED_PRACTICE, null);
    QuestionThreadPost evidenceRoot = post(4L, QuestionThreadDataKind.EXAM_EVIDENCE, null);
    when(threadPostRepository.findVisibleByQuestionWikiId(1L))
        .thenReturn(List.of(discRoot, reply, variantRoot, evidenceRoot));

    QuestionThreadVariantDetail v = QuestionThreadVariantDetail.builder()
        .post(variantRoot).questionText("빈칸 문제").questionType("빈칸").answer("priorities").build();
    ReflectionTestUtils.setField(v, "variantId", 50L);
    when(variantRepository.findVisibleByQuestionWikiId(1L)).thenReturn(List.of(v));

    QuestionThreadExamEvidence e = QuestionThreadExamEvidence.builder()
        .post(evidenceRoot).schoolName("○○고").examPeriod("2026 2학기 중간").build();
    ReflectionTestUtils.setField(e, "evidenceId", 60L);
    when(examEvidenceRepository.findVisibleByQuestionWikiId(1L)).thenReturn(List.of(e));

    QuestionWikiContextResponse ctx = service.getContext(1L, student());

    assertThat(ctx.threads()).hasSize(1);
    assertThat(ctx.threads().get(0).replies()).hasSize(1);
    assertThat(ctx.variants()).hasSize(1);
    assertThat(ctx.variants().get(0).variantDetail().answer()).isEqualTo("priorities");
    assertThat(ctx.examEvidence()).hasSize(1);
    assertThat(ctx.examEvidence().get(0).examEvidence().schoolName()).isEqualTo("○○고");
    assertThat(ctx.counts().threadCount()).isEqualTo(4);
    assertThat(ctx.counts().discussionCount()).isEqualTo(1);
    assertThat(ctx.question().displayContext()).isEqualTo("고2 · 2026년 · 9월 전국연합학력평가 · 21번");
  }

  @Test
  @DisplayName("given 비로그인 when getContext then mine=false + 역할 라벨(학생)")
  void getContext_anonymous() {
    QuestionThreadPost discRoot = post(1L, QuestionThreadDataKind.DISCUSSION, null);
    when(threadPostRepository.findVisibleByQuestionWikiId(1L)).thenReturn(List.of(discRoot));

    QuestionWikiContextResponse ctx = service.getContext(1L, null);

    assertThat(ctx.threads().get(0).mine()).isFalse();
    assertThat(ctx.threads().get(0).canManage()).isFalse();
    assertThat(ctx.threads().get(0).participantType()).isEqualTo("학생");
  }

  @Test
  @DisplayName("given 연결된 학습자료(분석자료) when getContext then 사람이 읽을 제목/설명 카드로 조립된다")
  void getContext_resolvesMaterialCard() {
    QuestionWikiMaterialLink link = QuestionWikiMaterialLink.builder()
        .questionWiki(question).materialKind(QuestionWikiMaterialKind.ANALYSIS_MATERIAL)
        .materialId(7L).label("상세 해설지").build();
    ReflectionTestUtils.setField(link, "linkId", 3L);
    when(materialLinkRepository.findByQuestionWiki_QuestionWikiIdOrderByLinkIdAsc(1L))
        .thenReturn(List.of(link));

    WikiAnalysisMaterial mat = WikiAnalysisMaterial.builder()
        .title("2026 고2 9월 20번 상세 해설").description("빈칸 추론 해설")
        .schoolName("전국연합").grade(2).year(2026).semester("공통").examType("9월 전국연합학력평가")
        .subject("영어").contentType("application/pdf").originalFileName("q20.pdf")
        .storageKey("k").fileSize(1024L).visible(true).build();
    ReflectionTestUtils.setField(mat, "wikiAnalysisMaterialId", 7L);
    when(wikiAnalysisMaterialRepository.findById(7L)).thenReturn(Optional.of(mat));

    QuestionWikiContextResponse ctx = service.getContext(1L, null);

    assertThat(ctx.materials()).hasSize(1);
    QuestionWikiMaterialLinkResponse card = ctx.materials().get(0);
    assertThat(card.label()).isEqualTo("상세 해설지");
    assertThat(card.title()).isEqualTo("2026 고2 9월 20번 상세 해설");
    assertThat(card.description()).isEqualTo("빈칸 추론 해설");
    assertThat(card.viewUrl()).isEqualTo("/api/wiki/analysis-materials/7/download");
    assertThat(card.materialId()).isEqualTo(7L);
  }

  @Test
  @DisplayName("given 검색어 when searchLinkableMaterials(ADMIN) then 제목·학교·파일명 매칭만 반환")
  void searchLinkableMaterials_filters() {
    WikiExamOriginal original = WikiExamOriginal.builder()
        .title("2026 고2 9월 영어 문제지").grade(2).examLabel("2026 9월 모의고사")
        .docType(WikiExamOriginalDocType.QUESTION).originalFileName("g2-sep.pdf")
        .storageKey("k").fileSize(1L).visible(true).build();
    ReflectionTestUtils.setField(original, "wikiExamOriginalId", 11L);
    WikiAnalysisMaterial mat = WikiAnalysisMaterial.builder()
        .title("서울고 1학기 중간 분석").schoolName("서울고").grade(1).year(2026).semester("1학기")
        .examType("중간고사").subject("영어").contentType("application/pdf").originalFileName("seoul.pdf")
        .storageKey("k").fileSize(1L).visible(true).build();
    ReflectionTestUtils.setField(mat, "wikiAnalysisMaterialId", 22L);
    when(wikiExamOriginalRepository.findAdminList()).thenReturn(List.of(original));
    when(wikiAnalysisMaterialRepository.findAdminList()).thenReturn(List.of(mat));

    List<LinkableMaterialResponse> hits = service.searchLinkableMaterials(admin(), "9월");

    assertThat(hits).hasSize(1);
    assertThat(hits.get(0).materialKind()).isEqualTo("EXAM_ORIGINAL");
    assertThat(hits.get(0).materialId()).isEqualTo(11L);
  }

  @Test
  @DisplayName("given 학생 when searchLinkableMaterials then 403")
  void searchLinkableMaterials_studentDenied() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.searchLinkableMaterials(student(), "x"))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }
}
