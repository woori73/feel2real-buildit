package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * 학교 프린트 HTML 에서 Showcase scene 텍스트를 <b>bounded pattern</b> 으로 뽑는다(Phase 4e).
 *
 * <p>원칙:
 * <ul>
 *   <li>특정 한 학교의 DOM 구조에 맞춘 selector 를 쓰지 않는다 — {@code title} + 모든 heading /
 *       문단 텍스트를 전체 스캔한다.</li>
 *   <li>AI 생성 / 인터넷 검색 / 추측 없음. 자료에 실제로 있는 문자열만.</li>
 *   <li>불확실하면 채우지 않는다(잘못 뽑는 것보다 생략).</li>
 * </ul>
 */
final class MidtermShowcaseExtractor {

  private MidtermShowcaseExtractor() {}

  /** {@code lesson}(nullable), {@code grammarPoints}, {@code sampleSentences}. */
  record Extract(String lesson, List<String> grammarPoints, List<String> sampleSentences) {
    static final Extract EMPTY = new Extract(null, List.of(), List.of());
  }

  private static final int MAX_HEADINGS = 24;
  private static final int MAX_BODY_SCAN = 40;
  private static final int MAX_GRAMMAR = 3;
  private static final int MAX_SENTENCES = 3;

  private static final Pattern LESSON =
      Pattern.compile("(?i)lesson\\s*0*([1-9]\\d?)|([1-9]\\d?)\\s*과(?![가-힣])");
  private static final Pattern BRACKET_TOPIC =
      Pattern.compile("[\\[(【]\\s*([가-힣][가-힣A-Za-z0-9·\\s]{1,18}?)\\s*[\\])】]");
  /** 흔한 중등 영어 문법 용어 allowlist — heading/title 텍스트에 그대로 등장할 때만 채택한다. */
  private static final List<String> GRAMMAR_TERMS = List.of(
      "가정법 과거완료", "가정법 과거", "혼합가정", "가정법",
      "관계대명사", "관계부사", "관계사",
      "간접의문문", "부가의문문",
      "현재완료", "과거완료", "미래완료",
      "수동태", "능동태",
      "분사구문", "현재분사", "과거분사", "분사",
      "to부정사", "부정사", "동명사",
      "조동사", "used to",
      "비교급", "최상급", "원급", "비교",
      "5형식", "4형식", "3형식", "형식 문장", "형식",
      "접속사", "전치사", "명사절", "형용사절", "부사절",
      "사역동사", "지각동사", "지시대명사", "재귀대명사",
      "가주어", "진주어", "강조구문", "도치");
  /** 예문이 아니라 문제 지시문인 줄 — 앞머리로 걸러낸다. */
  private static final List<String> INSTRUCTION_STEMS = List.of(
      "read ", "write ", "fill ", "choose ", "combine ", "complete ", "rewrite ",
      "look ", "circle ", "underline ", "match ", "answer ", "translate ", "make ",
      "change ", "find ", "put ", "use ", "select ", "arrange ", "correct ");
  private static final Pattern SENTENCE =
      Pattern.compile("^[A-Z][A-Za-z0-9 ,'\"()\\-;:]{10,108}[.?!]$");
  private static final Pattern HAS_LOWER = Pattern.compile("[a-z]");

  static Extract from(String html) {
    if (html == null || html.isBlank()) {
      return Extract.EMPTY;
    }
    Document doc;
    try {
      doc = Jsoup.parse(html);
    } catch (RuntimeException e) {
      return Extract.EMPTY;
    }

    List<String> headingTexts = new ArrayList<>();
    String title = safe(doc.title());
    if (!title.isEmpty()) {
      headingTexts.add(title);
    }
    doc.select("h1, h2, h3, h4").stream()
        .limit(MAX_HEADINGS)
        .map(e -> safe(e.text()))
        .filter(t -> !t.isEmpty())
        .forEach(headingTexts::add);

    // 문법 용어는 heading 뿐 아니라 본문 앞부분(개념정리)에도 나온다. 짧은 텍스트 블록만
    // 앞에서 몇 개 스캔한다 — allowlist 매칭이라 지시문에서 잘못 뽑힐 위험이 낮다.
    List<String> grammarTexts = new ArrayList<>(headingTexts);
    doc.select("p, li, th, strong, b").stream()
        .limit(MAX_BODY_SCAN)
        .map(e -> safe(e.text()))
        .filter(t -> !t.isEmpty() && t.length() <= 90)
        .forEach(grammarTexts::add);

    String lesson = extractLesson(headingTexts);
    List<String> grammar = extractGrammar(grammarTexts);
    List<String> sentences = extractSentences(doc);
    return new Extract(lesson, grammar, sentences);
  }

