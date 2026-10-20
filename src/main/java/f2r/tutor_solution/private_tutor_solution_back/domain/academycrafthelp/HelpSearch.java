package f2r.tutor_solution.private_tutor_solution_back.domain.academycrafthelp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 한글 2-gram BM25. 문서를 "## 섹션" 단위로 쪼개 점수를 매기고, 제목은 가중치를 준다. */
public final class HelpSearch {

  public record Section(HelpDoc doc, String heading, String text) {}

  /** matched/total: 서로 다른 질문 2-gram 중 이 섹션에 실제로 있는 개수/전체 개수. 우연한 한 글자 일치로 답하지 않기 위한 근거 지표. */
  public record Hit(Section section, double score, int matched, int total) {}

  private static final double K1 = 1.2;
  private static final double B = 0.75;

  private final List<Section> sections = new ArrayList<>();
  private final List<Map<String, Integer>> tfs = new ArrayList<>();
  private final Map<String, Integer> df = new HashMap<>();
  private double avgLen;

  public HelpSearch(List<HelpDoc> docs) {
    for (HelpDoc d : docs) {
      String intro = d.body();
      String[] parts = d.body().split("(?m)^## ");
      intro = parts[0].strip();
      sections.add(new Section(d, d.title(), intro));
      for (int i = 1; i < parts.length; i++) {
        int nl = parts[i].indexOf('\n');
        String heading = nl < 0 ? parts[i].strip() : parts[i].substring(0, nl).strip();
        String text = nl < 0 ? "" : parts[i].substring(nl + 1).strip();
        sections.add(new Section(d, heading, text));
      }
    }
    long total = 0;
    for (Section s : sections) {
      Map<String, Integer> tf = new HashMap<>();
      List<String> grams = grams(s.doc().title() + " " + s.heading() + " " + s.heading() + " " + s.text());
      for (String g : grams) tf.merge(g, 1, Integer::sum);
      tf.keySet().forEach(g -> df.merge(g, 1, Integer::sum));
      tfs.add(tf);
      total += grams.size();
    }
    avgLen = sections.isEmpty() ? 1 : (double) total / sections.size();
  }

  public List<Hit> search(String query, int limit) {
    List<String> qg = grams(query);
    int distinct = (int) qg.stream().distinct().count();
    List<Hit> hits = new ArrayList<>();
    int n = sections.size();
    for (int i = 0; i < n; i++) {
      Map<String, Integer> tf = tfs.get(i);
      double len = tf.values().stream().mapToInt(Integer::intValue).sum();
      double score = 0;
      int matched = 0;
      for (String g : qg) {
        Integer f = tf.get(g);
        if (f == null) continue;
        matched++;
        double idf = Math.log(1 + (n - df.get(g) + 0.5) / (df.get(g) + 0.5));
        score += idf * f * (K1 + 1) / (f + K1 * (1 - B + B * len / avgLen));
      }
      if (score > 0) hits.add(new Hit(sections.get(i), score, matched, distinct));
    }
    hits.sort((a, b) -> Double.compare(b.score(), a.score()));
    return hits.subList(0, Math.min(limit, hits.size()));
  }

  private static final String[] SUFFIXES = {"하나요", "하세요", "합니다", "하는", "하기", "하려면", "하면"};
  private static final java.util.Set<String> STOP_GRAMS =
      java.util.Set.of("어떻", "떻게", "하나", "나요", "하세", "세요", "니다", "있나", "있는", "수 ", "하면", "려면", "알려", "려줘", "어요", "싶어", "줘요", "주세");

  /** 공백·기호를 제거한 글자열의 2-gram. 흔한 어미 2-gram과 단어 끝 어미(하기·하나요 등)·조사 1자는 제외하고, 한 글자 단어는 1-gram으로 둔다. */
  static List<String> grams(String text) {
    String norm = text.toLowerCase().replaceAll("[^\\p{L}\\p{N}]+", " ");
    List<String> out = new ArrayList<>();
    for (String w : norm.split(" ")) {
      for (String suf : SUFFIXES) {
        if (w.length() > suf.length() + 1 && w.endsWith(suf)) {
          w = w.substring(0, w.length() - suf.length());
          break;
        }
      }
      if (w.length() >= 3 && "은는이가을를에의도로와과".indexOf(w.charAt(w.length() - 1)) >= 0) {
        w = w.substring(0, w.length() - 1);
      }
      if (w.length() == 1) out.add(w);
      for (int i = 0; i + 2 <= w.length(); i++) {
        String g = w.substring(i, i + 2);
        if (!STOP_GRAMS.contains(g)) out.add(g);
      }
    }
    return out;
  }
}
