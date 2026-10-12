package f2r.tutor_solution.private_tutor_solution_back.config.security;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationSourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.learningmaterial.entity.LearningMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.repository.LessonApplicationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * JWT principal에서 자기 userId/studentId를 끌어내는 헬퍼.
 *
 * <p>ADMIN role은 명시적인 {@code targetStudentId}/{@code targetUserId}를 query로 넘기면
 * 임퍼소네이트(다른 학생 자료 조회)를 허용한다. 학생(STUDENT)은 자기 ID로 강제 고정 — query에
 * 다른 ID를 넣어도 403 (IDOR 차단).
 *
 * <p>TUTOR는 {@code lesson_applications}에 APPROVED 신청이 있는 학생 자원만 통과시킨다 (student 단위).
 * 단, Academy 관리는 TUTOR 본인 소유 Academy만 통과한다.
 *
 * <p>1차 작업 단계 트레이드오프: 컨트롤러마다 일일이 분기 로직을 두지 않고 본 헬퍼에 캡슐화한다.
 * ADMIN 임퍼소네이트 흐름은 추후 학생 검색 + 전용 admin 엔드포인트로 분리할 예정.
 */
@Component
@RequiredArgsConstructor
public class AuthenticatedUserResolver {

  private final StudentRepository studentRepository;
  private final LessonApplicationRepository lessonApplicationRepository;
  private final ConversationSourceRepository conversationSourceRepository;
  private final ConversationRepository conversationRepository;

  /**
   * "내가 보려는 학생의 student_id를 결정한다."
   *
   * @param principal      JWT에서 꺼낸 인증 주체.
   * @param targetOverride ADMIN/TUTOR가 임퍼소네이트할 학생 ID. 학생 role이면 null이 아닌 경우 본인 ID와
   *                       일치할 때만 통과 (다른 ID면 403). null이면 무조건 자기 ID로.
   *                       TUTOR는 target이 매핑된 학생일 때만 통과.
   */
  public Long resolveStudentId(JwtTokenProvider.AuthenticatedPrincipal principal, Long targetOverride) {
    if (isAdmin(principal)) {
      return targetOverride != null
          ? targetOverride
          : selfStudentId(principal);
    }
    if (isTutor(principal)) {
      if (targetOverride == null) {
        throw new AccessDeniedException("강사는 학생을 지정해야 합니다 (targetStudentId).");
      }
      ensureTutorTeachesStudent(principal.userId(), targetOverride);
      return targetOverride;
    }
    Long selfId = selfStudentId(principal);
    if (targetOverride != null && !targetOverride.equals(selfId)) {
      throw new AccessDeniedException("다른 학생의 자료에 접근할 수 없습니다.");
    }
    return selfId;
  }

  /**
   * "내가 보려는 사용자의 user_id를 결정한다." (sources.uploaded_by, audit 등에서 사용)
   *
   * <ul>
   *   <li>ADMIN → target을 주면 그 user_id로 임퍼소네이트, 없으면 본인.</li>
   *   <li>TUTOR → target이 없거나 본인이면 본인. 다른 user_id를 주면 그 user_id가 담당 학생
   *       (APPROVED 신청 존재)의 user인지 검증하고 통과 — 아니면 403. (IDOR 차단)</li>
   *   <li>STUDENT → 본인 user_id만. 다른 ID면 403.</li>
   * </ul>
   *
   * <p>이전에는 TUTOR가 임의 targetUserId를 검증 없이 통과시켜, 담당하지 않는 학생의 자원
   * (검증 대기 목록·시험 확정 등)에 접근 가능한 IDOR가 있었다. 본 메서드에서 직접 막는다.
   */
  public Long resolveUserId(JwtTokenProvider.AuthenticatedPrincipal principal, Long targetOverride) {
    if (isAdmin(principal)) {
      return targetOverride != null ? targetOverride : principal.userId();
    }
    if (isTutor(principal)) {
      if (targetOverride == null || targetOverride.equals(principal.userId())) {
        return principal.userId();
      }
      ensureTutorTeachesUser(principal.userId(), targetOverride);
      return targetOverride;
    }
    if (targetOverride != null && !targetOverride.equals(principal.userId())) {
      throw new AccessDeniedException("다른 사용자의 자원에 접근할 수 없습니다.");
    }
    return principal.userId();
  }

  /** 학생 본인 진입 시 students 테이블에서 student_id 조회. */
  private Long selfStudentId(JwtTokenProvider.AuthenticatedPrincipal principal) {
    Student student = studentRepository.findByUser_UserId(principal.userId())
        .orElseThrow(() -> new AccessDeniedException(
            "학생 프로필이 없는 사용자입니다: userId=" + principal.userId()));
    return student.getStudentId();
  }