  private static String extractLesson(List<String> texts) {
    for (String t : texts) {
      Matcher m = LESSON.matcher(t);
      if (m.find()) {
        String n = m.group(1) != null ? m.group(1) : m.group(2);
        return "Lesson " + n;
      }
    }
    return null;
  }

  private static List<String> extractGrammar(List<String> texts) {
    Set<String> out = new LinkedHashSet<>();
    for (String t : texts) {
      Matcher bm = BRACKET_TOPIC.matcher(t);
      while (bm.find() && out.size() < MAX_GRAMMAR) {
        String topic = bm.group(1).trim().replaceAll("\\s+", " ");
        if (looksLikeGrammar(topic)) {
          out.add(topic);
        }
      }
      for (String term : GRAMMAR_TERMS) {
        if (out.size() >= MAX_GRAMMAR) {
          break;
        }
        if (t.contains(term)) {
          out.add(term);
        }
      }
    }
    return new ArrayList<>(out).subList(0, Math.min(out.size(), MAX_GRAMMAR));
  }

  private static boolean looksLikeGrammar(String topic) {
    if (topic.length() < 2 || topic.length() > 18) {
      return false;
    }
    for (String term : GRAMMAR_TERMS) {
      if (topic.contains(term) || term.contains(topic)) {
        return true;
      }
    }
    // "~법/~사/~형/~절/~문/~태" 로 끝나는 짧은 한글 용어만 허용 — 그 외 대괄호 텍스트는 버린다.
    return topic.matches("[가-힣]{2,10}(법|사|형|절|문|태|구문)");
  }

  private static List<String> extractSentences(Document doc) {
    Set<String> out = new LinkedHashSet<>();
    for (var el : doc.select("p, li, td, blockquote, span")) {
      if (out.size() >= MAX_SENTENCES) {
        break;
      }
      String raw = safe(el.text());
      if (raw.isEmpty()) {
        continue;
      }
      String s = firstEnglishSentence(raw);
      if (s == null) {
        continue;
      }
      String lower = s.toLowerCase();
      boolean instruction = false;
      for (String stem : INSTRUCTION_STEMS) {
        if (lower.startsWith(stem)) {
          instruction = true;
          break;
        }
      }
      if (instruction) {
        continue;
      }
      if (SENTENCE.matcher(s).matches() && HAS_LOWER.matcher(s).find() && s.contains(" ")) {
        out.add(s);
      }
    }
    return new ArrayList<>(out);
  }

  /**
   * "e.g. The news made him happy. (해석)" 같은 줄에서 영어 문장만 떼어낸다.
   * 순수 영어 문장 줄은 그대로. 그 외 null.
   */
  private static String firstEnglishSentence(String raw) {
    String t = raw.replaceAll("(?i)^\\s*e\\.?\\s*g\\.?\\s*[:.]?\\s*", "");
    int paren = t.indexOf('(');
    int hangul = indexOfHangul(t);
    int cut = t.length();
    if (paren > 0) {
      cut = Math.min(cut, paren);
    }
    if (hangul > 0) {
      cut = Math.min(cut, hangul);
    }
    t = t.substring(0, cut).trim();
    if (t.isEmpty() || indexOfHangul(t) >= 0) {
      return null;
    }
    if (t.matches(".*[.?!]$")) {
      return t;
    }
    return null;
  }

  private static int indexOfHangul(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c >= '가' && c <= '힣') {
        return i;
      }
    }
    return -1;
  }

  private static String safe(String s) {
    return s == null ? "" : s.replace((char) 0x00A0, ' ').trim().replaceAll("\\s+", " ");
  }
}
