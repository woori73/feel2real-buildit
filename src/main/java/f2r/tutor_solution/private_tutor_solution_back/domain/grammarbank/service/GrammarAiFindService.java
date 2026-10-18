package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto.GrammarAiFindResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto.GrammarQuestionItemResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarQuestionProfile.GrammarDifficulty;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.repository.GrammarTagRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.service.WikiAiRouterClient;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자연어 요청 → 필터 추출 → 실제 문법 문제은행 DB 검색.
 * AI(Wiki Router)가 응답하지 않으면 결정적 키워드 매핑으로 대체하고,
 * 어떤 경우에도 결과 문항은 DB 실조회만 반환한다(AI 창작 문항 금지).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GrammarAiFindService {

  private static final int DEFAULT_LIMIT = 30;
  private static final int MAX_LIMIT = 200;

  private final GrammarBankService bankService;
  private final GrammarTagRepository tagRepository;
  private final WikiAiRouterClient aiRouterClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public GrammarAiFindResponse find(AuthenticatedPrincipal principal, String request) {
    if (request == null || request.isBlank()) {
      throw new IllegalArgumentException("request가 필요합니다.");
    }
    FilterSet deterministic = parseDeterministic(request);
    FilterSet ai = tryAi(request);
    FilterSet used = ai != null ? ai : deterministic;
    boolean usedAi = ai != null;

    Page<GrammarQuestionItemResponse> page = bankService.search(
        new GrammarBankService.SearchFilters(used.schoolLevel, used.format, used.difficulty,
            null, null, null, null, used.search, used.tagIds),
        PageRequest.of(0, Math.max(1, Math.min(used.limit, MAX_LIMIT)),
            Sort.by(Sort.Direction.ASC, "profileId")));

    String answer = used.tagNames.isEmpty() && used.schoolLevel == null && used.search == null
        ? "요청에서 학년·문법 유형을 찾지 못해 전체 검색으로 대체했습니다. 실제 DB에서 "
            + page.getTotalElements() + "건을 찾았습니다."
        : String.join("·", describe(used)) + " 조건으로 실제 문제은행 DB에서 "
            + page.getTotalElements() + "건을 찾았습니다."
            + (usedAi ? "" : " (키워드 매핑 기반 — AI 미응답 시 결정적 분류)");
    return new GrammarAiFindResponse(usedAi, used.summary(), page.getTotalElements(),
        page.getContent(), answer);
  }

  private List<String> describe(FilterSet f) {
    List<String> out = new ArrayList<>();
    if (f.schoolLevel != null) {
      out.add(f.schoolLevel.labelKo());
    }
    out.addAll(f.tagNames);
    if (f.format != null) {
      out.add(f.format.name());
    }
    if (f.difficulty != null) {
      out.add(f.difficulty.name());
    }
    if (f.search != null) {
      out.add("'" + f.search + "'");
    }
    return out;
  }

  static class FilterSet {
    SchoolLevel schoolLevel;
    List<Long> tagIds = List.of();
    List<String> tagNames = List.of();
    GrammarQuestionFormat format;
    GrammarDifficulty difficulty;
    GrammarReviewStatus reviewStatus;
    String search;
    int limit = DEFAULT_LIMIT;

    String summary() {
      return String.format(
          "{\"schoolLevel\":\"%s\",\"tags\":[\"%s\"],\"format\":\"%s\",\"difficulty\":\"%s\",\"search\":\"%s\",\"limit\":%d}",
          schoolLevel, String.join("\",\"", tagNames), format, difficulty,
          search == null ? "" : search, limit);
    }
  }

  /** 결정적 키워드 매핑 — AI 없이도 "중2 수동태 객관식 30개" 수준의 요청을 해석한다. */
  FilterSet parseDeterministic(String request) {
    FilterSet f = new FilterSet();
    String r = request.toLowerCase();
    for (SchoolLevel level : List.of(SchoolLevel.M1, SchoolLevel.M2, SchoolLevel.M3,
        SchoolLevel.H1, SchoolLevel.H2, SchoolLevel.H3)) {
      String label = level.labelKo();
      String spaced = label.substring(0, 1) + " " + label.substring(1);
      if (r.contains(label.toLowerCase()) || r.contains(spaced)) {
        f.schoolLevel = level;
        break;
      }
    }
    if (f.schoolLevel == null && (r.contains("공통"))) {
      f.schoolLevel = SchoolLevel.COMMON;
    }
    List<GrammarTag> tags = tagRepository.findAllByOrderBySortOrderAsc();
    List<Long> tagIds = new ArrayList<>();
    List<String> tagNames = new ArrayList<>();
    for (GrammarTag tag : tags) {
      List<String> terms = new ArrayList<>();
      terms.add(tag.getNameKo());
      if (tag.getAliases() != null) {
        for (String a : tag.getAliases().split(",")) {
          if (!a.isBlank() && a.strip().length() >= 2) {
            terms.add(a.strip());
          }
        }
      }
      if (terms.stream().anyMatch(t -> r.contains(t.toLowerCase()))) {
        tagIds.add(tag.getTagId());
        tagNames.add(tag.getNameKo());
      }
    }
    f.tagIds = tagIds;
    f.tagNames = tagNames;
    f.format = GrammarQuestionFormat.fromRaw(pickFormatWord(r));
    if (r.contains("어려") || r.contains("하드") || r.contains("hard")) {
      f.difficulty = GrammarDifficulty.HARD;
    } else if (r.contains("쉬") || r.contains("이지") || r.contains("easy")) {
      f.difficulty = GrammarDifficulty.EASY;
    }
    java.util.regex.Matcher m = java.util.regex.Pattern
        .compile("([0-9]{1,3})\\s*(개|문항|문제|items?)").matcher(r);
    if (m.find()) {
      f.limit = Integer.parseInt(m.group(1));
    }
    return f;
  }

  private String pickFormatWord(String r) {
    if (r.contains("객관식")) {
      return "객관식";
    }
    if (r.contains("어법상 옳")) {
      return "옳은";
    }
    if (r.contains("어법상 틀")) {
      return "틀린";
    }
    if (r.contains("밑줄")) {
      return "밑줄";
    }
    if (r.contains("빈칸")) {
      return "빈칸";
    }
    if (r.contains("배열")) {
      return "배열";
    }
    if (r.contains("영작") || r.contains("서술")) {
      return "영작";
    }
    if (r.contains("전환")) {
      return "전환";
    }
    if (r.contains("오류") || r.contains("수정")) {
      return "오류";
    }
    return null;
  }

  FilterSet tryAi(String request) {
    String system = """
        너는 영어 문법 문제은행 검색 필터 추출기다. 사용자 요청에서 다음 JSON 하나만 출력한다(설명 문장 금지):
        {"schoolLevel":"M1|M2|M3|H1|H2|H3|COMMON|UNKNOWN|null","grammarTags":["문법 유형 한국어 이름"],
         "format":"MULTIPLE_CHOICE|CORRECT_SENTENCE|INCORRECT_SENTENCE|UNDERLINE|BLANK_FILL|SENTENCE_ORDER|WRITING|SENTENCE_TRANSFORM|ERROR_CORRECTION|null",
         "difficulty":"EASY|MEDIUM|HARD|null","search":"추가 키워드|null","limit":정수}
        grammarTags는 반드시 다음 목록의 한국어 이름만 사용한다: %s
        """;
    List<GrammarTag> tags = tagRepository.findAllByOrderBySortOrderAsc();
    List<String> names = tags.stream().map(GrammarTag::getNameKo).toList();
    WikiAiRouterClient.Completion completion =
        aiRouterClient.complete(system.formatted(String.join(", ", names)), request, 800);
    if (!completion.ok()) {
      return null;
    }
    try {
      String text = completion.text().trim();
      int start = text.indexOf('{');
      int end = text.lastIndexOf('}');
      if (start < 0 || end <= start) {
        return null;
      }
      JsonNode node = objectMapper.readTree(text.substring(start, end + 1));
      FilterSet f = new FilterSet();
      String level = node.path("schoolLevel").asText(null);
      if (level != null && !level.isBlank() && !"null".equals(level)) {
        f.schoolLevel = SchoolLevel.valueOf(level.strip().toUpperCase());
      }
      List<Long> tagIds = new ArrayList<>();
      List<String> tagNames = new ArrayList<>();
      for (JsonNode t : node.path("grammarTags")) {
        tags.stream().filter(g -> g.getNameKo().equals(t.asText())).findFirst().ifPresent(g -> {
          tagIds.add(g.getTagId());
          tagNames.add(g.getNameKo());
        });
      }
      f.tagIds = tagIds;
      f.tagNames = tagNames;
      String format = node.path("format").asText(null);
      if (format != null && !format.isBlank() && !"null".equals(format)) {
        f.format = GrammarQuestionFormat.valueOf(format.strip().toUpperCase());
      }
      String difficulty = node.path("difficulty").asText(null);
      if (difficulty != null && !difficulty.isBlank() && !"null".equals(difficulty)) {
        f.difficulty = GrammarDifficulty.valueOf(difficulty.strip().toUpperCase());
      }
      String search = node.path("search").asText(null);
      if (search != null && !search.isBlank() && !"null".equals(search)) {
        f.search = search.strip();
      }
      if (node.path("limit").isInt()) {
        f.limit = node.path("limit").asInt();
      }
      return f;
    } catch (Exception e) {
      return null;
    }
  }
}
