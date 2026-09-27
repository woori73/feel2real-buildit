package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartRequestKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartRequestStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartRequestStatusLog;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartRequestRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartRequestStatusLogRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 질문요청 게시판(설계 §5) service 계약 — 검증·권한·이력 기록의 단일 지점.
 *
 * <p>여기 보는 것: (1) 쓰기 검증(A to Z STEP 형식, 길이), (2) 개선 요청 전용 칼럼은
 * SITE_IMPROVEMENT에서만 보존, (3) 접수 이력 1건 자동 기록, (4) 취소는 본인/ADMIN만이고
 * 원문은 파기(soft delete), (5) 거절·중복은 사유 필수, (6) 이력은 상세에서만 조회(N+1 방지),
 * (7) 계정 정리 시 작성자 표기는 "회원"으로 대체(개인정보 추적 방지).
 */
class NewStartRequestServiceTest {

  private NewStartRequestRepository requestRepository;
  private NewStartRequestStatusLogRepository statusLogRepository;
  private UserRepository userRepository;
  private NewStartMemberProfileService memberProfileService;
  private NewStartRequestService service;

  private static final AuthenticatedPrincipal MEMBER =
      new AuthenticatedPrincipal(1L, "member1", "STUDENT");
  private static final AuthenticatedPrincipal OTHER =
      new AuthenticatedPrincipal(2L, "member2", "STUDENT");
  private static final AuthenticatedPrincipal ADMIN =
      new AuthenticatedPrincipal(9L, "admin", "ADMIN");

  @BeforeEach
  void setUp() {
    requestRepository = mock(NewStartRequestRepository.class);
    statusLogRepository = mock(NewStartRequestStatusLogRepository.class);
    userRepository = mock(UserRepository.class);
    memberProfileService = mock(NewStartMemberProfileService.class);
    service = new NewStartRequestService(
        requestRepository, statusLogRepository, userRepository, memberProfileService);

    when(requestRepository.save(any(NewStartRequest.class))).thenAnswer(invocation -> {
      NewStartRequest request = invocation.getArgument(0);
      if (request.getRequestId() == null) {
        ReflectionTestUtils.setField(request, "requestId", 100L);
      }
      return request;
    });
    when(userRepository.findById(anyLong())).thenReturn(Optional.of(user(1L)));
    when(memberProfileService.resolveDisplayNickname(any())).thenReturn("공용닉네임");
    when(memberProfileService.resolveDisplayNicknames(any()))
        .thenReturn(Map.of(1L, "공용닉네임"));
  }

  private static User user(long userId) {
    User u = User.builder().loginId("member" + userId).password("x").name("실명" + userId)
        .nickname("닉" + userId).role(Role.STUDENT).build();
    ReflectionTestUtils.setField(u, "userId", userId);
    return u;
  }

  private static NewStartRequest stored(NewStartRequestKind kind, NewStartRequestStatus status,
      Long ownerUserId) {
    NewStartRequest request = NewStartRequest.builder().userId(ownerUserId).kind(kind)
        .title("추심 연락 요청").problem("매일 전화가 옵니다").build();
    ReflectionTestUtils.setField(request, "requestId", 55L);
    ReflectionTestUtils.setField(request, "status", status);
    return request;
  }

  @Test
  @DisplayName("given: 정상 질문 요청 / when: create / then: RECEIVED 접수 + 이력 1건, 본문은 trim 저장")
  void createPersistsRequestAndReceiptLog() {
    var result = service.create(MEMBER, NewStartRequestKind.QUESTION,
        "  신용회복위원회 절차가 궁금해요  ", "  버림받을 화면 ", "  99-99  ", "  채무 조정 순서를 모릅니다 ", null);

    assertThat(result.ok()).isTrue();
    assertThat(result.view().title()).isEqualTo("신용회복위원회 절차가 궁금해요");
    assertThat(result.view().problem()).isEqualTo("채무 조정 순서를 모릅니다");
    assertThat(result.view().status()).isEqualTo("RECEIVED");
    // QUESTION은 개선 요청 전용 칼럼(화면/위치)을 보존하지 않는다.
    assertThat(result.view().screenLocation()).isNull();
    assertThat(result.view().aToZStep()).isNull();

    ArgumentCaptor<NewStartRequestStatusLog> log = ArgumentCaptor.forClass(NewStartRequestStatusLog.class);
    verify(statusLogRepository).save(log.capture());
    assertThat(log.getValue().getToStatus()).isEqualTo(NewStartRequestStatus.RECEIVED);
    assertThat(log.getValue().getFromStatus()).isNull();
    assertThat(log.getValue().getActor()).isEqualTo("member1");
  }

