package f2r.tutor_solution.private_tutor_solution_back.domain.user.service;

import java.security.SecureRandom;

/**
 * 임시 비밀번호 생성기. 회원마다 다른 무작위 10자이며, 헷갈리는 글자(0·O·o, 1·l·I)는 쓰지 않는다.
 * 대문자·소문자·숫자가 한 글자씩은 꼭 들어간다. 고정값은 쓰지 않는다.
 */
public final class TemporaryPasswordGenerator {

  static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
  static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
  static final String DIGITS = "23456789";
  static final String ALPHABET = UPPER + LOWER + DIGITS;
  public static final int LENGTH = 10;

  private TemporaryPasswordGenerator() {}

  public static String generate(SecureRandom random) {
    while (true) {
      StringBuilder sb = new StringBuilder(LENGTH);
      for (int i = 0; i < LENGTH; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
      String s = sb.toString();
      if (containsAny(s, UPPER) && containsAny(s, LOWER) && containsAny(s, DIGITS)) return s;
    }
  }

  private static boolean containsAny(String s, String chars) {
    for (int i = 0; i < s.length(); i++) if (chars.indexOf(s.charAt(i)) >= 0) return true;
    return false;
  }
}
