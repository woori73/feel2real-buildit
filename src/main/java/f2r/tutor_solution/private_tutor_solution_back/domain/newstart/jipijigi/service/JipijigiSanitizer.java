package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 카페 원문을 Agent 게시 초안으로 재사용하기 전 개인정보 마스킹.
 * 닉네임 재노출 금지 + 연락처/주민번호류 패턴 차단을 한 곳에서 수행한다.
 */
public final class JipijigiSanitizer {

    private static final Pattern PHONE = Pattern.compile("(?:0\\d{1,2}-\\d{3,4}-\\d{4}|0\\d{9,10})");
    private static final Pattern SSN = Pattern.compile("\\d{6}-[1234]\\d{6}");
    private static final Pattern EMAIL = Pattern.compile("[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+");
    private static final Pattern ACCOUNT = Pattern.compile("(?:국민|신한|우리|하나|농협|기업|새마을|광주|제주|부산|경남|토스|카카오)\\d{2,}[-]?\\d{6,}");

    private JipijigiSanitizer() {
    }

    public static String mask(String text, List<String> nicknames) {
        if (text == null || text.isBlank()) return text;
        String out = text;
        if (nicknames != null) {
            for (String nick : nicknames) {
                if (nick == null || nick.isBlank() || nick.length() < 2) continue;
                out = out.replace(nick, "(작성자)");
            }
        }
        out = EMAIL.matcher(out).replaceAll("(이메일 마스킹)");
        out = SSN.matcher(out).replaceAll("(주민번호 마스킹)");
        out = ACCOUNT.matcher(out).replaceAll("(계좌 마스킹)");
        out = PHONE.matcher(out).replaceAll("(연락처 마스킹)");
        return out;
    }
}