  @Test
  @DisplayName("given: SITE_IMPROVEMENT / when: create / then: 화면·위치와 STEP 번호가 함께 저장된다")
  void improvementRequestKeepsItsOwnFields() {
    var result = service.create(MEMBER, NewStartRequestKind.SITE_IMPROVEMENT,
        "12-3 링크가 잘못됐어요", "HOME > 진행상태 카드", "12-3", "오류 페이지로 갑니다", "공식 경로로 교체");

    assertThat(result.ok()).isTrue();
    assertThat(result.view().kind()).isEqualTo("SITE_IMPROVEMENT");
    assertThat(result.view().screenLocation()).isEqualTo("HOME > 진행상태 카드");
    assertThat(result.view().aToZStep()).isEqualTo("12-3");
    assertThat(result.view().proposal()).isEqualTo("공식 경로로 교체");
  }

  @Test
  @DisplayName("given: STEP 형식 오류(4_4) / when: create / then: 저장 없이 안내 문구만")
  void rejectsMalformedAtoZStep() {
    var result = service.create(MEMBER, NewStartRequestKind.SITE_IMPROVEMENT,
        "제목", "위치", "4_4", "내용", null);

    assertThat(result.ok()).isFalse();
    assertThat(result.errorMessage()).contains("A to Z STEP");
    verify(requestRepository, never()).save(any());
    verify(statusLogRepository, never()).save(any());
  }

  @Test
  @DisplayName("given: 본문 길이 초과 / when: create / then: 4000자 상한 안내")
  void rejectsOverlongProblem() {
    var result = service.create(MEMBER, NewStartRequestKind.QUESTION,
        "제목", null, null, "가".repeat(4001), null);

    assertThat(result.ok()).isFalse();
    assertThat(result.errorMessage()).contains("4000");
    verify(requestRepository, never()).save(any());
  }

  @Test
  @DisplayName("given: 타인의 요청 / when: withdraw / then: forbidden(403 근거) + 저장 없음")
  void onlyOwnerOrAdminCanWithdraw() {
    when(requestRepository.findById(55L))
        .thenReturn(Optional.of(stored(NewStartRequestKind.QUESTION, NewStartRequestStatus.RECEIVED, 1L)));

    var result = service.withdraw(OTHER, 55L);

    assertThat(result.ok()).isFalse();
    assertThat(result.forbidden()).isTrue();
    assertThat(result.errorMessage()).contains("본인이 작성한");
    verify(requestRepository, never()).save(any());
  }

  @Test
  @DisplayName("given: 본인 요청 / when: withdraw / then: soft delete + 원문 파기(제목·본문·제안)")
  void withdrawBlanksTheStoredContent() {
    NewStartRequest request = stored(NewStartRequestKind.SITE_IMPROVEMENT,
        NewStartRequestStatus.RECEIVED, 1L);
    ReflectionTestUtils.setField(request, "proposal", "개선 제안 원문");
    when(requestRepository.findById(55L)).thenReturn(Optional.of(request));

    assertThat(service.withdraw(MEMBER, 55L).ok()).isTrue();
    assertThat(request.isDeleted()).isTrue();
    assertThat(request.getTitle()).isEmpty();
    assertThat(request.getProblem()).isEmpty();
    assertThat(request.getProposal()).isNull();
  }

  @Test
  @DisplayName("given: ADMIN / when: 타인 요청 withdraw / then: 허용(운영 정리 권한)")
  void adminCanWithdrawOthersRequest() {
    when(requestRepository.findById(55L))
        .thenReturn(Optional.of(stored(NewStartRequestKind.QUESTION, NewStartRequestStatus.RECEIVED, 1L)));

    assertThat(service.withdraw(ADMIN, 55L).ok()).isTrue();
    verify(requestRepository).save(any());
  }

  @Test
  @DisplayName("given: 이미 같은 상태 / when: review / then: 중복 이적을 막고 저장·이력 없음")
  void reviewSameStatusIsRejected() {
    when(requestRepository.findById(55L)).thenReturn(Optional.of(
        stored(NewStartRequestKind.QUESTION, NewStartRequestStatus.REVIEWING, 1L)));

    var result = service.review(ADMIN, 55L, NewStartRequestStatus.REVIEWING, "답변", null, null, null);

    assertThat(result.ok()).isFalse();
    assertThat(result.errorMessage()).contains("REVIEWING");
    verify(statusLogRepository, never()).save(any());
  }

  @Test
  @DisplayName("given: REJECTED 사유 없음 / when: review / then: 사유를 요구(회원 설명 필요)")
  void rejectionNeedsAReason() {
    when(requestRepository.findById(55L)).thenReturn(Optional.of(
        stored(NewStartRequestKind.QUESTION, NewStartRequestStatus.RECEIVED, 1L)));

    var result = service.review(ADMIN, 55L, NewStartRequestStatus.REJECTED, "답변", "  ", null, null);

    assertThat(result.ok()).isFalse();
    assertThat(result.errorMessage()).contains("사유");
    verify(requestRepository, never()).save(any());
  }

