package f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.ConversationSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationSourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.dto.LearningMaterialResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.entity.LearningMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.entity.LearningMaterialAnswer;
import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.entity.LearningMaterialFeedback;
import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.entity.LearningMaterialStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.repository.LearningMaterialAnswerRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.repository.LearningMaterialFeedbackRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.repository.LearningMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.SourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.repository.SourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.UploadSizeGuard;

@DisplayName("LearningMaterialService — Phase 2 §5.5 학습자료 부여 + 풀이 재업로드")
class LearningMaterialServiceTest {

  private LearningMaterialRepository materialRepository;
  private LearningMaterialAnswerRepository answerRepository;
  private LearningMaterialFeedbackRepository feedbackRepository;
  private ConversationRepository conversationRepository;
  private SourceRepository sourceRepository;
  private UserRepository userRepository;
  private FileStorage fileStorage;
  private AuthenticatedUserResolver authResolver;
  private ConversationSourceRepository conversationSourceRepository;
  private ObjectMapper objectMapper;

  private LearningMaterialService service;

  private User tutor;       // userId=10, TUTOR
  private User adminUser;   // userId=11, ADMIN
  private User studentUser; // userId=20, STUDENT
  private Student student;  // studentId=200
  private Conversation conversation; // conversationId=500

