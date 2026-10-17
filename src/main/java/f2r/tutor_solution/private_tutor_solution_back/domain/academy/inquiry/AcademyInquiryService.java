package f2r.tutor_solution.private_tutor_solution_back.domain.academy.inquiry;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학원 공개 홈페이지 상담신청 접수·조회.
 *
 * <ul>
 *   <li>학원별 격리: 접수는 공개된 학원 slug로만, 조회·상태 변경은 로그인한 원장 본인 학원(academy_id 조건)으로만 한다.
 *       ADMIN은 학원 id를 지정해 조회만 할 수 있다(상태 변경 불가).
 *   <li>로그에는 접수 id·학원 id·결과만 남기고 이름·연락처·문의 본문은 남기지 않는다.
 * </ul>
 */
@Service
public class AcademyInquiryService {

  private static final Logger log = LoggerFactory.getLogger(AcademyInquiryService.class);
  static final int PER_ACADEMY_DAILY_LIMIT = 50;
  static final int DUPLICATE_WINDOW_MINUTES = 10;
  static final int MAX_PAGE_SIZE = 50;

  /** 접수 결과. accepted=false이고 reason이 있으면 사용자에게 보여 줄 수 있는 고정 사유다. */
  public enum SubmitResult { RECEIVED, SKIPPED_SPAM, SKIPPED_DUPLICATE, ACADEMY_NOT_FOUND, DAILY_LIMIT }

  public record Item(Long id, String name, String phone, String message, InquiryStatus status,
      LocalDateTime createdAt, LocalDateTime handledAt, boolean anonymized) {}

  public record ListResult(List<Item> items, long total, int page, int size, long newCount) {}

  private final AcademyInquiryRepository inquiries;
  private final AcademyRepository academies;
  private final Clock clock;

  @org.springframework.beans.factory.annotation.Autowired
  public AcademyInquiryService(AcademyInquiryRepository inquiries, AcademyRepository academies) {
    // created_at(JPA Auditing)이 JVM 기본 시간대의 LocalDateTime.now()로 기록되므로 같은 시계를 써야 중복·일일 한도 판정이 맞는다.
    this(inquiries, academies, Clock.systemDefaultZone());
  }

  AcademyInquiryService(AcademyInquiryRepository inquiries, AcademyRepository academies, Clock clock) {
    this.inquiries = inquiries;
    this.academies = academies;
    this.clock = clock;
  }

  /**
   * 공개 접수. honeypot(website)이 채워졌으면 봇으로 보고 저장하지 않되 같은 응답을 준다(봇에게 신호를 주지 않는다).
   * 같은 학원·같은 연락처가 10분 안에 다시 오면 저장하지 않고 접수된 것으로 응답한다(중복 클릭 방지).
   */
  @Transactional
  public SubmitResult submit(
      String slug, String name, String phone, String message, Boolean consent, String honeypot) {
    Optional<Academy> academy = academies.findBySlugAndStatusAndVisibleTrue(slug, AcademyStatus.PUBLISHED);
    if (academy.isEmpty()) {
      return SubmitResult.ACADEMY_NOT_FOUND;
    }
    Long academyId = academy.get().getAcademyId();
    if (honeypot != null && !honeypot.isBlank()) {
      log.info("academy inquiry skipped reason=spam academyId={}", academyId);
      return SubmitResult.SKIPPED_SPAM;
    }
    InquiryInput.Clean in = InquiryInput.validate(name, phone, message, consent);
    LocalDateTime now = LocalDateTime.now(clock);
    if (inquiries.existsByAcademyIdAndPhoneAndCreatedAtGreaterThanEqual(
        academyId, in.phone(), now.minusMinutes(DUPLICATE_WINDOW_MINUTES))) {
      return SubmitResult.SKIPPED_DUPLICATE;
    }
    if (inquiries.countByAcademyIdAndCreatedAtGreaterThanEqual(academyId, now.toLocalDate().atStartOfDay())
        >= PER_ACADEMY_DAILY_LIMIT) {
      log.warn("academy inquiry rejected reason=daily-limit academyId={}", academyId);
      return SubmitResult.DAILY_LIMIT;
    }
    AcademyInquiry saved = inquiries.save(AcademyInquiry.receive(academyId, in.name(), in.phone(), in.message(), now));
    log.info("academy inquiry received academyId={} inquiryId={}", academyId, saved.getInquiryId());
    return SubmitResult.RECEIVED;
  }

  /** 원장 본인 학원의 접수 목록(최신순). status가 null이면 전체. */
  @Transactional(readOnly = true)
  public ListResult listMine(AuthenticatedPrincipal principal, InquiryStatus status, int page, int size) {
    Academy academy = ownAcademy(principal);
    return list(academy.getAcademyId(), status, page, size);
  }

  /** ADMIN 조회 전용: 지정한 학원의 접수 목록. 조회 사실은 id만 기록한다. */
  @Transactional(readOnly = true)
  public ListResult listForAdmin(AuthenticatedPrincipal principal, Long academyId, InquiryStatus status, int page, int size) {
    if (principal == null || !"ADMIN".equals(principal.role())) {
      throw new org.springframework.security.access.AccessDeniedException("관리자만 조회할 수 있습니다.");
    }
    log.info("academy inquiry admin-read adminUserId={} academyId={}", principal.userId(), academyId);
    return list(academyId, status, page, size);
  }

  /** 원장 본인 학원의 접수 상태 변경. 다른 학원의 접수는 존재하지 않는 것처럼 empty. */
  @Transactional
  public Optional<Item> changeStatus(AuthenticatedPrincipal principal, Long inquiryId, InquiryStatus next) {
    Academy academy = ownAcademy(principal);
    return inquiries.findByInquiryIdAndAcademyId(inquiryId, academy.getAcademyId()).map(i -> {
      i.changeStatus(next, principal.userId(), LocalDateTime.now(clock));
      log.info("academy inquiry status academyId={} inquiryId={} status={}", academy.getAcademyId(), inquiryId, next);
      return toItem(i);
    });
  }

  private ListResult list(Long academyId, InquiryStatus status, int page, int size) {
    int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    PageRequest pr = PageRequest.of(Math.max(0, page), safeSize);
    Page<AcademyInquiry> result = status == null
        ? inquiries.findByAcademyIdOrderByInquiryIdDesc(academyId, pr)
        : inquiries.findByAcademyIdAndStatusOrderByInquiryIdDesc(academyId, status, pr);
    long newCount = inquiries.findByAcademyIdAndStatusOrderByInquiryIdDesc(academyId, InquiryStatus.NEW, PageRequest.of(0, 1))
        .getTotalElements();
    return new ListResult(result.getContent().stream().map(AcademyInquiryService::toItem).toList(),
        result.getTotalElements(), result.getNumber(), safeSize, newCount);
  }

  private Academy ownAcademy(AuthenticatedPrincipal principal) {
    if (principal == null || principal.userId() == null || !("TUTOR".equals(principal.role()) || "ADMIN".equals(principal.role()))) {
      throw new org.springframework.security.access.AccessDeniedException("원장만 접근할 수 있습니다.");
    }
    return academies.findByTutor_UserId(principal.userId())
        .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("본인 학원이 없습니다."));
  }

  private static Item toItem(AcademyInquiry i) {
    return new Item(i.getInquiryId(), i.getName(), i.getPhone(), i.getMessage(), i.getStatus(),
        i.getCreatedAt(), i.getHandledAt(), i.getAnonymizedAt() != null);
  }
}
