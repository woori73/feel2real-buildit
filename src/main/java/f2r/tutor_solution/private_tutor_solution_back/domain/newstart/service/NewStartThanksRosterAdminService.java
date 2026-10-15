package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartThanksRoster;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.repository.NewStartThanksRosterRepository;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사회원 명단 수동 관리 — 운영자 API용 서비스.
 *
 * <p>기존에는 명단 1건 추가에도 migration SQL → PR → CI → 머지 → 전체 재배포가 필요했다.
 * 명단은 DB 데이터이므로 이 서비스로 직접 추가하면 회원가입 시 uniqueMatch 로직이 재배포 없이
 * 즉시 적용된다.
 *
 * <p>쓰기는 source=MANUAL 행에만 허용하고, 이미 회원과 연결된(linked) 행은 삭제하지 않는다.
 */
@Service
public class NewStartThanksRosterAdminService {

  public static final String SOURCE_MANUAL = "MANUAL";
  private static final String GRADE_THANKS = "감사멤버";
  private static final int MEMBER_LEVEL_THANKS = 150;
  private static final int TAG_LENGTH = 12;

  private final NewStartThanksRosterRepository rosterRepository;

  public NewStartThanksRosterAdminService(NewStartThanksRosterRepository rosterRepository) {
    this.rosterRepository = rosterRepository;
  }

  public record RosterView(Long rosterId, String nickname, String naverIdPrefix, String grade,
      LocalDate gradeChangedAt, String matchStatus, boolean linked) {}

  public record AddResult(boolean ok, String errorMessage, RosterView roster) {}

  /** 운영자 화면용 MANUAL 명단 (최근 N건). */
  @Transactional(readOnly = true)
  public List<RosterView> listManual(int limit) {
    return rosterRepository.findBySourceOrderByRosterIdDesc(SOURCE_MANUAL).stream()
        .limit(Math.max(limit, 1))
        .map(this::toView)
        .toList();
  }

  /**
   * 명단 1건 수동 추가. 같은 닉네임(정규화 비교)+접두부의 미연결 행이 이미 있으면 중복으로 거부한다.
   */
  @Transactional
  public AddResult addManual(String nickname, String naverIdPrefix, LocalDate gradeChangedAt) {
    String nick = nickname == null ? "" : nickname.strip();
    String prefix = naverIdPrefix == null ? "" : naverIdPrefix.strip().toLowerCase(Locale.ROOT);
    if (nick.isEmpty()) {
      return new AddResult(false, "닉네임을 입력하세요.", null);
    }
    if (prefix.isEmpty()) {
      return new AddResult(false, "네이버 ID 앞자리를 입력하세요.", null);
    }
    boolean duplicate = rosterRepository.findByNickname(nick).stream()
        .anyMatch(r -> !r.isLinked()
            && normalize(r.getNickname()).equalsIgnoreCase(nick)
            && prefix.equals(nullToEmpty(r.getNaverIdPrefix()).strip().toLowerCase(Locale.ROOT)));
    if (duplicate) {
      return new AddResult(false, "이미 등록된 명단입니다.", null);
    }

    LocalDate today = LocalDate.now();
    NewStartThanksRoster roster = NewStartThanksRoster.builder()
        .cafeMemberTag(generateTag())
        .naverIdPrefix(prefix)
        .nickname(nick)
        .grade(GRADE_THANKS)
        .gradeChangedAt(gradeChangedAt)
        .memberLevel(MEMBER_LEVEL_THANKS)
        .source(SOURCE_MANUAL)
        .snapshotDate(today)
        .sourceUrl(null)
        .sourceFile(null)
        .matchStatus(NewStartThanksRoster.MATCH_UNMATCHED)
        .build();
    return new AddResult(true, null, toView(rosterRepository.save(roster)));
  }

  /** 미연결 MANUAL 행만 삭제한다. 이미 회원과 연결된 행은 삭제하지 않는다. */
  @Transactional
  public AddResult removeManual(Long rosterId) {
    NewStartThanksRoster roster = rosterRepository.findById(rosterId).orElse(null);
    if (roster == null) {
      return new AddResult(false, "명단이 없습니다.", null);
    }
    if (!SOURCE_MANUAL.equals(roster.getSource())) {
      return new AddResult(false, "수동 등록분이 아닙니다.", null);
    }
    if (roster.isLinked()) {
      return new AddResult(false, "이미 회원과 연결된 명단은 삭제할 수 없습니다.", null);
    }
    rosterRepository.delete(roster);
    return new AddResult(true, null, null);
  }

  private RosterView toView(NewStartThanksRoster r) {
    return new RosterView(r.getRosterId(), r.getNickname(), r.getNaverIdPrefix(), r.getGrade(),
        r.getGradeChangedAt(), r.getMatchStatus(), r.isLinked());
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  static String normalize(String raw) {
    if (raw == null) {
      return "";
    }
    return Normalizer.normalize(raw, Normalizer.Form.NFC).strip().replaceAll("\\s+", " ");
  }

  private String generateTag() {
    SecureRandom random = new SecureRandom();
    String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
    for (int attempt = 0; attempt < 10; attempt++) {
      StringBuilder sb = new StringBuilder(TAG_LENGTH);
      for (int i = 0; i < TAG_LENGTH; i++) {
        sb.append(chars.charAt(random.nextInt(chars.length())));
      }
      String tag = sb.toString();
      if (!rosterRepository.existsByCafeMemberTag(tag)) {
        return tag;
      }
    }
    throw new IllegalStateException("명단 태그 생성 실패");
  }
}