  @BeforeEach
  void setUp() {
    materialRepository = mock(LearningMaterialRepository.class);
    answerRepository = mock(LearningMaterialAnswerRepository.class);
    feedbackRepository = mock(LearningMaterialFeedbackRepository.class);
    conversationRepository = mock(ConversationRepository.class);
    sourceRepository = mock(SourceRepository.class);
    userRepository = mock(UserRepository.class);
    fileStorage = mock(FileStorage.class);
    authResolver = mock(AuthenticatedUserResolver.class);
    conversationSourceRepository = mock(ConversationSourceRepository.class);
    objectMapper = new ObjectMapper();

    service = new LearningMaterialService(
        materialRepository, answerRepository, feedbackRepository, conversationRepository, sourceRepository,
        userRepository, fileStorage, authResolver, conversationSourceRepository, objectMapper,
        new UploadSizeGuard(20L * 1024 * 1024));

    tutor = User.builder().loginId("tutor").password("pw").name("강사").phone("010").build();
    ReflectionTestUtils.setField(tutor, "userId", 10L);

    adminUser = User.builder().loginId("admin").password("pw").name("관리자").phone("010").build();
    ReflectionTestUtils.setField(adminUser, "userId", 11L);

    studentUser = User.builder().loginId("student").password("pw").name("학생").phone("010").build();
    ReflectionTestUtils.setField(studentUser, "userId", 20L);

    student = Student.builder().user(studentUser).schoolName("현대고").grade((byte) 2).build();
    ReflectionTestUtils.setField(student, "studentId", 200L);

    conversation = Conversation.builder().student(student).title("대화").build();
    ReflectionTestUtils.setField(conversation, "conversationId", 500L);

    when(sourceRepository.save(any(Source.class))).thenAnswer(inv -> {
      Source s = inv.getArgument(0);
      ReflectionTestUtils.setField(s, "sourceId", 9000L);
      return s;
    });
    when(materialRepository.save(any(LearningMaterial.class))).thenAnswer(inv -> {
      LearningMaterial m = inv.getArgument(0);
      ReflectionTestUtils.setField(m, "materialId", 7000L);
      return m;
    });
    when(answerRepository.save(any(LearningMaterialAnswer.class))).thenAnswer(inv -> inv.getArgument(0));
    when(feedbackRepository.save(any(LearningMaterialFeedback.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  /* ── assign ───────────────────────────────────────────── */

  @Test
  @DisplayName("assign 성공 — PDF + title 정상이면 source/material 모두 INSERT")
  void assign_success() throws Exception {
    // given
    when(conversationRepository.findById(500L)).thenReturn(Optional.of(conversation));
    when(userRepository.findById(10L)).thenReturn(Optional.of(tutor));
    when(fileStorage.saveRaw(eq(10L), eq("hw.pdf"), any(), anyLong())).thenReturn("/store/k1");

    MultipartFile pdf = new MockMultipartFile(
        "file", "hw.pdf", "application/pdf", "dummy".getBytes());

    // when
    LearningMaterialResponse res = service.assign(500L, 10L, pdf, "1과 문법 숙제", "내일까지 풀어와");

    // then
    assertThat(res.materialId()).isEqualTo(7000L);
    assertThat(res.title()).isEqualTo("1과 문법 숙제");
    assertThat(res.status()).isEqualTo(LearningMaterialStatus.ASSIGNED);
    assertThat(res.studentId()).isEqualTo(200L);
    verify(authResolver).verifyAdminOrTutor();
    verify(authResolver).verifyOwnsConversation(conversation);
    ArgumentCaptor<Source> srcCap = ArgumentCaptor.forClass(Source.class);
    verify(sourceRepository).save(srcCap.capture());
    assertThat(srcCap.getValue().getSourceType()).isEqualTo(SourceType.HOMEWORK_PROBLEM);
    assertThat(srcCap.getValue().getFileType()).isEqualTo("UNKNOWN");
    // 문제 source가 conversation_sources에 편입됨
    verify(conversationSourceRepository).save(any(ConversationSource.class));
    assertThat(conversation.getMetadata()).contains("9000");  // SourceSummary append (sourceId=9000)
  }

  @Test
  @DisplayName("assign 성공 — PDF 없이 title만 → problemSource=null 저장 + source/링크 스킵")
  void assign_success_without_pdf() {
    // given
    when(conversationRepository.findById(500L)).thenReturn(Optional.of(conversation));
    when(userRepository.findById(10L)).thenReturn(Optional.of(tutor));

    // when — file=null
    LearningMaterialResponse res = service.assign(500L, 10L, null, "PDF 없는 숙제", "교재 12쪽 풀어와");

    // then — material은 저장되지만 problemSource 관련은 비어있음
    assertThat(res.materialId()).isEqualTo(7000L);
    assertThat(res.title()).isEqualTo("PDF 없는 숙제");
    assertThat(res.hasProblemFile()).isFalse();
    assertThat(res.problemSourceId()).isNull();
    assertThat(res.problemFileName()).isNull();
    verify(authResolver).verifyAdminOrTutor();
    verify(authResolver).verifyOwnsConversation(conversation);
    // source 생성 / conversation 링크 모두 스킵
    verify(sourceRepository, never()).save(any(Source.class));
    verify(conversationSourceRepository, never()).save(any(ConversationSource.class));
    verify(materialRepository).save(any(LearningMaterial.class));
  }

  @Test
  @DisplayName("assign 성공 — 빈 파일(file.isEmpty)도 PDF 없는 부여로 처리")
  void assign_success_with_empty_file() {
    // given
    when(conversationRepository.findById(500L)).thenReturn(Optional.of(conversation));
    when(userRepository.findById(10L)).thenReturn(Optional.of(tutor));
    MultipartFile empty = new MockMultipartFile("file", "", "application/pdf", new byte[0]);

    // when
    LearningMaterialResponse res = service.assign(500L, 10L, empty, "빈 파일 숙제", null);

    // then
    assertThat(res.hasProblemFile()).isFalse();
    verify(sourceRepository, never()).save(any(Source.class));
  }

  @Test
  @DisplayName("assign 거부 — 허용되지 않는 확장자")
  void assign_rejects_disallowed_extension() {
    // given
    MultipartFile txt = new MockMultipartFile(
        "file", "hw.txt", "text/plain", "x".getBytes());

    // when & then
    assertThatThrownBy(() -> service.assign(500L, 10L, txt, "t", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("PDF / JPG / PNG / WebP");
    verify(materialRepository, never()).save(any());
  }

  @Test
  @DisplayName("assign 거부 — title 공백")
  void assign_rejects_blank_title() {
    // given
    MultipartFile pdf = new MockMultipartFile(
        "file", "hw.pdf", "application/pdf", "x".getBytes());

    // when & then
    assertThatThrownBy(() -> service.assign(500L, 10L, pdf, "   ", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("제목");
  }

  @Test
  @DisplayName("assign 거부 — STUDENT는 verifyAdminOrTutor에서 차단")
  void assign_rejects_student_role() {
    // given
    doThrow(new AccessDeniedException("관리자 또는 강사 권한이 필요합니다."))
        .when(authResolver).verifyAdminOrTutor();
    MultipartFile pdf = new MockMultipartFile(
        "file", "hw.pdf", "application/pdf", "x".getBytes());

    // when & then
    assertThatThrownBy(() -> service.assign(500L, 20L, pdf, "t", null))
        .isInstanceOf(AccessDeniedException.class);
    verify(materialRepository, never()).save(any());
  }

  /* ── complete ─────────────────────────────────────────── */

  @Test
  @DisplayName("complete 첫 업로드 — answers INSERT + material COMPLETED")
  void complete_first_upload() throws Exception {
    // given
    LearningMaterial material = sampleMaterial();
    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));
    stubStudentPrincipal();
    when(answerRepository.findByMaterial_MaterialIdOrderByOrderNumAsc(7000L)).thenReturn(List.of());
    when(fileStorage.saveRaw(eq(20L), anyString(), any(), anyLong())).thenReturn("/store/ans1");

    List<MultipartFile> files = List.of(
        new MockMultipartFile("files", "ans1.jpg", "image/jpeg", "a".getBytes()));

    // when
    LearningMaterialResponse res = service.complete(7000L, files);

    // then
    assertThat(material.getStatus()).isEqualTo(LearningMaterialStatus.COMPLETED);
    assertThat(material.getCompletedAt()).isNotNull();
    verify(authResolver).verifyOwnsLearningMaterial(material);
    verify(answerRepository, times(1)).save(any(LearningMaterialAnswer.class));
    verify(fileStorage, never()).delete(anyString(), anyString());  // 기존 파일 없음
    // 답안 source가 conversation_sources에 편입됨
    verify(conversationSourceRepository).save(any(ConversationSource.class));
  }

  @Test
  @DisplayName("complete 재업로드 — 기존 answers + storage 정리 후 새로 INSERT")
  void complete_replaces_existing() throws Exception {
    // given
    LearningMaterial material = sampleMaterial();
    Source prevSrc = sourceOf(8500L, studentUser, "ans-prev.png", "UNKNOWN", "/store/prev");
    LearningMaterialAnswer prev = LearningMaterialAnswer.builder()
        .material(material).source(prevSrc).orderNum(1).build();
    ReflectionTestUtils.setField(prev, "answerId", 4000L);

    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));
    stubStudentPrincipal();
    when(answerRepository.findByMaterial_MaterialIdOrderByOrderNumAsc(7000L))
        .thenReturn(List.of(prev), List.of());  // first call: existing, second call: empty (after wipe + INSERT, mocked)
    when(fileStorage.saveRaw(eq(20L), anyString(), any(), anyLong())).thenReturn("/store/ans-new");

    List<MultipartFile> files = List.of(
        new MockMultipartFile("files", "ans-new.pdf", "application/pdf", "b".getBytes()));

    // when
    service.complete(7000L, files);

    // then — 기존 answer/source/storage 모두 정리
    verify(answerRepository).delete(prev);
    verify(sourceRepository).delete(prevSrc);
    verify(fileStorage).delete("/store/prev", "UNKNOWN");
    // 신규 INSERT
    verify(answerRepository, times(1)).save(any(LearningMaterialAnswer.class));
  }

  @Test
  @DisplayName("complete 재업로드 — C1 FK-safe 순서: 링크 제거 → metadata 제거 → source delete")
  void complete_replaces_existing_fk_safe_order() throws Exception {
    // given
    LearningMaterial material = sampleMaterial();
    Source prevSrc = sourceOf(8500L, studentUser, "ans-prev.png", "UNKNOWN", "/store/prev");
    LearningMaterialAnswer prev = LearningMaterialAnswer.builder()
        .material(material).source(prevSrc).orderNum(1).build();
    ReflectionTestUtils.setField(prev, "answerId", 4000L);
    material.markReviewed(tutor);  // 재제출 전 강사가 이미 확인한 상태

    // 기존 answer source가 metadata에 들어가 있다고 가정
    conversation.updateMetadata(
        new f2r.tutor_solution.private_tutor_solution_back.domain.conversation.dto.ConversationMetadata(
            null, null, new ArrayList<>())
            .withSourceAppended(
                new f2r.tutor_solution.private_tutor_solution_back.domain.conversation.dto.ConversationMetadata.SourceSummary(
                    8500L, "ans-prev.png"))
            .toJson(objectMapper));

    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));
    stubStudentPrincipal();
    when(answerRepository.findByMaterial_MaterialIdOrderByOrderNumAsc(7000L))
        .thenReturn(List.of(prev), List.of());
    when(fileStorage.saveRaw(eq(20L), anyString(), any(), anyLong())).thenReturn("/store/ans-new");

    List<MultipartFile> files = List.of(
        new MockMultipartFile("files", "ans-new.pdf", "application/pdf", "b".getBytes()));

    // when
    service.complete(7000L, files);

    // then — 링크/metadata 제거가 source delete보다 먼저 (InOrder)
    var inOrder = org.mockito.Mockito.inOrder(conversationSourceRepository, sourceRepository);
    inOrder.verify(conversationSourceRepository).deleteBySourceId(8500L);
    inOrder.verify(sourceRepository).delete(prevSrc);
    // metadata에서 기존 source 제거됨
    assertThat(conversation.getMetadata()).doesNotContain("8500");
    // 재제출 = 확인 리셋
    assertThat(material.isReviewed()).isFalse();
    // 신규 answer source 링크됨
    verify(conversationSourceRepository).save(any(ConversationSource.class));
  }

  @Test
  @DisplayName("complete #16 거부 — TUTOR가 학생 제출물 대치 시도 → 403")
  void complete_denied_for_tutor() {
    // given
    LearningMaterial material = sampleMaterial();
    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));
    when(authResolver.currentPrincipal()).thenReturn(Optional.of(
        new JwtTokenProvider.AuthenticatedPrincipal(10L, "tutor", Role.TUTOR.name())));

    List<MultipartFile> files = List.of(
        new MockMultipartFile("files", "ans.pdf", "application/pdf", "b".getBytes()));

    // when & then
    assertThatThrownBy(() -> service.complete(7000L, files))
        .isInstanceOf(AccessDeniedException.class);
    verify(answerRepository, never()).save(any());
  }

