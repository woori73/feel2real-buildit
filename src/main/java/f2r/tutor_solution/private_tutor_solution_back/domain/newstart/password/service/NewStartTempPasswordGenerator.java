package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * ADMIN 초기화용 1회용 임시 비밀번호 생성기.
 *
 * <p>길이는 NewStart 가입 정책과 같은 최소 8자 이상(여기는 12자). 보기·듣기 쉬운 글자만 쓴다 —
 * 회원이 운영자에게 전화로 받아 적는 값이라 0/O·1/l/I 같은 혼동 글자를 제외한다.
 *
 * <p>만들어진 값은 호출자에게 한 번만 반환되고 어디에도 저장·로그되지 않는다. 저장되는 것은
 * {@code PasswordEncoder}로 만든 hash뿐이다.
 */
@Component
public class NewStartTempPasswordGenerator {

  static final int LENGTH = 12;
  /** 흔한 혼동 글자(0 O o 1 l I)를 뺀 알파벳 + 숫자. */
  private static final char[] ALPHABET =
      "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
  private static final char[] DIGITS = "23456789".toCharArray();

  private final SecureRandom random = new SecureRandom();

  public String generate() {
    char[] value = new char[LENGTH];
    for (int i = 0; i < LENGTH; i++) {
      value[i] = ALPHABET[random.nextInt(ALPHABET.length)];
    }
    // 문자로만 채워지는 경우를 막기 위해 마지막 자리는 숫자로 확정한다(최소 숫자 1개 보장).
    value[LENGTH - 1] = DIGITS[random.nextInt(DIGITS.length)];
    return new String(value);
  }
}
