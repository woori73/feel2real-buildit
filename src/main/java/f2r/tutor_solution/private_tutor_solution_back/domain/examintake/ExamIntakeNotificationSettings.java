package f2r.tutor_solution.private_tutor_solution_back.domain.examintake;

import static f2r.tutor_solution.private_tutor_solution_back.domain.examintake.ExamIntake.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import jakarta.mail.internet.InternetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Private runtime settings; neither recipients nor provider credentials belong in source control. */
@Component
@ConditionalOnProperty(name = "app.exam-intake.enabled", havingValue = "true")
public class ExamIntakeNotificationSettings {
  record Config(
      long version, String email, String phone, boolean emailEnabled, boolean smsEnabled,
      String naverAccount, String naverPassword, String solapiKey, String solapiSecret,
      String sender, String emailSince, String smsSince) {
    static Config empty() {
      return new Config(0, "", "", false, false, "", "", "", "", "", "", "");
    }
    boolean emailReady() {
      return emailEnabled && !email.isBlank() && !naverAccount.isBlank() && !naverPassword.isBlank();
    }
    boolean smsReady() {
      return smsEnabled && !phone.isBlank() && !sender.isBlank()
          && !solapiKey.isBlank() && !solapiSecret.isBlank();
    }
    @Override public String toString() { return "ExamIntakeNotificationConfig[redacted]"; }
  }

  public record Update(
      long version, String email, String phone, boolean emailEnabled, boolean smsEnabled,
      String naverAccount, String naverPassword, String solapiKey, String solapiSecret,
      String sender) {
    @Override public String toString() { return "ExamIntakeNotificationUpdate[redacted]"; }
  }

  private final Path file;
  private final SecretKeySpec key;
  private final ObjectMapper json = new ObjectMapper();
  private final SecureRandom random = new SecureRandom();

