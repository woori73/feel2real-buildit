package f2r.tutor_solution.private_tutor_solution_back.domain.academy.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

@DisplayName("AcademyInquiryService — 접수 규칙·개인정보 로그")
class AcademyInquiryServiceTest {

  private final AcademyInquiryRepository repo = mock(AcademyInquiryRepository.class);
  private final AcademyRepository academies = mock(AcademyRepository.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-17T03:00:00Z"), ZoneId.of("Asia/Seoul"));
  private final AcademyInquiryService service = new AcademyInquiryService(repo, academies, clock);
  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
  private final Logger logger = (Logger) LoggerFactory.getLogger(AcademyInquiryService.class);

  @BeforeEach
  void setUp() {
    Academy academy = mock(Academy.class);
    when(academy.getAcademyId()).thenReturn(7L);
    when(academies.findBySlugAndStatusAndVisibleTrue("open", AcademyStatus.PUBLISHED)).thenReturn(Optional.of(academy));
    when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    logs.start();
    logger.addAppender(logs);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(logs);
  }

  @Test
  @DisplayName("given 공개 학원과 정상 입력 when 접수하면 then 저장되고 동의·보관기한이 기록된다")
  void savesWithConsentAndRetention() {
    // given / when
    var r = service.submit("open", "김", "010-1234-5678", "상담 원해요", true, null);
    // then
    assertThat(r).isEqualTo(AcademyInquiryService.SubmitResult.RECEIVED);
    org.mockito.ArgumentCaptor<AcademyInquiry> cap = org.mockito.ArgumentCaptor.forClass(AcademyInquiry.class);
    verify(repo).save(cap.capture());
    AcademyInquiry saved = cap.getValue();
    assertThat(saved.getAcademyId()).isEqualTo(7L);
    assertThat(saved.getStatus()).isEqualTo(InquiryStatus.NEW);
    assertThat(saved.getConsentVersion()).isEqualTo(AcademyInquiry.CONSENT_VERSION);
    assertThat(saved.getRetentionUntil()).isEqualTo(saved.getConsentedAt().toLocalDate().plusDays(365));
  }

  @Test
  @DisplayName("given 함정 입력칸이 채워짐 when 접수하면 then 저장하지 않는다")
  void honeypotIsNotStored() {
    // given / when
    var r = service.submit("open", "김", "01012345678", "문의", true, "http://spam.example");
    // then
    assertThat(r).isEqualTo(AcademyInquiryService.SubmitResult.SKIPPED_SPAM);
    verify(repo, never()).save(any());
  }

  @Test
  @DisplayName("given 같은 연락처가 10분 안에 접수됨 when 다시 접수하면 then 저장하지 않는다")
  void duplicateIsNotStored() {
    // given
    when(repo.existsByAcademyIdAndPhoneAndCreatedAtGreaterThanEqual(anyLong(), anyString(), any())).thenReturn(true);
    // when
    var r = service.submit("open", "김", "01012345678", "문의", true, null);
    // then
    assertThat(r).isEqualTo(AcademyInquiryService.SubmitResult.SKIPPED_DUPLICATE);
    verify(repo, never()).save(any());
  }

  @Test
  @DisplayName("given 학원의 오늘 접수가 한도에 도달 when 접수하면 then 거절한다")
  void dailyLimit() {
    // given
    when(repo.countByAcademyIdAndCreatedAtGreaterThanEqual(anyLong(), any())).thenReturn((long) AcademyInquiryService.PER_ACADEMY_DAILY_LIMIT);
    // when / then
    assertThat(service.submit("open", "김", "01012345678", "문의", true, null)).isEqualTo(AcademyInquiryService.SubmitResult.DAILY_LIMIT);
    verify(repo, never()).save(any());
  }

  @Test
  @DisplayName("given 공개되지 않은 학원 slug when 접수하면 then 없는 학원으로 취급한다")
  void unknownAcademy() {
    // given / when / then
    assertThat(service.submit("draft", "김", "01012345678", "문의", true, null)).isEqualTo(AcademyInquiryService.SubmitResult.ACADEMY_NOT_FOUND);
  }

  @Test
  @DisplayName("given 접수·스팸·한도 처리 when 로그를 보면 then 이름·연락처·문의 본문이 한 줄도 없다")
  void logsContainNoPersonalData() {
    // given
    String name = "비밀이름", phone = "01099998888", message = "비밀문의본문";
    // when
    service.submit("open", name, phone, message, true, null);
    service.submit("open", name, phone, message, true, "bot");
    // then
    assertThat(logs.list).isNotEmpty();
    for (ILoggingEvent e : logs.list) {
      assertThat(e.getFormattedMessage()).doesNotContain(name, phone, message);
    }
  }
}
