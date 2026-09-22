package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.Category;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.entity.NewStartOfficialUpdate.SourceType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

/**
 * 신용회복위원회 공지사항 어댑터.
 * 2026-09-22 조사 결과 목록 페이지(/cms/com/index.do?MENU_ID=840) 자체는 빈 template 이고
 * 실제 데이터가 POST /ajaxf/frBoard/bbsViewList.do JSON 으로 내려오는 것이 확인되어 해당 API 를 사용한다.
 * JSON 응답에 CONTENTS(본문) 와 boardFileList(첨부) 가 포함되어 상세 HTML 조회가 불필요하다.
 */
@Component
@Slf4j
public class CcrsDiscoveryAdapter extends AbstractOfficialUpdateDiscoveryAdapter {

  static final String LIST_API_URL = "https://www.ccrs.or.kr/ajaxf/frBoard/bbsViewList.do";
  static final String DETAIL_URL =
      "https://www.ccrs.or.kr/cms/board/boardView.do?MENU_ID=840&SITE_NO=2&BOARD_SEQ=41&BBS_SEQ=%d";
  private static final String LIST_FORM_BODY =
      "pageNo=1&pagePerCnt=20&recordCountPerPage=20&MENU_ID=840&CONTENTS_NO=1&SITE_NO=2"
          + "&BOARD_SEQ=41&BBS_SEQ=&PWD=&SEARCH_SEQ=&SEARCH_FLD=&SEARCH=&START_DT=&END_DT=";

  private static final String[] RELEVANT_KEYWORDS = {
      "채무조정", "신용회복", "연체", "추심", "금융취약계층", "새출발기금",
      "채무조정제도", "개인워크아웃", "프리워크아웃", "채무자", "지원"
  };

  private final ObjectMapper objectMapper;

  public CcrsDiscoveryAdapter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public SourceType getSourceType() {
    return SourceType.CCRS;
  }

  @Override
  public String getSourceName() {
    return "신용회복위원회";
  }

  @Override
  protected List<DiscoveredCandidate> doDiscover(LocalDateTime since) {
    log.info("신용회복위원회 discovery 실행 (since={})", since);
    String json;
    try {
      json = httpClient.postForm(LIST_API_URL, LIST_FORM_BODY);
    } catch (Exception e) {
      throw new DiscoverySourceException(getSourceName(), DiscoverySourceException.Reason.FETCH_FAILED,
          "공지목록 API 조회 실패: " + e.getMessage(), e);
    }
    List<DiscoveredCandidate> all = parseListJson(json);

    List<DiscoveredCandidate> relevant = new ArrayList<>();
    for (DiscoveredCandidate candidate : all) {
      if (since != null && candidate.publishedAt() != null && candidate.publishedAt().isBefore(since)) {
        continue;
      }
      if (containsAny(candidate.title(), RELEVANT_KEYWORDS)
          || containsAny(candidate.summary(), RELEVANT_KEYWORDS)) {
        relevant.add(candidate);
      }
    }
    log.info("신용회복위원회 관련 후보 {}건 발견 (전체 {}건)", relevant.size(), all.size());
    return relevant;
  }

  List<DiscoveredCandidate> parseListJson(String json) {
    JsonNode root;
    try {
      root = objectMapper.readTree(json);
    } catch (Exception e) {
      throw new DiscoverySourceException(getSourceName(), DiscoverySourceException.Reason.PARSE_EMPTY_WITH_ITEMS,
          "공지목록 API 응답 JSON 파싱 실패: " + e.getMessage(), e);
    }
    JsonNode list = root.path("data").path("list");
    if (!list.isArray()) {
      throw new DiscoverySourceException(getSourceName(), DiscoverySourceException.Reason.PARSE_EMPTY_WITH_ITEMS,
          "공지목록 API 응답에 data.list 가 없음 (응답 형식 변경 가능성)");
    }
    if (list.isEmpty()) {
      JsonNode rowCount = root.path("data").path("recordCountPerPage");
      log.warn("신용회복위원회 공지목록 0건 응답 (recordCountPerPage={})", rowCount.asInt());
      return List.of();
    }

    List<DiscoveredCandidate> parsed = new ArrayList<>();
    for (JsonNode item : list) {
      if ("Y".equals(item.path("SECRET_YN").asText())) {
        continue;
      }
      long bbsSeq = item.path("BBS_SEQ").asLong();
      if (bbsSeq <= 0) {
        continue;
      }
      String subject = cleanText(item.path("SUBJECT").asText(""));
      if (subject.isBlank()) {
        continue;
      }
      String contents = cleanText(item.path("CONTENTS").asText(""));
      LocalDateTime publishedAt = parseLooseDate(item.path("WRITE_DT").asText(""));

      StringBuilder summary = new StringBuilder(
          contents.length() > 300 ? contents.substring(0, 300) + "..." : (contents.isBlank() ? subject : contents));
      List<String> attachments = readAttachments(item.path("boardFileList"));
      if (!attachments.isEmpty()) {
        summary.append(" [첨부: ").append(String.join("; ", attachments)).append("]");
      }

      parsed.add(new DiscoveredCandidate(
          subject,
          summary.toString(),
          String.format(DETAIL_URL, bbsSeq),
          "CCRS-BBS-" + bbsSeq,
          publishedAt,
          null,
          null,
          Category.NOTICE,
          item.path("CONTENTS").asText(null)));
    }

    if (parsed.isEmpty() && !list.isEmpty()) {
      throw new DiscoverySourceException(getSourceName(), DiscoverySourceException.Reason.PARSE_EMPTY_WITH_ITEMS,
          "API 목록 " + list.size() + "건 중 파싱 성공 0건 - 응답 필드 변경 가능성");
    }
    return parsed;
  }

  private List<String> readAttachments(JsonNode files) {
    List<String> attachments = new ArrayList<>();
    if (!files.isArray()) {
      return attachments;
    }
    for (JsonNode file : files) {
      String name = file.path("FILE_ORG_NM").asText("");
      if (name.isBlank()) {
        name = file.path("FILE_NM").asText("");
      }
      if (!name.isBlank()) {
        attachments.add(name + "|상세페이지 첨부");
      }
    }
    return attachments;
  }

  private static String cleanText(String html) {
    if (html == null || html.isBlank()) {
      return "";
    }
    return Jsoup.parse(html).text().trim();
  }

  private static boolean containsAny(String text, String... keywords) {
    if (text == null) return false;
    for (String kw : keywords) {
      if (text.contains(kw)) {
        return true;
      }
    }
    return false;
  }
}