  public ExamIntakeNotificationSettings(
      ExamPrivateFiles files, @Value("${app.jwt.secret:}") String secret) {
    file = files.root.resolve(".notification-settings.v1.enc");
    try {
      key = secret.getBytes(StandardCharsets.UTF_8).length < 32 ? null
          : new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(
              ("exam-intake-notifications-v1\0" + secret).getBytes(StandardCharsets.UTF_8)), "AES");
    } catch (Exception e) {
      throw new IllegalStateException("알림 설정 암호화를 준비할 수 없습니다.");
    }
  }

  synchronized Config current() {
    if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return Config.empty();
    try {
      if (key == null || Files.isSymbolicLink(file) || Files.size(file) > 16384)
        throw new IllegalStateException();
      byte[] stored = Files.readAllBytes(file);
      if (stored.length < 29) throw new IllegalStateException();
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, stored, 0, 12));
      return json.readValue(cipher.doFinal(stored, 12, stored.length - 12), Config.class);
    } catch (Exception e) {
      throw error(503, "저장된 알림 설정을 읽을 수 없습니다. 운영 담당자에게 확인해 주세요.");
    }
  }

  public Map<String, Object> view(AuthenticatedPrincipal p) {
    admin(p);
    return view(current());
  }

  private Map<String, Object> view(Config c) {
    var result = new LinkedHashMap<String, Object>();
    result.put("version", c.version());
    result.put("email", c.email());
    result.put("phone", c.phone());
    result.put("emailEnabled", c.emailEnabled());
    result.put("smsEnabled", c.smsEnabled());
    result.put("naverAccount", c.naverAccount());
    result.put("sender", c.sender());
    result.put("emailCredentialSaved", !c.naverPassword().isBlank());
    result.put("smsCredentialSaved", !c.solapiKey().isBlank() && !c.solapiSecret().isBlank());
    result.put("emailStatus", !c.emailEnabled() ? "DISABLED" : c.emailReady() ? "READY" : "CONNECTION_REQUIRED");
    result.put("smsStatus", !c.smsEnabled() ? "DISABLED" : c.smsReady() ? "READY" : "CONNECTION_REQUIRED");
    result.put("storageReady", key != null);
    return result;
  }

  public synchronized Map<String, Object> save(AuthenticatedPrincipal p, Update u) {
    admin(p);
    if (key == null) throw error(503, "운영 암호화 설정을 먼저 확인해 주세요.");
    Config old = current();
    if (u.version() != old.version()) throw error(409, "알림 설정이 변경되었습니다. 새로고침 후 다시 저장해 주세요.");
    String email = email(u.email(), false);
    String phone = phone(u.phone());
    String account = email(u.naverAccount(), true);
    String password = preserve(u.naverPassword(), old.naverPassword(), 256);
    if (!old.naverAccount().isBlank() && !old.naverAccount().equals(account)
        && clean(u.naverPassword()).isBlank()) password = "";
    String apiKey = preserve(u.solapiKey(), old.solapiKey(), 256);
    String apiSecret = preserve(u.solapiSecret(), old.solapiSecret(), 256);
    String sender = phone(u.sender());
    if ((u.emailEnabled() && email.isBlank()) || (u.smsEnabled() && phone.isBlank()))
      throw error(400, "사용할 알림의 수신처를 입력해 주세요.");
    // Event timestamps on the deployed MySQL schema have second precision.
    String now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
    Config candidate = new Config(old.version() + 1, email, phone, u.emailEnabled(), u.smsEnabled(),
        account, password, apiKey, apiSecret, sender, old.emailSince(), old.smsSince());
    Config saved = new Config(candidate.version(), email, phone, u.emailEnabled(), u.smsEnabled(),
        account, password, apiKey, apiSecret, sender,
        candidate.emailReady() && !old.emailReady() ? now : old.emailSince(),
        candidate.smsReady() && !old.smsReady() ? now : old.smsSince());
    Path temporary = null;
    try {
      if (Files.isSymbolicLink(file)) throw new IllegalStateException();
      byte[] nonce = new byte[12];
      random.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
      byte[] encrypted = cipher.doFinal(json.writeValueAsBytes(saved));
      byte[] bytes = Arrays.copyOf(nonce, nonce.length + encrypted.length);
      System.arraycopy(encrypted, 0, bytes, nonce.length, encrypted.length);
      temporary = Files.createTempFile(file.getParent(), ".notification-", ".tmp",
          PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
      Files.write(temporary, bytes);
      Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      return view(saved);
    } catch (Exception e) {
      throw error(503, "알림 설정을 저장하지 못했습니다. 다시 확인해 주세요.");
    } finally {
      if (temporary != null) try { Files.deleteIfExists(temporary); } catch (Exception ignored) { }
    }
  }

  private static String clean(String s) { return Objects.toString(s, "").trim(); }

  private static String preserve(String input, String old, int max) {
    String value = clean(input);
    if (value.length() > max || value.chars().anyMatch(Character::isISOControl))
      throw error(400, "연결 인증정보의 형식을 확인해 주세요.");
    return value.isBlank() ? old : value;
  }

  private static String email(String input, boolean naver) {
    String value = clean(input);
    if (value.isBlank()) return "";
    try {
      var address = new InternetAddress(value, true);
      address.validate();
      if (value.length() > 254 || !value.equals(address.getAddress()) || address.getPersonal() != null
          || value.chars().anyMatch(Character::isISOControl)
          || (naver && !value.toLowerCase(Locale.ROOT).endsWith("@naver.com")))
        throw new IllegalArgumentException();
      return value;
    } catch (Exception e) { throw error(400, "이메일 주소를 확인해 주세요. 발신 계정은 네이버 메일 주소를 입력해 주세요."); }
  }

  private static String phone(String input) {
    String value = clean(input).replaceAll("[\\s-]", "");
    if (!value.isBlank() && !value.matches("0[0-9]{8,10}"))
      throw error(400, "전화번호는 숫자 9~11자리로 입력해 주세요.");
    return value;
  }
}
