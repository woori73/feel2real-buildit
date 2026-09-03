package f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.dto.QuestionWikiContextResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.questionwiki.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
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

@DisplayName("QuestionWikiService — 문항 Context 조립: 데이터 성격 분리 + 답글 중첩 + 카운트")
class QuestionWikiServiceTest {

  private QuestionWikiRepository questionWikiRepository;
  private QuestionThreadPostRepository threadPostRepository;
  private QuestionThreadVariantDetailRepository variantRepository;
  private QuestionThreadExamEvidenceRepository examEvidenceRepository;
  private QuestionThreadAttachmentRepository attachmentRepository;
  private QuestionWikiMaterialLinkRepository materialLinkRepository;
  private QuestionAiSummaryRepository aiSummaryRepository;
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
    service = new QuestionWikiService(questionWikiRepository, threadPostRepository, variantRepository,
        examEvidenceRepository, attachmentRepository, materialLinkRepository, aiSummaryRepository);

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

    QuestionWikiContextResponse ctx = service.getContext(1L, principal());

    assertThat(ctx.threads()).hasSize(1);
    assertThat(ctx.threads().get(0).replies()).hasSize(1);
    assertThat(ctx.variants()).hasSize(1);
    assertThat(ctx.variants().get(0).variantDetail().answer()).isEqualTo("priorities");
    assertThat(ctx.examEvidence()).hasSize(1);
    assertThat(ctx.examEvidence().get(0).examEvidence().schoolName()).isEqualTo("○○고");
    assertThat(ctx.counts().threadCount()).isEqualTo(4);
    assertThat(ctx.counts().discussionCount()).isEqualTo(1);
    assertThat(ctx.counts().variantCount()).isEqualTo(1);
    assertThat(ctx.counts().examEvidenceCount()).isEqualTo(1);
    assertThat(ctx.question().displayContext()).isEqualTo("고2 · 2026년 · 9월 전국연합학력평가 · 21번");
  }

  @Test
  @DisplayName("given 비로그인 when getContext then mine=false 로 조립된다")
  void getContext_anonymous() {
    QuestionThreadPost discRoot = post(1L, QuestionThreadDataKind.DISCUSSION, null);
    when(threadPostRepository.findVisibleByQuestionWikiId(1L)).thenReturn(List.of(discRoot));
    when(variantRepository.findVisibleByQuestionWikiId(1L)).thenReturn(List.of());
    when(examEvidenceRepository.findVisibleByQuestionWikiId(1L)).thenReturn(List.of());

    QuestionWikiContextResponse ctx = service.getContext(1L, null);

    assertThat(ctx.threads().get(0).mine()).isFalse();
    assertThat(ctx.threads().get(0).canManage()).isFalse();
    assertThat(ctx.threads().get(0).participantType()).isEqualTo("학생");
  }

  private JwtTokenProvider.AuthenticatedPrincipal principal() {
    return new JwtTokenProvider.AuthenticatedPrincipal(10L, "stu", Role.STUDENT.name());
  }
}
