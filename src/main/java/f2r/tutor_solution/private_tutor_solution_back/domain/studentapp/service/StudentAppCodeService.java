package f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyStudentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyStudentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.entity.StudentAppCode;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.repository.StudentAppCodeRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재원생 앱 접속 코드: 원장이 학생부 학생에게 발급·재발급·폐기한다(2026-11-21 사용자 결정).
 * <ul>
 *   <li>코드는 헷갈리는 글자(0·O·1·I)를 뺀 8글자. 원문은 발급할 때 한 번만 보여 주고 SHA-256만 저장한다.</li>
 *   <li>학생마다 사용 중(ACTIVE) 코드는 하나다. 재발급하면 이전 코드는 바로 못 쓴다.</li>
 *   <li>재원 중(ENROLLED)인 학생만 로그인된다. 그만둔 학생의 응시 기록은 지우지 않는다.</li>
 * </ul>
 */
@Service
@Transactional
public class StudentAppCodeService {

  static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  static final int LENGTH = 8;
  public static final String APP_PATH = "/naesintest/app";

  private final StudentAppCodeRepository codes;
  private final EasyStudentRepository students;
  private final SecureRandom random;

  @Autowired
  public StudentAppCodeService(StudentAppCodeRepository codes, EasyStudentRepository students) {
    this(codes, students, new SecureRandom());
  }

  StudentAppCodeService(StudentAppCodeRepository codes, EasyStudentRepository students, SecureRandom random) {
    this.codes = codes;
    this.students = students;
    this.random = random;
  }

  /** code = 원문(한 번만 보여 줌), display = 4-4로 끊은 글자, appUrl = 코드가 #뒤에 붙은 앱 주소(서버 로그에 남지 않음). */
  public record IssuedCode(String code, String display, String appUrl, CodeStatus status) {}

  public record CodeStatus(boolean active, String tail, String issuedAt, String lastUsedAt) {
    static final CodeStatus NONE = new CodeStatus(false, null, null, null);
  }

  /** 새 코드를 발급한다. 사용 중인 코드가 있으면 먼저 폐기한다(재발급). */
  public IssuedCode issue(Long businessId, Long studentId, Long issuerUserId) {
    requireEnrolled(studentId);
    LocalDateTime now = LocalDateTime.now();
    codes.findByBusinessIdAndStudentIdAndStatus(businessId, studentId, StudentAppCode.ACTIVE).forEach(c -> c.revoke(now));
    String code;
    String hash;
    do {
      code = generate();
      hash = hash(code);
    } while (codes.findByCodeHash(hash).isPresent());
    StudentAppCode saved = codes.save(new StudentAppCode(businessId, studentId, hash, code.substring(LENGTH - 2), issuerUserId, now));
    return new IssuedCode(code, display(code), APP_PATH + "#code=" + code, status(saved));
  }

  /** 사용 중인 코드를 폐기한다. 이미 로그인한 앱도 다음 요청부터 막힌다. */
  public CodeStatus revoke(Long businessId, Long studentId) {
    LocalDateTime now = LocalDateTime.now();
    codes.findByBusinessIdAndStudentIdAndStatus(businessId, studentId, StudentAppCode.ACTIVE).forEach(c -> c.revoke(now));
    return CodeStatus.NONE;
  }

  @Transactional(readOnly = true)
  public CodeStatus status(Long businessId, Long studentId) {
    return codes.findByBusinessIdAndStudentIdAndStatus(businessId, studentId, StudentAppCode.ACTIVE).stream()
        .findFirst().map(StudentAppCodeService::status).orElse(CodeStatus.NONE);
  }

  /** 학생이 입력한 코드로 로그인한다. 형식이 틀리거나, 폐기됐거나, 재원생이 아니면 빈 값. */
  public Optional<StudentAppCode> login(String raw) {
    String code = normalize(raw);
    if (code == null) return Optional.empty();
    Optional<StudentAppCode> found = codes.findByCodeHash(hash(code)).filter(StudentAppCode::isActive)
        .filter(c -> isEnrolled(c.getStudentId()));
    found.ifPresent(c -> c.touch(LocalDateTime.now()));
    return found;
  }

  /** 앱 요청마다 확인: 코드가 아직 사용 중이고 같은 학생이며 재원 중인지. */
  @Transactional(readOnly = true)
  public Optional<StudentAppCode> activeSession(Long codeId, Long studentId) {
    if (codeId == null || studentId == null) return Optional.empty();
    return codes.findById(codeId).filter(StudentAppCode::isActive)
        .filter(c -> studentId.equals(c.getStudentId())).filter(c -> isEnrolled(c.getStudentId()));
  }

  String generate() {
    StringBuilder sb = new StringBuilder(LENGTH);
    for (int i = 0; i < LENGTH; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    return sb.toString();
  }

  /** 대문자로 바꾸고 하이픈·공백을 뺀다. 8글자가 아니거나 쓰지 않는 글자가 있으면 null. */
  static String normalize(String raw) {
    if (raw == null) return null;
    String v = raw.toUpperCase().replaceAll("[\\s-]", "");
    if (v.length() != LENGTH) return null;
    for (char ch : v.toCharArray()) if (ALPHABET.indexOf(ch) < 0) return null;
    return v;
  }

  static String display(String code) {
    return code.substring(0, 4) + "-" + code.substring(4);
  }

  static String hash(String normalized) {
    try {
      byte[] d = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(d);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static CodeStatus status(StudentAppCode c) {
    return new CodeStatus(c.isActive(), c.getCodeTail(), str(c.getIssuedAt()), str(c.getLastUsedAt()));
  }

  private static String str(LocalDateTime t) {
    return t == null ? null : t.withNano(0).toString();
  }

  private EasyStudent requireEnrolled(Long studentId) {
    EasyStudent s = students.findById(studentId).orElseThrow(() -> new IllegalArgumentException("학생을 찾을 수 없습니다."));
    if (s.getStatus() != EasyStudentStatus.ENROLLED) throw new IllegalArgumentException("재원 중인 학생에게만 코드를 발급합니다.");
    return s;
  }

  private boolean isEnrolled(Long studentId) {
    return students.findById(studentId).map(s -> s.getStatus() == EasyStudentStatus.ENROLLED).orElse(false);
  }
}
