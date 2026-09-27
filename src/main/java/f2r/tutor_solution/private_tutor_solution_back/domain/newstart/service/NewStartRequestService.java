package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartRequestKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartRequestStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartRequestStatusLog;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartRequestRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartRequestStatusLogRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NewStart 질문요청 게시판(설계 §5) service — [질문·조사 요청] + [사이트 개선 요청] 인박스.
 *
 * <p>권한 경계는 service가 아니라 요청 경로·SecurityConfig에서 갈린다:
 * 읽기/쓰기는 {@code /api/newstart/requests}(인증 회원), 쓰기 실행은 이 service의
 * {@code principal} 검증을 통과한 THANKS_MEMBER만, 운영자 상태 관리는
 * {@code /api/admin/newstart/requests}(hasRole ADMIN)로만 들어온다.
 *
 * <p>실패는 예외로 던지지 않고 result-object로 보고한다 — controller가 사용자 안내 문구만
 * 내려보내고 내부 상세는 브라우저에 노출하지 않기 위해서다.
 */
@Service
public class NewStartRequestService {

  static final int MAX_TITLE_CHARS = 200;
  static final int MAX_BODY_CHARS = 4000;
  static final int MAX_LOCATION_CHARS = 120;
  static final int DEFAULT_PAGE_SIZE = 20;
  static final int MAX_PAGE_SIZE = 50;
  /** A to Z STEP 번호는 "13-2" 형태만 받는다(화면 표기와 동일). */
  private static final Pattern A_TO_Z_STEP = Pattern.compile("^\\d{1,2}-\\d{1,2}$");
  private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
  /** 운영자 식별자(loginId)를 회원 화면에 노출하지 않고 통일된 표기로 내린다. */
  private static final String OPERATOR_LABEL = "운영자";
  /** 계정이 정리된 요청의 작성자 대체 표기(개인정보 미노출). */
  private static final String UNKNOWN_AUTHOR = "회원";

  private final NewStartRequestRepository requestRepository;
  private final NewStartRequestStatusLogRepository statusLogRepository;
  private final UserRepository userRepository;
  private final NewStartMemberProfileService memberProfileService;

  public NewStartRequestService(NewStartRequestRepository requestRepository,
      NewStartRequestStatusLogRepository statusLogRepository,
      UserRepository userRepository,
      NewStartMemberProfileService memberProfileService) {
    this.requestRepository = requestRepository;
    this.statusLogRepository = statusLogRepository;
    this.userRepository = userRepository;
    this.memberProfileService = memberProfileService;
  }

  @Transactional
  public RequestResult create(AuthenticatedPrincipal principal, NewStartRequestKind kind, String title,
      String screenLocation, String aToZStep, String problem, String proposal) {
    if (kind == null) return RequestResult.failed("요청 종류를 선택해 주세요.");
    String cleanTitle = trim(title);
    String cleanProblem = trim(problem);
    String cleanProposal = trim(proposal);
    String cleanLocation = trim(screenLocation);
    String cleanStep = trim(aToZStep);
    if (cleanTitle.isEmpty()) return RequestResult.failed("제목을 입력해 주세요.");
    if (cleanTitle.length() > MAX_TITLE_CHARS) {
      return RequestResult.failed("제목은 " + MAX_TITLE_CHARS + "자 이내로 적어 주세요.");
    }
    if (cleanProblem.isEmpty()) return RequestResult.failed("내용을 입력해 주세요.");
    if (cleanProblem.length() > MAX_BODY_CHARS) {
      return RequestResult.failed("내용은 " + MAX_BODY_CHARS + "자 이내로 적어 주세요.");
    }
    if (cleanProposal.length() > MAX_BODY_CHARS) {
      return RequestResult.failed("제안은 " + MAX_BODY_CHARS + "자 이내로 적어 주세요.");
    }
    if (cleanLocation.length() > MAX_LOCATION_CHARS) {
      return RequestResult.failed("화면/위치는 " + MAX_LOCATION_CHARS + "자 이내로 적어 주세요.");
    }
    if (!cleanStep.isEmpty() && !A_TO_Z_STEP.matcher(cleanStep).matches()) {
      return RequestResult.failed("A to Z STEP은 숫자-숫자 형식(예: 4-4)으로 적어 주세요.");
    }

    NewStartRequest saved = requestRepository.save(NewStartRequest.builder()
        .userId(principal.userId())
        .kind(kind)
        .title(cleanTitle)
        .screenLocation(emptyToNull(cleanLocation))
        .aToZStep(emptyToNull(cleanStep))
        .problem(cleanProblem)
        .proposal(emptyToNull(cleanProposal))
        .build());
    statusLogRepository.save(NewStartRequestStatusLog.builder()
        .requestId(saved.getRequestId())
        .toStatus(NewStartRequestStatus.RECEIVED)
        .actor(principal.loginId())
        .reason("접수")
        .build());
    return RequestResult.success(toView(saved, displayNameOf(saved.getUserId())));
  }