  @Test
  @DisplayName("given: RECEIVED 요청 / when: RESOLVED + githubRef review / then: 반영 정보·해결 시각·이력(from/to) 기록")
  void resolvedReviewRecordsGithubRefAndLog() {
    when(requestRepository.findById(55L)).thenReturn(Optional.of(
        stored(NewStartRequestKind.SITE_IMPROVEMENT, NewStartRequestStatus.RECEIVED, 1L)));

    var result = service.review(ADMIN, 55L, NewStartRequestStatus.RESOLVED,
        "12-3 공식 경로로 교체했습니다", "링크 오류 확인", "  https://github.com/woori73/feel2real-buildit/pull/999  ",
        "v2026.10");

    assertThat(result.ok()).isTrue();
    assertThat(result.view().status()).isEqualTo("RESOLVED");
    assertThat(result.view().githubRef()).isEqualTo("https://github.com/woori73/feel2real-buildit/pull/999");
    assertThat(result.view().resolvedVersion()).isEqualTo("v2026.10");
    assertThat(result.view().resolvedAt()).isNotBlank();

    ArgumentCaptor<NewStartRequestStatusLog> log = ArgumentCaptor.forClass(NewStartRequestStatusLog.class);
    verify(statusLogRepository).save(log.capture());
    assertThat(log.getValue().getFromStatus()).isEqualTo(NewStartRequestStatus.RECEIVED);
    assertThat(log.getValue().getToStatus()).isEqualTo(NewStartRequestStatus.RESOLVED);
    assertThat(log.getValue().getActor()).isEqualTo("admin");
  }

  @Test
  @DisplayName("given: 상세 조회 / when: detail / then: 상태 이력이 붙고 actor는 '운영자'로 통일 노출")
  void detailAttachesStatusLogs() {
    when(requestRepository.findById(55L)).thenReturn(Optional.of(
        stored(NewStartRequestKind.QUESTION, NewStartRequestStatus.ANSWERED, 1L)));
    NewStartRequestStatusLog log = NewStartRequestStatusLog.builder()
        .requestId(55L).fromStatus(NewStartRequestStatus.RECEIVED)
        .toStatus(NewStartRequestStatus.ANSWERED).actor("admin").reason(null).build();
    ReflectionTestUtils.setField(log, "createdAt", java.time.LocalDateTime.of(2026, 9, 27, 10, 30));
    when(statusLogRepository.findByRequestIdOrderByLogIdAsc(55L)).thenReturn(List.of(log));

    var result = service.detail(55L);

    assertThat(result.ok()).isTrue();
    assertThat(result.view().statusLogs()).hasSize(1);
    assertThat(result.view().statusLogs().get(0).fromStatus()).isEqualTo("RECEIVED");
    assertThat(result.view().statusLogs().get(0).actor()).isEqualTo("운영자");
    assertThat(result.view().statusLogs().get(0).createdAt()).isEqualTo("2026-09-27T10:30:00");
  }

  @Test
  @DisplayName("given: 목록 조회 / when: list / then: 이력 추가 조회 없음(N+1 방지)")
  void listDoesNotFetchStatusLogs() {
    when(requestRepository.findByDeletedFalseOrderByRequestIdDesc(any()))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(
            List.of(stored(NewStartRequestKind.QUESTION, NewStartRequestStatus.RECEIVED, 1L)),
            org.springframework.data.domain.PageRequest.of(0, 20), 1));

    var page = service.list(null, null, null);

    assertThat(page.requests()).hasSize(1);
    assertThat(page.requests().get(0).statusLogs()).isEmpty();
    verify(statusLogRepository, never()).findByRequestIdOrderByLogIdAsc(any());
  }

  @Test
  @DisplayName("given: 계정이 정리된 작성자 / when: 상세 조회 / then: 실명 노출 없이 '회원' 대체")
  void deletedAccountAuthorFallsBackToGenericLabel() {
    when(requestRepository.findById(55L)).thenReturn(Optional.of(
        stored(NewStartRequestKind.QUESTION, NewStartRequestStatus.RECEIVED, 3L)));
    when(userRepository.findById(3L)).thenReturn(Optional.empty());

    var result = service.detail(55L);

    assertThat(result.ok()).isTrue();
    assertThat(result.view().author()).isEqualTo("회원");
  }

  @Test
  @DisplayName("given: 삭제된 요청 / when: 상세·withdraw / then: 존재하지 않는 것처럼 처리(404 안내)")
  void deletedRequestIsNotFound() {
    NewStartRequest request = stored(NewStartRequestKind.QUESTION, NewStartRequestStatus.RECEIVED, 1L);
    request.markDeleted();
    when(requestRepository.findById(55L)).thenReturn(Optional.of(request));

    assertThat(service.detail(55L).errorMessage()).contains("찾을 수 없습니다");
    assertThat(service.withdraw(MEMBER, 55L).errorMessage()).contains("찾을 수 없습니다");
  }
}
