package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.Category;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * 새출발기금 공식 FAQ(newstartfund.or.kr/Faq/List.do) 어댑터.
 * Q 항목(h3[id]) 과 답변(div[id]_1) 이 서버 렌더링 HTML 로 포함되어 있어
 * 상세 페이지 추가 조회 없이 상세本文까지 확보할 수 있다.
 */
@Component
@Slf4j
public class NewStartFundFaqDiscoveryAdapter extends AbstractOfficialUpdateDiscoveryAdapter {

  static final String FAQ_LIST_URL = "https://www.newstartfund.or.kr/Faq/List.do?pageIndex=%d&schPrcdRnk=%s";
  private static final List<String> CATEGORY_CODES = List.of("001", "002", "003", "004", "005");
  private static final int MAX_PAGES_PER_CATEGORY = 20;
  private static final Pattern ID_PATTERN = Pattern.compile("^(\\d+)$");

  @Override
  public SourceType getSourceType() {
    return SourceType.NEWSTART_FUND;
  }

  @Override
  public String getSourceName() {
    return "새출발기금 공식 FAQ";
  }

  @Override
  protected List<DiscoveredCandidate> doDiscover(LocalDateTime since) {
    List<DiscoveredCandidate> candidates = new ArrayList<>();
    Set<Long> seenIdx = new LinkedHashSet<>();
    log.info("새출발기금 공식 FAQ discovery 실행 (since={})", since);

    for (String category : CATEGORY_CODES) {
      for (int pageIndex = 1; pageIndex <= MAX_PAGES_PER_CATEGORY; pageIndex++) {
        String url = String.format(FAQ_LIST_URL, pageIndex, category);
        String html = fetchHtmlRequired(url, 3);
        List<DiscoveredCandidate> page = parseFaqPage(html, "https://www.newstartfund.or.kr/" + url);
        if (page.isEmpty()) {
          break;
        }
        for (DiscoveredCandidate candidate : page) {
          Matcher idm = ID_PATTERN.matcher(candidate.officialDocumentId().replace("NSF-FAQ-", ""));
          if (idm.find() && seenIdx.add(Long.parseLong(idm.group(1)))) {
            candidates.add(candidate);
          }
        }
      }
    }

    if (seenIdx.isEmpty()) {
      throw new DiscoverySourceException(getSourceName(), DiscoverySourceException.Reason.PARSE_EMPTY_WITH_ITEMS,
          "FAQ 카테고리 5종 전체에서 Q 항목 0건 - 새출발기금 FAQ 는 항상 항목이 존재하므로 셀렉터/사이트 점검 필요");
    }

    log.info("새출발기금 공식 FAQ 후보 {}건 발견", candidates.size());
    return candidates;
  }

  List<DiscoveredCandidate> parseFaqPage(String html, String pageUrl) {
    Document doc = Jsoup.parse(html);
    List<DiscoveredCandidate> parsed = new ArrayList<>();
    int structuralQuestions = 0;

    for (Element h3 : doc.select("h3[id]")) {
      if (h3.selectFirst("span.num") == null || h3.selectFirst("i") == null) {
        continue;
      }
      structuralQuestions++;
      Matcher m = ID_PATTERN.matcher(h3.attr("id"));
      if (!m.matches()) {
        continue;
      }
      long idx = Long.parseLong(m.group(1));
      String title = h3.selectFirst("i").text().trim();
      if (title.isBlank()) {
        continue;
      }

      Element answerEl = doc.getElementById(idx + "_1");
      String answer = answerEl != null ? answerEl.text().trim() : "";
      String summary = answer.length() > 300 ? answer.substring(0, 300) + "..." : answer;
      if (summary.isBlank()) {
        summary = title;
      }

      parsed.add(new DiscoveredCandidate(
          title,
          summary,
          pageUrl + "#" + idx,
          "NSF-FAQ-" + idx,
          null,
          null,
          null,
          Category.ELIGIBILITY,
          answerEl != null ? answerEl.html() : null));
    }

    if (structuralQuestions > 0 && parsed.isEmpty()) {
      throw new DiscoverySourceException(getSourceName(), DiscoverySourceException.Reason.PARSE_EMPTY_WITH_ITEMS,
          "FAQ Q 항목 " + structuralQuestions + "건 중 파싱 성공 0건");
    }
    return parsed;
  }
}