  /** 목록: 최근 작성 순. {@code kind}가 없으면 두 인박스를 합쳐 조회한다. */
  @Transactional(readOnly = true)
  public RequestPage list(NewStartRequestKind kind, Integer page, Integer size) {
    int p = page == null || page < 0 ? 0 : page;
    int s = size == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    PageRequest pageable = PageRequest.of(p, s);
    Page<NewStartRequest> result = kind == null
        ? requestRepository.findByDeletedFalseOrderByRequestIdDesc(pageable)
        : requestRepository.findByKindAndDeletedFalseOrderByRequestIdDesc(kind, pageable);
    Map<Long, String> names = displayNames(result.getContent());
    return new RequestPage(result.getContent().stream()
        .map(request -> toView(request, names.getOrDefault(request.getUserId(), UNKNOWN_AUTHOR)))
        .toList(), result.hasNext());
  }

  /** 운영자 검토 큐: 같은 상태가 오래 방치된 순(오래된 것이 먼저). ADMIN 경로에서만 사용한다. */
  @Transactional(readOnly = true)
  public List<RequestView> queue(NewStartRequestStatus status) {
    List<NewStartRequest> found =
        requestRepository.findByStatusAndDeletedFalseOrderByRequestIdAsc(status);
    Map<Long, String> names = displayNames(found);
    return found.stream()
        .map(request -> toView(request, names.getOrDefault(request.getUserId(), UNKNOWN_AUTHOR)))
        .toList();
  }

  @Transactional(readOnly = true)
  public RequestResult detail(Long requestId) {
    NewStartRequest request = findActive(requestId);
    if (request == null) return RequestResult.failed("요청을 찾을 수 없습니다.");
    RequestView base = toView(request, displayNameOf(request.getUserId()));
    return RequestResult.success(base.withLogs(logsOf(request.getRequestId())));
  }

  /**
   * Withdraw — 본인 또는 ADMIN만. 요청 본문에 채무·기관 정보가 남을 수 있어 삭제 시 원문을 파기한다
   * (row는 검토 이력 연결을 위해 soft delete).
   */
  @Transactional
  public RequestResult withdraw(AuthenticatedPrincipal principal, Long requestId) {
    NewStartRequest request = findActive(requestId);
    if (request == null) return RequestResult.failed("요청을 찾을 수 없습니다.");
    boolean admin = "ADMIN".equals(principal.role());
    if (!admin && !principal.userId().equals(request.getUserId())) {
      return RequestResult.forbidden("본인이 작성한 요청만 취소할 수 있습니다.");
    }
    request.markDeleted();
    requestRepository.save(request);
    return RequestResult.success(null);
  }

  /**
   * 운영자 검토(ADMIN 경로로만 호출된다). REJECTED/DUPLICATE는 회원에게 설명할 사유가 필요하다.
   * 이력 row는 상태 이동마다 하나씩 남긴다.
   */
  @Transactional
  public RequestResult review(AuthenticatedPrincipal admin, Long requestId, NewStartRequestStatus status,
      String answer, String reason, String githubRef, String resolvedVersion) {
    NewStartRequest request = findActive(requestId);
    if (request == null) return RequestResult.failed("요청을 찾을 수 없습니다.");
    if (status == null) return RequestResult.failed("변경할 상태를 선택해 주세요.");
    String cleanReason = trim(reason);
    if (status.requiresReason() && cleanReason.isEmpty()) {
      return RequestResult.failed("거절·중복 처리에는 사유를 적어 주세요.");
    }
    NewStartRequestStatus from = request.getStatus();
    if (from == status) return RequestResult.failed("이미 " + status.name() + " 상태입니다.");
    request.review(status, trim(answer), admin.loginId());
    if (status == NewStartRequestStatus.RESOLVED) {
      request.markResolved(emptyToNull(trim(githubRef)), emptyToNull(trim(resolvedVersion)));
    }
    requestRepository.save(request);
    statusLogRepository.save(NewStartRequestStatusLog.builder()
        .requestId(requestId)
        .fromStatus(from)
        .toStatus(status)
        .actor(admin.loginId())
        .reason(emptyToNull(cleanReason))
        .build());
    return RequestResult.success(toView(request, displayNameOf(request.getUserId())));
  }

