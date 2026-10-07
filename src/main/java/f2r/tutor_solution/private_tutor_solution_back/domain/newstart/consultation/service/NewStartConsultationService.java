package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto.AdminConsultationSummaryView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto.AdminConsultationThreadView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto.ConsultationMessageView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto.ConsultationSummaryView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto.ConsultationThreadView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.ConsultationStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.MessageAuthorRole;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.NewStartConsultation;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.entity.NewStartConsultationMessage;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.repository.NewStartConsultationMessageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.repository.NewStartConsultationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service.JipijigiSanitizer;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartAiChatService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartAiChatService.AnswerSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartAiChatService.NewStartAiAnswer;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartAtozCaseService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartAtozCaseService.AtozCase;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartEntitlementService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartAtozStep;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartAtozStep.Step;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartMemberDebtStatusService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartMemberDebtStatusService.DebtStatusView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartMemberProfileService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * 감사회원 1:1 비공개 상담 — 회원 본인 + ADMIN만 접근하는 상담 스레드(설계 §1~§8).
 *
 * <p><b>기존 쪽지와는 별개</b>: {@code newstart_direct_messages}는 단발·단일복사본·회원 상호 DM이라
 * 상담 스레드로 재사용하지 않는다. 대신 회원 식별·별명·권한·채무·지피지기·A to Z 단계 등 기존 서비스만
 * 재사용하고, 스레드·상태는 새 테이블에 담는다(§9 기존 데이터 보존).
 *
 * <p><b>HARD GATE(§8)</b> — 화면 숨김이 아니라 조회 자체에서 강제한다. 회원 경로는
 * {@link #ownConsultation}이 {@code findById}(PK) 조회 뒤 소유자(user id)를 비교해 본인 상담만 읽고,
 * 다른 회원 상담은 404/403로 막는다. 운영자 경로는 {@code /api/admin/**}(ROLE_ADMIN) + 본 서비스의
 * {@link #requireAdmin} 이중 확인. AI_RESEARCH 메시지는 회원 조회에서 원천 제외한다(§5).
 *
 * <p>로그에 상담 본문·채무 원본을 남기지 않는다(§8). 본문은 오직 DB와 권한 있는 응답에만 실린다.
 */
@Service
public class NewStartConsultationService {

  /** 상담 메시지 본문 상한(쪽지 1000보다 길게 — 질문·경험 서술용). */
  static final int MAX_MESSAGE_CHARS = 2000;
  /** 목록 미리보기(처음 질문) 자름 길이. */
  static final int PREVIEW_CHARS = 80;

  private static final List<MessageAuthorRole> MEMBER_VISIBLE =
      List.of(MessageAuthorRole.MEMBER, MessageAuthorRole.OPERATOR);

  private final NewStartConsultationRepository consultationRepository;
  private final NewStartConsultationMessageRepository messageRepository;
  private final NewStartMemberProfileService memberProfileService;
  private final UserRepository userRepository;
  private final NewStartMemberDebtStatusService debtStatusService;
  private final NewStartAtozCaseService atozCaseService;
  private final NewStartAiChatService aiChatService;
  private final NewStartConsultationNotifier notifier;
  private final NewStartEntitlementService entitlementService;

  public NewStartConsultationService(
      NewStartConsultationRepository consultationRepository,
      NewStartConsultationMessageRepository messageRepository,
      NewStartMemberProfileService memberProfileService,
      UserRepository userRepository,
      NewStartMemberDebtStatusService debtStatusService,
      NewStartAtozCaseService atozCaseService,
      NewStartAiChatService aiChatService,
      NewStartConsultationNotifier notifier,
      NewStartEntitlementService entitlementService) {
    this.consultationRepository = consultationRepository;
    this.messageRepository = messageRepository;
    this.memberProfileService = memberProfileService;
    this.userRepository = userRepository;
    this.debtStatusService = debtStatusService;
    this.atozCaseService = atozCaseService;
    this.aiChatService = aiChatService;
    this.notifier = notifier;
    this.entitlementService = entitlementService;
  }

  // ── 회원 경로 ──────────────────────────────────────────────────────────

  /** 새 상담 생성 — 첫 질문 1건을 MEMBER 메시지로 남기고, 질문에서 A to Z 단계를 결정적으로 연결(§7). */
  @Transactional
  public ConsultationThreadView create(AuthenticatedPrincipal principal, String content, boolean linkDebt) {
    entitlementService.requireThanksMember(principal);
    String text = normalize(content);
    LocalDateTime now = LocalDateTime.now();
    NewStartConsultation saved = consultationRepository.save(NewStartConsultation.builder()
        .memberUserId(principal.userId())
        .status(ConsultationStatus.NEW)
        .linkDebt(linkDebt)
        .atozStep(detectStepOrNull(text))
        .lastMessageAt(now)
        .build());
    NewStartConsultationMessage msg = messageRepository.save(NewStartConsultationMessage.builder()
        .consultationId(saved.getConsultationId())
        .authorRole(MessageAuthorRole.MEMBER)
        .content(text)
        .build());
    saved.markMemberSeen(msg.getMessageId());
    consultationRepository.save(saved);
    notifyOperatorAfterCommit(saved, principal.userId(), text, now);
    return memberThread(saved, List.of(msg));
  }

  /** 내 상담 목록(§7). 각 행에 못 본 운영자 답변 수를 실어 "새 답변 N건"을 뽑는다. */
  /** 커밋 이후에만 알림을 보낸다. 메일 준비·발송 어떤 실패도 상담 저장에 영향을 주지 않는다. */
  private void notifyOperatorAfterCommit(NewStartConsultation c, Long userId, String text, LocalDateTime at) {
    try {
      String nickname = userRepository.findById(userId)
          .map(memberProfileService::resolveDisplayNickname).orElse(null);
      Long id = c.getConsultationId();
      Runnable send = () -> notifier.notifyAsync(id, nickname, text, at);
      if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
          @Override public void afterCommit() {
            try {
              send.run();
            } catch (RuntimeException e) {
              // 커밋 후 알림 요청 실패는 이미 저장된 상담에 영향을 주지 않는다.
            }
          }
        });
      } else {
        send.run();
      }
    } catch (RuntimeException e) {
      // 알림 준비 실패는 상담 생성 결과와 무관하다.
    }
  }

  @Transactional(readOnly = true)
  public List<ConsultationSummaryView> myConsultations(AuthenticatedPrincipal principal) {
    entitlementService.requireThanksMember(principal);
    return consultationRepository.findByMemberUserIdOrderByLastMessageAtDesc(principal.userId()).stream()
        .map(c -> new ConsultationSummaryView(
            c.getConsultationId(),
            c.getStatus().name(),
            statusLabel(c.getStatus()),
            openingPreview(c.getConsultationId()),
            c.getLastMessageAt(),
            unreadOperator(c),
            c.isLinkDebt()))
        .toList();
  }

  /** 회원 스레드 상세(§7 과거 대화·답변 시간순). AI_RESEARCH는 조회에서 제외되고 열람 시 읽음 커서를 갱신한다. */
  @Transactional
  public ConsultationThreadView thread(AuthenticatedPrincipal principal, Long consultationId) {
    entitlementService.requireThanksMember(principal);
    NewStartConsultation c = ownConsultation(principal, consultationId);
    List<NewStartConsultationMessage> msgs =
        messageRepository.findByConsultationIdAndAuthorRoleInOrderByMessageIdAsc(consultationId, MEMBER_VISIBLE);
    Long topId = messageRepository.findTopByConsultationIdOrderByMessageIdDesc(consultationId)
        .map(NewStartConsultationMessage::getMessageId).orElse(null);
    if (topId != null) {
      c.markMemberSeen(topId);
      consultationRepository.save(c);
    }
    return memberThread(c, msgs);
  }

  /** 같은 상담 스레드에 회원 추가 질문(§2). 운영자 답변 뒤면 MEMBER_FOLLOWUP으로 전이. */
  @Transactional
  public ConsultationThreadView reply(AuthenticatedPrincipal principal, Long consultationId, String content) {
    entitlementService.requireThanksMember(principal);
    String text = normalize(content);
    NewStartConsultation c = ownConsultation(principal, consultationId);
    LocalDateTime now = LocalDateTime.now();
    NewStartConsultationMessage msg = messageRepository.save(NewStartConsultationMessage.builder()
        .consultationId(consultationId)
        .authorRole(MessageAuthorRole.MEMBER)
        .content(text)
        .build());
    c.onMemberMessage();
    c.markMemberSeen(msg.getMessageId());
    c.touch(now);
    consultationRepository.save(c);
    List<NewStartConsultationMessage> msgs =
        messageRepository.findByConsultationIdAndAuthorRoleInOrderByMessageIdAsc(consultationId, MEMBER_VISIBLE);
    return memberThread(c, msgs);
  }

  /** 헤더 배지용 — 내 모든 상담 중 아직 못 본 운영자 답변 총계. */
  @Transactional(readOnly = true)
  public long unreadAnswerCount(AuthenticatedPrincipal principal) {
    entitlementService.requireThanksMember(principal);
    return consultationRepository.findByMemberUserIdOrderByLastMessageAtDesc(principal.userId()).stream()
        .mapToLong(this::unreadOperator)
        .sum();
  }

  // ── 운영자 경로(ADMIN 전용) ────────────────────────────────────────────

  /** 운영자 상담함 목록(§3). 회원 닉네임만 노출. */
  @Transactional(readOnly = true)
  public List<AdminConsultationSummaryView> adminList(AuthenticatedPrincipal principal) {
    requireAdmin(principal);
    List<NewStartConsultation> rows = consultationRepository.findAllByOrderByLastMessageAtDesc();
    if (rows.isEmpty()) return List.of();
    Map<Long, User> users = memberUsers(rows);
    Map<Long, String> nicknames = memberProfileService.resolveDisplayNicknames(new ArrayList<>(users.values()));
    return rows.stream().map(c -> new AdminConsultationSummaryView(
        c.getConsultationId(),
        nicknameOf(c.getMemberUserId(), users.get(c.getMemberUserId()), nicknames),
        c.getStatus().name(),
        statusLabel(c.getStatus()),
        openingPreview(c.getConsultationId()),
        c.getLastMessageAt(),
        unreadMemberQuestion(c),
        c.isLinkDebt())).toList();
  }

  /** 운영자 상담 상세(§3 한 화면: 닉네임/연결 채무상황/현재 단계/이번 질문/이전 대화/관련 사례). 열람 시 상태·읽음 갱신. */
  @Transactional
  public AdminConsultationThreadView adminThread(AuthenticatedPrincipal principal, Long consultationId) {
    requireAdmin(principal);
    NewStartConsultation c = loadExisting(consultationId);
    User member = userRepository.findById(c.getMemberUserId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다."));
    String nickname = memberProfileService.resolveDisplayNickname(member);
    List<NewStartConsultationMessage> all =
        messageRepository.findByConsultationIdOrderByMessageIdAsc(consultationId);
    if (!all.isEmpty()) {
      c.markOperatorSeen(all.get(all.size() - 1).getMessageId());
    }
    c.onOperatorView();
    consultationRepository.save(c);

    Integer step = c.getAtozStep();
    Step st = step == null ? null : NewStartAtozStep.byNo(step);
    DebtStatusView debt = c.isLinkDebt() ? debtStatusService.findMine(c.getMemberUserId()) : null;
    List<AtozCase> cases = step == null
        ? List.of()
        : atozCaseService.casesByStep().getOrDefault(step, List.of());
    List<ConsultationMessageView> messages = all.stream().map(NewStartConsultationService::toMessageView).toList();

    return new AdminConsultationThreadView(
        c.getConsultationId(), nickname, c.getStatus().name(), statusLabel(c.getStatus()),
        step, st == null ? null : st.title(), st == null ? null : st.url(),
        debt, cases, messages);
  }

  /** [회원에게 답변] — 운영자 답변을 회원 스레드에 전달(§5). AI 조사 결과와 별개로, 운영자가 작성한 텍스트만 보낸다. */
  @Transactional
  public ConsultationMessageView adminAnswer(AuthenticatedPrincipal principal, Long consultationId, String content) {
    requireAdmin(principal);
    String text = normalize(content);
    NewStartConsultation c = loadExisting(consultationId);
    NewStartConsultationMessage msg = messageRepository.save(NewStartConsultationMessage.builder()
        .consultationId(consultationId)
        .authorRole(MessageAuthorRole.OPERATOR)
        .content(text)
        .build());
    c.onOperatorAnswer();
    c.touch(LocalDateTime.now());
    consultationRepository.save(c);
    return toMessageView(msg);
  }

  /**
   * [지피지기에게 조사시키기] — 회원의 최신 질문을 <b>익명화한 뒤</b> 지피지기에 조사시키고, 결과를
   * 운영자 전용 AI_RESEARCH 메시지로 남긴다(§4·§5). 회원에게 자동 전송하지 않고, 상태·정렬 시각도 바꾸지 않는다.
   * 조사는 지피지기 원칙 순서(전자책→실제 사례→운영자 경험→공식 확인)를 그대로 따른다(NewStartAiChatService 재사용).
   */
  @Transactional
  public ConsultationMessageView adminResearch(AuthenticatedPrincipal principal, Long consultationId) {
    requireAdmin(principal);
    NewStartConsultation c = loadExisting(consultationId);
    User member = userRepository.findById(c.getMemberUserId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다."));
    String question = messageRepository
        .findTopByConsultationIdAndAuthorRoleOrderByMessageIdDesc(consultationId, MessageAuthorRole.MEMBER)
        .map(NewStartConsultationMessage::getContent)
        .orElseThrow(() -> new IllegalArgumentException("조사할 회원 질문이 없습니다."));

    String anonymized = JipijigiSanitizer.mask(question, identityTerms(member));
    NewStartAiAnswer step1 = aiChatService.ask(anonymized);
    String note;
    if (!step1.ok()) {
      note = "지피지기 조사(익명 질문 기준)\n\n[1차 조사 실패] " + nullSafe(step1.errorMessage());
    } else {
      NewStartAiAnswer step2 = aiChatService.askOfficial(anonymized, step1.answer());
      note = composeResearchNote(step1, step2);
    }
    NewStartConsultationMessage msg = messageRepository.save(NewStartConsultationMessage.builder()
        .consultationId(consultationId)
        .authorRole(MessageAuthorRole.AI_RESEARCH)
        .content(note)
        .build());
    return toMessageView(msg);
  }

  // ── 내부 헬퍼 ──────────────────────────────────────────────────────────

  /** 회원 이름·닉네임·표시 별명을 마스킹 대상어로(§4 개인 식별정보를 검색어에 그대로 넣지 않는다). */
  private List<String> identityTerms(User member) {
    List<String> terms = new ArrayList<>();
    addIfPresent(terms, member.getName());
    addIfPresent(terms, member.getNickname());
    addIfPresent(terms, memberProfileService.resolveDisplayNickname(member));
    return terms;
  }

  private static void addIfPresent(List<String> list, String value) {
    if (value != null && !value.isBlank() && value.length() >= 2) list.add(value);
  }

  private static String composeResearchNote(NewStartAiAnswer step1, NewStartAiAnswer step2) {
    StringBuilder sb = new StringBuilder();
    sb.append("지피지기 조사 (개인정보를 제외한 익명 질문 기준 — 운영자 전용 초안. 회원에게 자동 전송되지 않습니다.)\n\n");
    sb.append("— 1차 답변 (전자책·실제 사례) —\n").append(nullSafe(step1.answer()).strip());
    if (step2.ok() && !nullSafe(step2.answer()).isBlank()) {
      sb.append("\n\n— 현재 규정 확인 —\n").append(step2.answer().strip());
    } else if (!step2.ok()) {
      sb.append("\n\n— 현재 규정 확인 실패 — ").append(nullSafe(step2.errorMessage()));
    }
    List<AnswerSource> sources = step1.sources();
    if (sources != null && !sources.isEmpty()) {
      sb.append("\n\n[참고 근거]\n");
      for (AnswerSource s : sources) {
        sb.append("- ").append(nullSafe(s.kind())).append(": ").append(nullSafe(s.title()));
        if (s.url() != null && !s.url().isBlank()) sb.append(" ").append(s.url());
        sb.append('\n');
      }
    }
    return sb.toString();
  }

  private ConsultationThreadView memberThread(NewStartConsultation c, List<NewStartConsultationMessage> msgs) {
    Integer step = c.getAtozStep();
    List<ConsultationMessageView> views = msgs.stream().map(NewStartConsultationService::toMessageView).toList();
    // A to Z는 ADMIN 전용 화면이므로 회원 응답에는 제목·URL을 싣지 않는다(내부 단계 번호만 유지).
    return new ConsultationThreadView(
        c.getConsultationId(), c.getStatus().name(), statusLabel(c.getStatus()), c.isLinkDebt(),
        step, null, null, views);
  }

  /** HARD GATE — 본인 상담만. 없으면 404, 다른 회원 것이면 403(AccessDeniedException). */
  private NewStartConsultation ownConsultation(AuthenticatedPrincipal principal, Long consultationId) {
    NewStartConsultation c = consultationRepository.findById(consultationId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "상담을 찾을 수 없습니다."));
    if (!c.getMemberUserId().equals(principal.userId())) {
      throw new AccessDeniedException("본인의 상담이 아닙니다.");
    }
    return c;
  }

  private NewStartConsultation loadExisting(Long consultationId) {
    return consultationRepository.findById(consultationId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "상담을 찾을 수 없습니다."));
  }

  /**
   * 상담은 로그인한 모든 회원이 쓴다(감사회원 요건 없음 — 2026-10 정책). 소유권은 {@link #ownConsultation}이
   * 계속 강제한다. 지피지기(THANKS_MEMBER)와 이 권한은 별개다.
   */
  private void requireAdmin(AuthenticatedPrincipal principal) {
    if (principal == null || !"ADMIN".equals(principal.role())) {
      throw new AccessDeniedException("운영자만 접근할 수 있습니다.");
    }
  }

  private String normalize(String content) {
    String text = content == null ? "" : content.strip();
    if (text.isEmpty()) throw new IllegalArgumentException("내용을 입력해 주세요.");
    if (text.length() > MAX_MESSAGE_CHARS) {
      throw new IllegalArgumentException("내용이 너무 깁니다. " + MAX_MESSAGE_CHARS + "자 이내로 입력해 주세요.");
    }
    return text;
  }

  private static Integer detectStepOrNull(String text) {
    int step = NewStartAtozStep.detectStepNo(text);
    return step < 0 ? null : step;
  }

  private long unreadOperator(NewStartConsultation c) {
    return messageRepository.countByConsultationIdAndAuthorRoleAndMessageIdGreaterThan(
        c.getConsultationId(), MessageAuthorRole.OPERATOR, cursor(c.getMemberLastSeenMessageId()));
  }

  private long unreadMemberQuestion(NewStartConsultation c) {
    return messageRepository.countByConsultationIdAndAuthorRoleAndMessageIdGreaterThan(
        c.getConsultationId(), MessageAuthorRole.MEMBER, cursor(c.getOperatorLastSeenMessageId()));
  }

  private String openingPreview(Long consultationId) {
    return messageRepository
        .findFirstByConsultationIdAndAuthorRoleOrderByMessageIdAsc(consultationId, MessageAuthorRole.MEMBER)
        .map(m -> preview(m.getContent())).orElse("");
  }

  private static long cursor(Long seen) {
    return seen == null ? 0L : seen;
  }

  private static String preview(String content) {
    String oneLine = content == null ? "" : content.replace("\n", " ").strip();
    return oneLine.length() <= PREVIEW_CHARS ? oneLine : oneLine.substring(0, PREVIEW_CHARS) + "…";
  }

  private Map<Long, User> memberUsers(List<NewStartConsultation> rows) {
    List<Long> ids = rows.stream().map(NewStartConsultation::getMemberUserId).distinct().toList();
    return userRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(User::getUserId, u -> u, (a, b) -> a));
  }

  private String nicknameOf(Long userId, User user, Map<Long, String> nicknames) {
    if (user == null) return "알 수 없는 회원";
    return nicknames.getOrDefault(userId, user.getDisplayName());
  }

  private static String statusLabel(ConsultationStatus status) {
    return switch (status) {
      case NEW -> "새 상담";
      case AWAITING_ANSWER -> "답변 대기";
      case OPERATOR_ANSWERED -> "운영자 답변 완료";
      case MEMBER_FOLLOWUP -> "회원 추가 질문";
    };
  }

  private static ConsultationMessageView toMessageView(NewStartConsultationMessage m) {
    return new ConsultationMessageView(
        m.getMessageId(), m.getAuthorRole().name(), m.getContent(), m.getCreatedAt());
  }

  private static String nullSafe(String s) {
    return s == null ? "" : s;
  }
}
