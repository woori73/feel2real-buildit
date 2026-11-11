package f2r.tutor_solution.private_tutor_solution_back.domain.academyhome;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 학생명단(시간표) 자료의 이름과 선택한 학원의 AcademyCraft 학생 명단을 대조한다(순수 계산, 쓰기 없음).
 *
 * <p>분류(행 = 자료 안의 서로 다른 이름 1개):
 * <ul>
 *   <li>EXISTING — 원장이 이 학원 재원 학생으로 확인·연결한 이름.
 *   <li>NEW — 같은 이름의 학생이 이 학원에 없다(재원·비활성 모두). 원장 승인 후 학생 등록 대상.
 *   <li>HOLD — 연결 보류. 이름이 같은 학생이 있거나(이름만으로는 같은 사람인지 알 수 없다), 연결한 학생이 더 이상 재원이 아니다.
 *   <li>EXCLUDED — 원장이 명단에서 제외했거나 이름 형식이 아니다.
 * </ul>
 * 원본 이름 수 = EXISTING + NEW + HOLD + EXCLUDED 가 항상 성립한다. 같은 이름이 여러 날짜에 나와도 한 행이다.
 */
public final class RosterReconciler {
  private RosterReconciler() {}

  public record Student(long id, String name, String grade, String schoolName, boolean active) {}
  public record Link(String name, String status, Long studentId) {}
  public record Row(String name, String status, String reason, int occurrences, Long linkedStudentId, List<Student> candidates) {}
  public record Report(int sourceEntries, int sourceNames, int existing, int added, int hold, int excluded, List<Row> rows) {
    public boolean balanced() { return existing + added + hold + excluded == sourceNames; }
  }

  static String key(String name) {
    return name == null ? "" : Normalizer.normalize(name, Normalizer.Form.NFC).strip().replaceAll("\\s+", " ");
  }

  static boolean nameLike(String name) {
    return name.matches("[가-힣A-Za-z][가-힣A-Za-z ]{0,19}");
  }

  public static Report reconcile(List<String> entryNames, List<Student> students, List<Link> links) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (String n : entryNames) counts.merge(key(n), 1, Integer::sum);
    Map<String, Link> linkByName = new LinkedHashMap<>();
    for (Link l : links) linkByName.put(key(l.name()), l);
    List<Row> rows = new ArrayList<>();
    int existing = 0, added = 0, hold = 0, excluded = 0;
    for (var e : counts.entrySet()) {
      String name = e.getKey();
      int n = e.getValue();
      Link link = linkByName.get(name);
      List<Student> same = students.stream().filter(s -> key(s.name()).equals(name)).toList();
      Row row;
      if (!nameLike(name)) row = new Row(name, "EXCLUDED", "이름 형식이 아닌 기록", n, null, List.of());
      else if (link != null && AcademyRosterStudentLink.EXCLUDED.equals(link.status())) row = new Row(name, "EXCLUDED", "원장이 명단에서 제외함", n, null, List.of());
      else if (link != null && AcademyRosterStudentLink.LINKED.equals(link.status())) {
        var target = students.stream().filter(s -> link.studentId() != null && s.id() == link.studentId()).findFirst();
        if (target.isPresent() && target.get().active()) row = new Row(name, "EXISTING", "원장이 연결한 재원 학생", n, link.studentId(), List.of(target.get()));
        else row = new Row(name, "HOLD", target.isPresent() ? "연결한 학생이 비활성 상태입니다" : "연결한 학생을 찾을 수 없습니다", n, link.studentId(), same);
      } else if (!same.isEmpty()) row = new Row(name, "HOLD", same.size() > 1 ? "이름이 같은 학생 " + same.size() + "명 — 누구인지 골라 주세요" : "이름이 같은 학생이 있습니다 — 같은 학생인지 확인해 주세요", n, null, same);
      else row = new Row(name, "NEW", "이 학원에 같은 이름의 학생이 없습니다", n, null, List.of());
      switch (row.status()) {
        case "EXISTING" -> existing++;
        case "NEW" -> added++;
        case "HOLD" -> hold++;
        default -> excluded++;
      }
      rows.add(row);
    }
    return new Report(entryNames.size(), counts.size(), existing, added, hold, excluded, rows);
  }
}