  private NewStartRequest findActive(Long requestId) {
    if (requestId == null) return null;
    return requestRepository.findById(requestId).filter(r -> !r.isDeleted()).orElse(null);
  }

  private Map<Long, String> displayNames(List<NewStartRequest> requests) {
    List<Long> userIds = requests.stream().map(NewStartRequest::getUserId).distinct().toList();
    return memberProfileService.resolveDisplayNicknames(userRepository.findAllById(userIds));
  }

  private String displayNameOf(Long userId) {
    User user = userRepository.findById(userId).orElse(null);
    return user == null ? UNKNOWN_AUTHOR : memberProfileService.resolveDisplayNickname(user);
  }

  /** 목록/생성 응답은 이력을 싣지 않는다( row마다 추가 조회를 하지 않기 위해). 상세에서만 붙인다. */
  private RequestView toView(NewStartRequest request, String authorName) {
    return new RequestView(request.getRequestId(), request.getKind().name(), request.getTitle(),
        request.getScreenLocation(), request.getAToZStep(), request.getProblem(), request.getProposal(),
        request.getStatus().name(), request.getAnswer(), request.getAnsweredBy(),
        iso(request.getResolvedAt()), request.getGithubRef(), request.getResolvedVersion(),
        request.getUserId(), authorName, iso(request.getCreatedAt()), List.of());
  }

  private List<StatusLogView> logsOf(Long requestId) {
    return statusLogRepository.findByRequestIdOrderByLogIdAsc(requestId).stream()
        .map(log -> new StatusLogView(log.getFromStatus() == null ? null : log.getFromStatus().name(),
            log.getToStatus().name(), OPERATOR_LABEL, iso(log.getCreatedAt()), log.getReason()))
        .toList();
  }

  private static String trim(String value) {
    return value == null ? "" : value.strip();
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }

  private static String iso(LocalDateTime time) {
    return time == null ? null : time.format(ISO);
  }

  /**
   * @param authorId 작성자 userId — 채팅의 senderUserId와 동일한 공개 범위(서버가 결정, 취소 버튼
   *                 표시 판정용). 권한 판정의 근거는 항상 서버다.
   * @param author 서버가 resolve한 공개 표기 이름(실명·이메일·전화 아님).
   * @param statusLogs 상태 이동 이력(actor는 "운영자"로 통일 노출). 목록 조회에서는 빈 배열.
   */
  public record RequestView(Long id, String kind, String title, String screenLocation, String aToZStep,
      String problem, String proposal, String status, String answer, String answeredBy,
      String resolvedAt, String githubRef, String resolvedVersion, Long authorId, String author,
      String createdAt, List<StatusLogView> statusLogs) {

    /** 상세 응답에서만 이력을 붙인다(목록은 빈 배열). */
    public RequestView withLogs(List<StatusLogView> logs) {
      return new RequestView(id, kind, title, screenLocation, aToZStep, problem, proposal, status,
          answer, answeredBy, resolvedAt, githubRef, resolvedVersion, authorId, author, createdAt, logs);
    }
  }

  public record StatusLogView(String fromStatus, String toStatus, String actor, String createdAt,
      String reason) {}

  public record RequestPage(List<RequestView> requests, boolean hasMore) {}

  /** 성공 시 {@code view}, 실패 시 {@code errorMessage}. {@code forbidden}이면 controller가 403으로 낸다. */
  public record RequestResult(boolean ok, String errorMessage, boolean forbidden, RequestView view) {
    public static RequestResult success(RequestView view) {
      return new RequestResult(true, null, false, view);
    }

    public static RequestResult failed(String errorMessage) {
      return new RequestResult(false, errorMessage, false, null);
    }

    public static RequestResult forbidden(String errorMessage) {
      return new RequestResult(false, errorMessage, true, null);
    }
  }
}
