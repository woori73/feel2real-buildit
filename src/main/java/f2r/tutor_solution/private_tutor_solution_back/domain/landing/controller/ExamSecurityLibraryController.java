package f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
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

  Library load() throws IOException {
    try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
      return mapper.readValue(in, Library.class);
    }
  }
}