  @Test
  @DisplayName("complete #16 허용 — ADMIN은 운영 대리 제출 통과")
  void complete_allowed_for_admin() throws Exception {
    // given
    LearningMaterial material = sampleMaterial();
    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));
    when(authResolver.currentPrincipal()).thenReturn(Optional.of(
        new JwtTokenProvider.AuthenticatedPrincipal(11L, "admin", Role.ADMIN.name())));
    when(answerRepository.findByMaterial_MaterialIdOrderByOrderNumAsc(7000L)).thenReturn(List.of());
    when(fileStorage.saveRaw(eq(20L), anyString(), any(), anyLong())).thenReturn("/store/ans1");

    List<MultipartFile> files = List.of(
        new MockMultipartFile("files", "ans.pdf", "application/pdf", "b".getBytes()));

    // when
    service.complete(7000L, files);

    // then
    verify(answerRepository, times(1)).save(any(LearningMaterialAnswer.class));
  }

  @Test
  @DisplayName("complete 거부 — 빈 files 리스트")
  void complete_rejects_empty_files() {
    // given
    LearningMaterial material = sampleMaterial();
    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));
    stubStudentPrincipal();

    // when & then
    assertThatThrownBy(() -> service.complete(7000L, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("complete 거부 — 비허용 확장자")
  void complete_rejects_disallowed_extension() {
    // given
    LearningMaterial material = sampleMaterial();
    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));
    stubStudentPrincipal();

    MultipartFile bad = new MockMultipartFile(
        "files", "ans.exe", "application/x-msdownload", "x".getBytes());

    // when & then
    assertThatThrownBy(() -> service.complete(7000L, List.of(bad)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("PDF / JPG");
    verify(answerRepository, never()).save(any());
  }

  /* ── delete ───────────────────────────────────────────── */

  @Test
  @DisplayName("delete cascade — answers + storage + material + problem source 모두 정리")
  void delete_cascades() {
    // given
    LearningMaterial material = sampleMaterial();
    Source ansSrc = sourceOf(8500L, studentUser, "ans.jpg", "UNKNOWN", "/store/ans");
    LearningMaterialAnswer ans = LearningMaterialAnswer.builder()
        .material(material).source(ansSrc).orderNum(1).build();

    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));
    when(authResolver.currentPrincipal()).thenReturn(Optional.of(
        new JwtTokenProvider.AuthenticatedPrincipal(10L, "tutor", Role.TUTOR.name())));
    when(answerRepository.findByMaterial_MaterialIdOrderByOrderNumAsc(7000L)).thenReturn(List.of(ans));

    // when — assignedBy=tutor(10L), requester=10L → 통과
    service.delete(7000L, 10L);

    // then
    verify(answerRepository).delete(ans);
    verify(sourceRepository).delete(ansSrc);
    verify(fileStorage).delete("/store/ans", "UNKNOWN");
    verify(materialRepository).delete(material);
    verify(sourceRepository).delete(material.getProblemSource());
    verify(fileStorage).delete(material.getProblemSource().getStorageKey(), "UNKNOWN");
  }

  @Test
  @DisplayName("delete cascade — problem/answer source의 conversation 링크+metadata 정리 (FK-safe)")
  void delete_cascades_conversation_links() {
    // given
    LearningMaterial material = sampleMaterial();  // problemSource sourceId=8000
    Source ansSrc = sourceOf(8500L, studentUser, "ans.jpg", "UNKNOWN", "/store/ans");
    LearningMaterialAnswer ans = LearningMaterialAnswer.builder()
        .material(material).source(ansSrc).orderNum(1).build();

    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));
    when(authResolver.currentPrincipal()).thenReturn(Optional.of(
        new JwtTokenProvider.AuthenticatedPrincipal(10L, "tutor", Role.TUTOR.name())));
    when(answerRepository.findByMaterial_MaterialIdOrderByOrderNumAsc(7000L)).thenReturn(List.of(ans));

    // when
    service.delete(7000L, 10L);

    // then — answer + problem source 모두 링크 제거 (FK-safe: source delete 전)
    var inOrder = org.mockito.Mockito.inOrder(conversationSourceRepository, sourceRepository);
    inOrder.verify(conversationSourceRepository).deleteBySourceId(8500L);
    inOrder.verify(sourceRepository).delete(ansSrc);
    inOrder.verify(conversationSourceRepository).deleteBySourceId(8000L);
    inOrder.verify(sourceRepository).delete(material.getProblemSource());
  }

  @Test
  @DisplayName("delete 거부 — 부여자가 아니고 ADMIN도 아니면 403")
  void delete_denied_for_non_owner_non_admin() {
    // given — material.assignedBy=tutor(10L), 호출자=다른 TUTOR(99L)
    LearningMaterial material = sampleMaterial();
    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));
    when(authResolver.currentPrincipal()).thenReturn(Optional.of(
        new JwtTokenProvider.AuthenticatedPrincipal(99L, "other-tutor", Role.TUTOR.name())));

    // when & then
    assertThatThrownBy(() -> service.delete(7000L, 99L))
        .isInstanceOf(AccessDeniedException.class);
    verify(materialRepository, never()).delete(any());
  }

  @Test
  @DisplayName("delete 허용 — ADMIN은 부여자 무관 통과")
  void delete_admin_bypass() {
    // given — material.assignedBy=tutor(10L), 호출자=admin(11L)
    LearningMaterial material = sampleMaterial();
    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));
    when(authResolver.currentPrincipal()).thenReturn(Optional.of(
        new JwtTokenProvider.AuthenticatedPrincipal(11L, "admin", Role.ADMIN.name())));
    when(answerRepository.findByMaterial_MaterialIdOrderByOrderNumAsc(7000L)).thenReturn(List.of());

    // when
    service.delete(7000L, 11L);

    // then
    verify(materialRepository).delete(material);
  }

  @Test
  @DisplayName("delete — PDF 없이 부여된 숙제(problemSource=null)는 problem source 정리 스킵")
  void delete_without_problem_source() {
    // given — problemSource=null 인 숙제
    LearningMaterial material = sampleMaterialNoPdf();
    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));
    when(authResolver.currentPrincipal()).thenReturn(Optional.of(
        new JwtTokenProvider.AuthenticatedPrincipal(10L, "tutor", Role.TUTOR.name())));
    when(answerRepository.findByMaterial_MaterialIdOrderByOrderNumAsc(7000L)).thenReturn(List.of());
    when(feedbackRepository.findByMaterial_MaterialIdOrderByOrderNumAsc(7000L)).thenReturn(List.of());

    // when
    service.delete(7000L, 10L);

    // then — material은 삭제되지만 problem source 정리(sourceRepository.delete)는 호출되지 않음
    verify(materialRepository).delete(material);
    verify(sourceRepository, never()).delete(any(Source.class));
    verify(fileStorage, never()).delete(anyString(), anyString());
  }

  /* ── getProblemFileBundle (view/download) null 가드 ────── */

  @Test
  @DisplayName("getProblemFileBundle 404 — PDF 없이 부여된 숙제는 NOT_FOUND")
  void getProblemFileBundle_404_when_no_pdf() {
    // given
    LearningMaterial material = sampleMaterialNoPdf();
    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));

    // when & then
    assertThatThrownBy(() -> service.getProblemFileBundle(7000L))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageContaining("404");
    verify(authResolver).verifyOwnsLearningMaterial(material);
  }

  /* ── setReviewed ──────────────────────────────────────── */

  @Test
  @DisplayName("setReviewed on — markReviewed + reviewedBy 기록")
  void setReviewed_on() {
    // given
    LearningMaterial material = sampleMaterial();
    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));
    when(answerRepository.findByMaterial_MaterialIdOrderByOrderNumAsc(7000L)).thenReturn(List.of());
    when(authResolver.currentPrincipal()).thenReturn(Optional.of(
        new JwtTokenProvider.AuthenticatedPrincipal(10L, "tutor", Role.TUTOR.name())));
    when(userRepository.findById(10L)).thenReturn(Optional.of(tutor));

    // when
    LearningMaterialResponse res = service.setReviewed(7000L, true);

    // then
    assertThat(material.isReviewed()).isTrue();
    assertThat(material.getReviewedBy()).isEqualTo(tutor);
    assertThat(res.reviewed()).isTrue();
    verify(authResolver).verifyAdminOrTutor();
    verify(authResolver).verifyOwnsLearningMaterial(material);
  }

  @Test
  @DisplayName("setReviewed off — unmarkReviewed")
  void setReviewed_off() {
    // given
    LearningMaterial material = sampleMaterial();
    material.markReviewed(tutor);
    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));
    when(answerRepository.findByMaterial_MaterialIdOrderByOrderNumAsc(7000L)).thenReturn(List.of());

    // when
    LearningMaterialResponse res = service.setReviewed(7000L, false);

    // then
    assertThat(material.isReviewed()).isFalse();
    assertThat(material.getReviewedBy()).isNull();
    assertThat(res.reviewed()).isFalse();
  }

  @Test
  @DisplayName("setReviewed 거부 — 학생(verifyAdminOrTutor)에서 차단")
  void setReviewed_denied_for_student() {
    // given
    LearningMaterial material = sampleMaterial();
    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));
    doThrow(new AccessDeniedException("관리자 또는 강사 권한이 필요합니다."))
        .when(authResolver).verifyAdminOrTutor();

    // when & then
    assertThatThrownBy(() -> service.setReviewed(7000L, true))
        .isInstanceOf(AccessDeniedException.class);
  }

  /* ── addFeedback ──────────────────────────────────────── */

  @Test
  @DisplayName("addFeedback 성공 — TUTOR가 PDF 업로드 → source/feedback INSERT + 대화 링크")
  void addFeedback_success_tutor() throws Exception {
    // given
    LearningMaterial material = sampleMaterial();
    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));
    when(authResolver.currentPrincipal()).thenReturn(Optional.of(
        new JwtTokenProvider.AuthenticatedPrincipal(10L, "tutor", Role.TUTOR.name())));
    when(userRepository.findById(10L)).thenReturn(Optional.of(tutor));
    when(feedbackRepository.findByMaterial_MaterialIdOrderByOrderNumAsc(7000L)).thenReturn(List.of());
    when(fileStorage.saveRaw(eq(10L), anyString(), any(), anyLong())).thenReturn("/store/fb1");

    List<MultipartFile> files = List.of(
        new MockMultipartFile("files", "feedback.pdf", "application/pdf", "f".getBytes()));

    // when
    service.addFeedback(7000L, null, files);

    // then
    verify(authResolver).verifyAdminOrTutor();
    verify(authResolver).verifyOwnsLearningMaterial(material);
    ArgumentCaptor<Source> srcCap = ArgumentCaptor.forClass(Source.class);
    verify(sourceRepository).save(srcCap.capture());
    assertThat(srcCap.getValue().getSourceType()).isEqualTo(SourceType.HOMEWORK_FEEDBACK);
    verify(feedbackRepository, times(1)).save(any(LearningMaterialFeedback.class));
    // 피드백 source가 conversation_sources에 편입됨
    verify(conversationSourceRepository).save(any(ConversationSource.class));
  }

  @Test
  @DisplayName("addFeedback 성공 — ADMIN도 통과")
  void addFeedback_success_admin() throws Exception {
    // given
    LearningMaterial material = sampleMaterial();
    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));
    when(authResolver.currentPrincipal()).thenReturn(Optional.of(
        new JwtTokenProvider.AuthenticatedPrincipal(11L, "admin", Role.ADMIN.name())));
    when(userRepository.findById(11L)).thenReturn(Optional.of(adminUser));
    when(feedbackRepository.findByMaterial_MaterialIdOrderByOrderNumAsc(7000L)).thenReturn(List.of());
    when(fileStorage.saveRaw(eq(11L), anyString(), any(), anyLong())).thenReturn("/store/fb1");

    List<MultipartFile> files = List.of(
        new MockMultipartFile("files", "feedback.pdf", "application/pdf", "f".getBytes()));

    // when
    service.addFeedback(7000L, null, files);

    // then
    verify(feedbackRepository, times(1)).save(any(LearningMaterialFeedback.class));
  }


  @Test
  @DisplayName("addFeedback 성공 — 첨부 파일 없이도 피드백 row 등록")
  void addFeedback_success_without_file() {
    // given
    LearningMaterial material = sampleMaterial();
    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));
    when(authResolver.currentPrincipal()).thenReturn(Optional.of(
        new JwtTokenProvider.AuthenticatedPrincipal(10L, "tutor", Role.TUTOR.name())));
    when(userRepository.findById(10L)).thenReturn(Optional.of(tutor));
    when(feedbackRepository.findByMaterial_MaterialIdOrderByOrderNumAsc(7000L)).thenReturn(List.of());

    // when
    service.addFeedback(7000L, "관계대명사 보충 필요", List.of());

    // then
    ArgumentCaptor<LearningMaterialFeedback> cap =
        ArgumentCaptor.forClass(LearningMaterialFeedback.class);
    verify(feedbackRepository).save(cap.capture());
    assertThat(cap.getValue().getSource()).isNull();
    assertThat(cap.getValue().getFeedbackText()).isEqualTo("관계대명사 보충 필요");
    assertThat(cap.getValue().getOrderNum()).isEqualTo(1);
    verify(sourceRepository, never()).save(any(Source.class));
    verify(conversationSourceRepository, never()).save(any(ConversationSource.class));
  }


  @Test
  @DisplayName("addFeedback 거부 — 설명과 자료 파일이 모두 없으면 차단")
  void addFeedback_rejects_empty_text_and_files() {
    // given
    LearningMaterial material = sampleMaterial();
    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));

    // when & then
    assertThatThrownBy(() -> service.addFeedback(7000L, "  ", List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("피드백 설명 또는 자료 파일");
    verify(feedbackRepository, never()).save(any());
  }

  @Test
  @DisplayName("addFeedback 거부 — 허용되지 않는 확장자")
  void addFeedback_rejects_disallowed_extension() {
    // given
    LearningMaterial material = sampleMaterial();
    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));

    List<MultipartFile> files = List.of(
        new MockMultipartFile("files", "feedback.gif", "image/gif", "x".getBytes()));

    // when & then
    assertThatThrownBy(() -> service.addFeedback(7000L, null, files))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("PDF / JPG / PNG / WebP");
    verify(feedbackRepository, never()).save(any());
  }

  @Test
  @DisplayName("addFeedback 거부 — 학생(verifyAdminOrTutor)에서 차단")
  void addFeedback_denied_for_student() {
    // given
    LearningMaterial material = sampleMaterial();
    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));
    doThrow(new AccessDeniedException("관리자 또는 강사 권한이 필요합니다."))
        .when(authResolver).verifyAdminOrTutor();

    List<MultipartFile> files = List.of(
        new MockMultipartFile("files", "feedback.pdf", "application/pdf", "f".getBytes()));

    // when & then
    assertThatThrownBy(() -> service.addFeedback(7000L, null, files))
        .isInstanceOf(AccessDeniedException.class);
    verify(feedbackRepository, never()).save(any());
  }

  @Test
  @DisplayName("addFeedback append — 기존 order_num 최대+1 부터 이어붙임")
  void addFeedback_appends_order() throws Exception {
    // given — 기존 피드백 order_num=1,2 존재
    LearningMaterial material = sampleMaterial();
    Source existingSrc = sourceOf(8600L, tutor, "fb-old.pdf", "UNKNOWN", "/store/fb-old");
    LearningMaterialFeedback existing = LearningMaterialFeedback.builder()
        .material(material).source(existingSrc).orderNum(2).build();
    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));
    when(authResolver.currentPrincipal()).thenReturn(Optional.of(
        new JwtTokenProvider.AuthenticatedPrincipal(10L, "tutor", Role.TUTOR.name())));
    when(userRepository.findById(10L)).thenReturn(Optional.of(tutor));
    when(feedbackRepository.findByMaterial_MaterialIdOrderByOrderNumAsc(7000L))
        .thenReturn(List.of(existing));
    when(fileStorage.saveRaw(eq(10L), anyString(), any(), anyLong())).thenReturn("/store/fb-new");

    List<MultipartFile> files = List.of(
        new MockMultipartFile("files", "fb-new.pdf", "application/pdf", "f".getBytes()));

    // when
    service.addFeedback(7000L, null, files);

    // then — 새 feedback의 order_num = 기존 최대(2)+1 = 3
    ArgumentCaptor<LearningMaterialFeedback> cap =
        ArgumentCaptor.forClass(LearningMaterialFeedback.class);
    verify(feedbackRepository).save(cap.capture());
    assertThat(cap.getValue().getOrderNum()).isEqualTo(3);
  }

  /* ── deleteFeedback ───────────────────────────────────── */

  @Test
  @DisplayName("deleteFeedback FK-safe 순서 — 링크 제거 → feedback delete → source delete")
  void deleteFeedback_fk_safe_order() {
    // given
    LearningMaterial material = sampleMaterial();
    Source fbSrc = sourceOf(8600L, tutor, "fb.pdf", "UNKNOWN", "/store/fb");
    LearningMaterialFeedback feedback = LearningMaterialFeedback.builder()
        .material(material).source(fbSrc).orderNum(1).build();
    ReflectionTestUtils.setField(feedback, "feedbackId", 6000L);

    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));
    when(authResolver.currentPrincipal()).thenReturn(Optional.of(
        new JwtTokenProvider.AuthenticatedPrincipal(10L, "tutor", Role.TUTOR.name())));
    when(feedbackRepository.findById(6000L)).thenReturn(Optional.of(feedback));

    // when
    service.deleteFeedback(7000L, 6000L);

    // then — 링크 제거가 source delete보다 먼저 (InOrder)
    var inOrder = org.mockito.Mockito.inOrder(
        conversationSourceRepository, feedbackRepository, sourceRepository);
    inOrder.verify(feedbackRepository).delete(feedback);
    inOrder.verify(conversationSourceRepository).deleteBySourceId(8600L);
    inOrder.verify(sourceRepository).delete(fbSrc);
    verify(fileStorage).delete("/store/fb", "UNKNOWN");
    verify(authResolver).verifyAdminOrTutor();
    verify(authResolver).verifyOwnsLearningMaterial(material);
  }


  @Test
  @DisplayName("deleteFeedback 성공 — 첨부 없는 피드백은 source 정리 없이 row만 삭제")
  void deleteFeedback_without_file() {
    // given
    LearningMaterial material = sampleMaterial();
    LearningMaterialFeedback feedback = LearningMaterialFeedback.builder()
        .material(material).source(null).orderNum(1).build();
    ReflectionTestUtils.setField(feedback, "feedbackId", 6001L);

    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));
    when(authResolver.currentPrincipal()).thenReturn(Optional.of(
        new JwtTokenProvider.AuthenticatedPrincipal(10L, "tutor", Role.TUTOR.name())));
    when(feedbackRepository.findById(6001L)).thenReturn(Optional.of(feedback));

    // when
    service.deleteFeedback(7000L, 6001L);

    // then
    verify(feedbackRepository).delete(feedback);
    verify(conversationSourceRepository, never()).deleteBySourceId(anyLong());
    verify(sourceRepository, never()).delete(any(Source.class));
    verify(fileStorage, never()).delete(anyString(), anyString());
  }

  @Test
  @DisplayName("deleteFeedback 거부 — 학생(verifyAdminOrTutor)에서 차단")
  void deleteFeedback_denied_for_student() {
    // given
    LearningMaterial material = sampleMaterial();
    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));
    doThrow(new AccessDeniedException("관리자 또는 강사 권한이 필요합니다."))
        .when(authResolver).verifyAdminOrTutor();

    // when & then
    assertThatThrownBy(() -> service.deleteFeedback(7000L, 6000L))
        .isInstanceOf(AccessDeniedException.class);
    verify(feedbackRepository, never()).delete(any());
  }

  /* ── complete 재제출 시 피드백 정리 ──────────────────────── */

  @Test
  @DisplayName("complete 재제출 — 기존 피드백도 FK-safe하게 삭제됨")
  void complete_wipes_stale_feedback() throws Exception {
    // given
    LearningMaterial material = sampleMaterial();
    Source fbSrc = sourceOf(8600L, tutor, "fb.pdf", "UNKNOWN", "/store/fb");
    LearningMaterialFeedback feedback = LearningMaterialFeedback.builder()
        .material(material).source(fbSrc).orderNum(1).build();

    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));
    stubStudentPrincipal();
    when(answerRepository.findByMaterial_MaterialIdOrderByOrderNumAsc(7000L)).thenReturn(List.of());
    when(feedbackRepository.findByMaterial_MaterialIdOrderByOrderNumAsc(7000L))
        .thenReturn(List.of(feedback), List.of());
    when(fileStorage.saveRaw(eq(20L), anyString(), any(), anyLong())).thenReturn("/store/ans-new");

    List<MultipartFile> files = List.of(
        new MockMultipartFile("files", "ans-new.pdf", "application/pdf", "b".getBytes()));

    // when
    service.complete(7000L, files);

    // then — 기존 피드백 row/source/storage 모두 정리 (FK-safe)
    var inOrder = org.mockito.Mockito.inOrder(
        feedbackRepository, conversationSourceRepository, sourceRepository);
    inOrder.verify(feedbackRepository).delete(feedback);
    inOrder.verify(conversationSourceRepository).deleteBySourceId(8600L);
    inOrder.verify(sourceRepository).delete(fbSrc);
    verify(fileStorage).delete("/store/fb", "UNKNOWN");
  }

  /* ── delete cascade 시 피드백 정리 ───────────────────────── */

  @Test
  @DisplayName("delete cascade — feedback source도 링크 제거 후 삭제 (FK-safe)")
  void delete_cascades_feedback_sources() {
    // given
    LearningMaterial material = sampleMaterial();
    Source fbSrc = sourceOf(8600L, tutor, "fb.pdf", "UNKNOWN", "/store/fb");
    LearningMaterialFeedback feedback = LearningMaterialFeedback.builder()
        .material(material).source(fbSrc).orderNum(1).build();

    when(materialRepository.findById(7000L)).thenReturn(Optional.of(material));
    when(authResolver.currentPrincipal()).thenReturn(Optional.of(
        new JwtTokenProvider.AuthenticatedPrincipal(10L, "tutor", Role.TUTOR.name())));
    when(answerRepository.findByMaterial_MaterialIdOrderByOrderNumAsc(7000L)).thenReturn(List.of());
    when(feedbackRepository.findByMaterial_MaterialIdOrderByOrderNumAsc(7000L))
        .thenReturn(List.of(feedback));

    // when
    service.delete(7000L, 10L);

    // then — feedback row 삭제 + 링크 제거 후 source 삭제 (material delete 전)
    var inOrder = org.mockito.Mockito.inOrder(
        feedbackRepository, conversationSourceRepository, sourceRepository, materialRepository);
    inOrder.verify(feedbackRepository).delete(feedback);
    inOrder.verify(conversationSourceRepository).deleteBySourceId(8600L);
    inOrder.verify(sourceRepository).delete(fbSrc);
    inOrder.verify(materialRepository).delete(material);
    verify(fileStorage).delete("/store/fb", "UNKNOWN");
  }

  /* ── helpers ──────────────────────────────────────────── */

  /** complete 제출 가드 통과용 — 학생 본인(userId=20) principal stub. */
  private void stubStudentPrincipal() {
    when(authResolver.currentPrincipal()).thenReturn(Optional.of(
        new JwtTokenProvider.AuthenticatedPrincipal(20L, "student", Role.STUDENT.name())));
  }

  private LearningMaterial sampleMaterial() {
    Source problemSrc = sourceOf(8000L, tutor, "hw.pdf", "UNKNOWN", "/store/prob");
    LearningMaterial m = LearningMaterial.builder()
        .student(student)
        .conversation(conversation)
        .problemSource(problemSrc)
        .assignedBy(tutor)
        .title("숙제")
        .description("내일까지")
        .build();
    ReflectionTestUtils.setField(m, "materialId", 7000L);
    return m;
  }

  /** PDF 없이 부여된 숙제 — problemSource=null. */
  private LearningMaterial sampleMaterialNoPdf() {
    LearningMaterial m = LearningMaterial.builder()
        .student(student)
        .conversation(conversation)
        .problemSource(null)
        .assignedBy(tutor)
        .title("PDF 없는 숙제")
        .description("교재 12쪽")
        .build();
    ReflectionTestUtils.setField(m, "materialId", 7000L);
    return m;
  }

  private Source sourceOf(Long id, User uploader, String name, String fileType, String storageKey) {
    Source s = Source.builder()
        .uploadedBy(uploader)
        .originalFileName(name)
        .storageKey(storageKey)
        .fileType(fileType)
        .fileSize(1L)
        .sourceType(SourceType.HOMEWORK_PROBLEM)
        .contentKind(null)
        .build();
    ReflectionTestUtils.setField(s, "sourceId", id);
    return s;
  }
}