  private boolean isAdmin(JwtTokenProvider.AuthenticatedPrincipal principal) {
    return principal != null && Role.ADMIN.name().equals(principal.role());
  }

  private boolean isTutor(JwtTokenProvider.AuthenticatedPrincipal principal) {
    return principal != null && Role.TUTOR.name().equals(principal.role());
  }

  public void requireTutor(JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (!isTutor(principal)) {
      throw new AccessDeniedException("강사 권한이 필요합니다.");
    }
  }

  /**
   * TUTOR 본인 또는 ADMIN principal만 통과. 소유권까지 확인하지는 않는다 — "내 학원" 조회처럼
   * ADMIN이 소유 학원 없이 진입해도 빈 결과로 떨어지면 되는 read 경로에서만 쓴다. 쓰기 경로는
   * 계속 {@link #requireTutor} + {@link #verifyOwnsAcademy}를 쓴다.
   */
  public void requireTutorOrAdmin(JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (!isTutor(principal) && !isAdmin(principal)) {
      throw new AccessDeniedException("강사 또는 관리자 권한이 필요합니다.");
    }
  }

  /**
   * 홈페이지 빌더 계열(생성·수정·AI 채팅·작업본·게시) 전용 역할 검사: TUTOR 또는 ADMIN.
   *
   * <p>수업상품·블로그·LMS·영상·삭제 등 기존 TUTOR 전용 기능은 계속 {@link #requireTutor}를 쓴다.
   * 이 메서드는 역할만 본다 — 소유권은 {@link #verifyOwnsAcademyForBuilder}가 따로 확인한다.
   */
  public void requireAcademyBuilderRole(JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (!isTutor(principal) && !isAdmin(principal)) {
      throw new AccessDeniedException("강사 또는 관리자 권한이 필요합니다.");
    }
  }

  /** TUTOR가 넘긴 target user_id가 담당 학생(APPROVED 신청 존재)의 user인지 검증. 없으면 403. */
  private void ensureTutorTeachesUser(Long tutorUserId, Long targetUserId) {
    Student student = studentRepository.findByUser_UserId(targetUserId)
        .orElseThrow(() -> new AccessDeniedException(
            "담당 학생이 아닙니다: userId=" + targetUserId));
    ensureTutorTeachesStudent(tutorUserId, student.getStudentId());
  }

  /** TUTOR가 담당하는 학생(APPROVED 신청 존재)인지 검증. 없으면 403. */
  private void ensureTutorTeachesStudent(Long tutorUserId, Long studentId) {
    boolean teaches = lessonApplicationRepository
        .existsApprovedTutorStudent(tutorUserId, studentId);
    if (!teaches) {
      throw new AccessDeniedException("담당 학생이 아닙니다: studentId=" + studentId);
    }
  }

  /* ── service-layer ownership 가드 ─────────────────────────────────
   * 컨트롤러의 IDOR 차단(query param 가드)이 닫지 못하는 path-id 기반 호출을 막는다.
   * 예: GET /api/conversations/{id} — 학생A가 학생B의 conversationId를 알면 컨트롤러는 그대로 통과.
   *     service 진입부에서 본 헬퍼를 호출해 owner 검증을 수행한다.
   *
   * ADMIN role은 임퍼소네이트 흐름을 위해 항상 통과시킨다.
   * TUTOR role은 lesson_applications에 APPROVED 신청이 있는 학생일 때만 통과 (student 단위).
   * 학생은 자기 user_id와 owner user_id가 일치할 때만 통과 — 아니면 AccessDeniedException(403).
   * ────────────────────────────────────────────────────────────────── */

