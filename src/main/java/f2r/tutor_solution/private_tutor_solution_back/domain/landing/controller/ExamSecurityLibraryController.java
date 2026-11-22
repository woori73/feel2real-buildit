package f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 공개 페이지 /exam-security — 부정행위 방지 기술(렌즈 찾기·종이 시험지 보안 무늬) 관련 논문·기술 문서·제품·언론 보도 모음과
 * F2R 개발 현황. 내용은 {@code content/exam-security/library.json} 한 파일에 있어 정기 갱신은 이 파일만 고친다
 * (docs/exam-security/LIBRARY_UPDATE.md). 다른 연구의 수치는 그 연구의 결과로만 적고 F2R 성능처럼 쓰지 않는다.
 */
@Controller
public class ExamSecurityLibraryController {

  static final String RESOURCE = "content/exam-security/library.json";

  /** checked: "원문"(F2R이 원문을 직접 열어 확인) 또는 "검색"(검색 요약으로만 확인 — 화면에 표시). */
  public record Entry(String kind, String title, String by, String date, String summary, String result, String url,
      String checked) {}

  public record Section(String id, String title, String lead, List<Entry> entries) {}

  public record Library(String updatedAt, String nextUpdate, List<String> f2rStatus, List<String> cautions,
      List<Section> sections) {}

  private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @GetMapping("/exam-security")
  public String page(Model model) throws IOException {
    model.addAttribute("lib", load());
    return "public/exam_security";
  }

  /** 홈 '부정행위 방지 기술' 영역의 최근 기사: 언론 보도 섹션에서 날짜가 늦은 순으로 n건. */
  public static List<Entry> latestNews(Library lib, int n) {
    return lib.sections().stream().filter(s -> "news".equals(s.id())).flatMap(s -> s.entries().stream())
        .sorted(Comparator.comparing(Entry::date, Comparator.nullsLast(Comparator.reverseOrder()))).limit(n).toList();
  }

  /** 홈 화면용. 자료 파일을 못 읽으면 빈 목록(홈 화면은 깨지지 않고 최근 기사 칸만 숨는다). */
  public List<Entry> latestNewsOrEmpty(int n) {
    try {
      return latestNews(load(), n);
    } catch (IOException | RuntimeException e) {
      return List.of();
    }
  }

  Library load() throws IOException {
    try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
      return mapper.readValue(in, Library.class);
    }
  }
}