  /** SecurityContext에 박힌 principal을 안전하게 꺼낸다. 없으면 empty. */
  public Optional<JwtTokenProvider.AuthenticatedPrincipal> currentPrincipal() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) return Optional.empty();
    Object principal = auth.getPrincipal();
    if (principal instanceof JwtTokenProvider.AuthenticatedPrincipal p) {
      return Optional.of(p);
    }
    return Optional.empty();
  }

  /**
   * conversation 소유자 학생 ↔ 현재 principal 일치 검증.
   * <ul>
   *   <li>ADMIN → 항상 통과.</li>
   *   <li>TUTOR → conversation.student에 APPROVED 신청 있으면 통과 (student 단위).</li>
   *   <li>STUDENT → 본인 conversation만 통과.</li>
   * </ul>
   */
  public void verifyOwnsConversation(Conversation conversation) {
    requireStudentOwnedConversationAccess(conversation);
  }

  /** Student-shaped legacy API/page access: tutor-owned conversations fail closed before getStudent() use. */
  public void requireStudentOwnedConversationAccess(Conversation conversation) {
    if (conversation.getStudent() == null || conversation.getTutorUser() != null) {
      throw new AccessDeniedException("학생 소유 대화만 접근할 수 있습니다.");
    }
    JwtTokenProvider.AuthenticatedPrincipal p = currentPrincipal()
        .orElseThrow(() -> new AccessDeniedException("인증이 필요합니다."));
    if (isAdmin(p)) return;
    if (isTutor(p)) {
      // TUTOR는 본인 academy lesson에 APPROVED로 매칭된 student-owned conversation만 읽을 수 있다.
      ensureTutorOwnsConversation(p.userId(), conversation.getConversationId());
      return;
    }
    Long ownerUserId = conversation.getStudent().getUser().getUserId();
    if (!ownerUserId.equals(p.userId())) {
      throw new AccessDeniedException("자기 대화에만 접근할 수 있습니다.");
    }
  }

  /** Student-owned conversation mutation: TUTOR is intentionally denied. */
  public void requireStudentOwnedConversationManage(Conversation conversation) {
    if (conversation.getStudent() == null || conversation.getTutorUser() != null) {
      throw new AccessDeniedException("학생 소유 대화만 수정할 수 있습니다.");
    }
    JwtTokenProvider.AuthenticatedPrincipal p = currentPrincipal()
        .orElseThrow(() -> new AccessDeniedException("인증이 필요합니다."));
    if (isAdmin(p)) return;
    if (isTutor(p)) {
      throw new AccessDeniedException("강사는 학생 학습기록을 수정하거나 삭제할 수 없습니다.");
    }
    Long ownerUserId = conversation.getStudent().getUser().getUserId();
    if (!ownerUserId.equals(p.userId())) {
      throw new AccessDeniedException("자기 대화만 수정할 수 있습니다.");
    }
  }

  /** Tutor-owned canonical API access/manage: only the owning TUTOR can use it; ADMIN is out of scope. */
  public void requireTutorOwnedConversationManage(Conversation conversation) {
    if (conversation.getTutorUser() == null || conversation.getStudent() != null) {
      throw new AccessDeniedException("강사 소유 기록만 접근할 수 있습니다.");
    }
    JwtTokenProvider.AuthenticatedPrincipal p = currentPrincipal()
        .orElseThrow(() -> new AccessDeniedException("인증이 필요합니다."));
    if (!isTutor(p)) {
      throw new AccessDeniedException("강사 권한이 필요합니다.");
    }
    Long ownerUserId = conversation.getTutorUser().getUserId();
    if (!ownerUserId.equals(p.userId())) {
      throw new AccessDeniedException("자기 강사 기록만 접근할 수 있습니다.");
    }
  }

  public void requireTutorOwnedConversationAccess(Conversation conversation) {
    requireTutorOwnedConversationManage(conversation);
  }

  /** TUTOR가 넘긴 conversation이 본인 academy lesson에 APPROVED로 매칭됐는지 검증. 아니면 403. */
  private void ensureTutorOwnsConversation(Long tutorUserId, Long conversationId) {
    boolean owns = lessonApplicationRepository
        .existsApprovedTutorConversation(tutorUserId, conversationId);
    if (!owns) {
      throw new AccessDeniedException("담당 수업의 대화가 아닙니다: conversationId=" + conversationId);
    }
  }

  /**
   * source 업로더(user_id) ↔ 현재 principal 일치 검증. ADMIN은 항상 통과.
   *
   * <p>conversation-scoped 통과: 업로더 본인이 아니어도, source가 현재 principal이 접근 가능한
   * conversation에 첨부돼 있으면 통과한다. 강사가 올린 시험대비 자료(uploadedBy=강사,
   * source_type=TUTOR_MATERIAL)를 그 대화의 학생이 열람할 수 있게 하기 위함. conversation_sources는
   * 서버측 업로드 흐름에서만 기록되므로 학생이 임의 source를 끼워 넣어 우회할 수 없다.
   */
  public void verifyOwnsSource(Source source) {
    JwtTokenProvider.AuthenticatedPrincipal p = currentPrincipal()
        .orElseThrow(() -> new AccessDeniedException("인증이 필요합니다."));
    if (isAdmin(p)) return;
    Long ownerUserId = source.getUploadedBy().getUserId();
    if (ownerUserId.equals(p.userId())) return;  // 업로더 본인
    if (sourceInAccessibleConversation(source.getSourceId(), p)) return;  // 본인 대화에 첨부된 자료
    throw new AccessDeniedException("자기 자료에만 접근할 수 있습니다.");
  }

  /** source가 현재 principal이 접근 가능한 conversation에 1개라도 첨부돼 있는지. (STUDENT=본인 대화 / TUTOR=APPROVED 매칭 대화) */
  private boolean sourceInAccessibleConversation(
      Long sourceId, JwtTokenProvider.AuthenticatedPrincipal p) {
    for (var cs : conversationSourceRepository.findBySourceId(sourceId)) {
      Conversation conv = conversationRepository.findById(cs.getConversationId()).orElse(null);
      if (conv == null) continue;
      if (conv.getStudent() == null || conv.getTutorUser() != null) continue;
      if (isTutor(p)) {
        if (lessonApplicationRepository.existsApprovedTutorConversation(
            p.userId(), conv.getConversationId())) return true;
      } else {
        if (conv.getStudent().getUser().getUserId().equals(p.userId())) return true;
      }
    }
    return false;
  }

  /**
   * academy 소유자(tutor_user_id) ↔ 현재 principal 일치 검증. ADMIN 우회는 Tutor MVP에서 허용하지 않는다.
   *
   * <p>이 메서드는 academy 변경 경로 전체(수정/삭제/페이지설정/레이아웃/수업상품/블로그)가 거치는
   * 단일 지점이다.
   */
  public void verifyOwnsAcademy(Academy academy) {
    JwtTokenProvider.AuthenticatedPrincipal p = currentPrincipal()
        .orElseThrow(() -> new AccessDeniedException("인증이 필요합니다."));
    requireTutor(p);
    Long ownerUserId = academy.getTutor().getUserId();
    if (!ownerUserId.equals(p.userId())) {
      throw new AccessDeniedException("자기 Academy만 수정할 수 있습니다.");
    }
  }

  /**
   * 빌더 계열 경로의 소유자 검증: TUTOR 또는 ADMIN이면서 academy 소유자 본인이어야 한다.
   * ADMIN도 다른 회원의 학원은 통과하지 못한다(소유권 보호 유지).
   */
  public void verifyOwnsAcademyForBuilder(Academy academy) {
    JwtTokenProvider.AuthenticatedPrincipal p = currentPrincipal()
        .orElseThrow(() -> new AccessDeniedException("인증이 필요합니다."));
    requireAcademyBuilderRole(p);
    Long ownerUserId = academy.getTutor().getUserId();
    if (!ownerUserId.equals(p.userId())) {
      throw new AccessDeniedException("자기 Academy만 수정할 수 있습니다.");
    }
  }

  /**
   * 학습자료(LearningMaterial) ↔ 현재 principal 일치 검증.
   * <ul>
   *   <li>ADMIN → 항상 통과.</li>
   *   <li>TUTOR → material.student에 APPROVED 신청 있으면 통과 (student 단위, assigned_by 본인 여부 무관).</li>
   *   <li>STUDENT → material.student가 본인이면 통과.</li>
   * </ul>
   */
  public void verifyOwnsLearningMaterial(LearningMaterial material) {
    JwtTokenProvider.AuthenticatedPrincipal p = currentPrincipal()
        .orElseThrow(() -> new AccessDeniedException("인증이 필요합니다."));
    if (isAdmin(p)) return;
    Long studentId = material.getStudent().getStudentId();
    if (isTutor(p)) {
      ensureTutorTeachesStudent(p.userId(), studentId);
      return;
    }
    Long ownerUserId = material.getStudent().getUser().getUserId();
    if (!ownerUserId.equals(p.userId())) {
      throw new AccessDeniedException("자기 학습자료에만 접근할 수 있습니다.");
    }
  }

  /**
   * "이 작업은 ADMIN 또는 TUTOR가 공유한다" — role-only 가드.
   * 호출 측에서 분기하지 않도록 OR을 한 메서드로 묶었다 (학습자료 부여/삭제 등).
   * 학생 단위 매핑은 별도 (verifyOwnsConversation 또는 verifyOwnsLearningMaterial)에서 확인.
   */
  public void verifyAdminOrTutor() {
    JwtTokenProvider.AuthenticatedPrincipal p = currentPrincipal()
        .orElseThrow(() -> new AccessDeniedException("인증이 필요합니다."));
    if (!isAdmin(p) && !isTutor(p)) {
      throw new AccessDeniedException("관리자 또는 강사 권한이 필요합니다.");
    }
  }
}
