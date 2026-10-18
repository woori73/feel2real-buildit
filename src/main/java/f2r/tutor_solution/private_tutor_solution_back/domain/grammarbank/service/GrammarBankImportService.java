package f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto.GrammarImportRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.dto.GrammarImportResultResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.entity.GrammarQuestionProfile.GrammarDifficulty;
import f2r.tutor_solution.private_tutor_solution_back.domain.grammarbank.repository.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.entity.ProblemGeneratorMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.problemgenerator.repository.ProblemGeneratorMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Passage;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.Question;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.QuestionSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.entity.QuestionType;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.PassageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.question.repository.QuestionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.QuestionCategory;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.QuestionCategoryRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 2,824개 파일 추출 결과 등 구조화 문항 데이터를 문법 문제은행에 등록한다.
 * 중복 방지: 정규화 본문 해시 + (material, external_ref) + (material, source_position).
 * 저신뢰(0.60 미만)·태그 없음·본문 분리 실패는 NEEDS_REVIEW 보류함으로 넣는다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class GrammarBankImportService {

  private static final int ENGLISH_SUBJECT_ID = 1;
  private static final int MAX_ERRORS = 30;

  private final GrammarQuestionProfileRepository profileRepository;
  private final GrammarTagRepository tagRepository;
  private final QuestionGrammarTagRepository questionTagRepository;
  private final QuestionRepository questionRepository;
  private final PassageRepository passageRepository;
  private final SubjectRepository subjectRepository;
  private final QuestionCategoryRepository categoryRepository;
  private final ProblemGeneratorMaterialRepository materialRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public GrammarImportResultResponse importJson(AuthenticatedPrincipal principal,
      GrammarImportRequest request) {
    requireImporter(principal);
    if (request.records() == null || request.records().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "records 배열이 필요합니다.");
    }
    return run(principal, request.materialId(), request.defaultRightsStatus(), request.records());
  }

  public GrammarImportResultResponse importCsv(AuthenticatedPrincipal principal, Long materialId,
      String defaultRightsStatus, String csv) {
    requireImporter(principal);
    List<Map<String, Object>> records = parseCsv(csv);
    if (records.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV에서 데이터 행을 찾지 못했습니다.");
    }
    return run(principal, materialId, defaultRightsStatus, records);
  }

  private GrammarImportResultResponse run(AuthenticatedPrincipal principal, Long materialId,
      String defaultRightsStatus, List<Map<String, Object>> records) {
    ProblemGeneratorMaterial material = materialId != null
        ? materialRepository.findById(materialId).orElse(null) : null;
    if (materialId != null && material == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "material을 찾을 수 없습니다.");
    }
    boolean isNewBatch = material == null
        || profileRepository.countByMaterial_MaterialId(material.getMaterialId()) == 0;

    Subject subject = subjectRepository.findById(ENGLISH_SUBJECT_ID)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "영어 subject가 없습니다."));
    QuestionCategory grammarCategory = categoryRepository.findByCategoryNameKo("문법").orElse(null);
    List<GrammarTag> allTags = tagRepository.findAllByOrderBySortOrderAsc();

    int created = 0, duplicates = 0, held = 0, failed = 0;
    List<String> errors = new ArrayList<>();
    Map<String, Passage> passageCache = new LinkedHashMap<>();
    int orderSeq = 1;

    for (int i = 0; i < records.size(); i++) {
      try {
        Outcome outcome = importOne(principal, material, subject, grammarCategory, allTags,
            defaultRightsStatus, records.get(i), orderSeq++, passageCache);
        switch (outcome) {
          case CREATED -> created++;
          case DUPLICATE -> duplicates++;
          case HELD -> {
            created++;
            held++;
          }
        }
      } catch (RuntimeException e) {
        failed++;
        if (errors.size() < MAX_ERRORS) {
          errors.add("record[" + i + "]: " + e.getMessage());
        }
      }
    }
    return new GrammarImportResultResponse(records.size(), created, duplicates, held, failed,
        isNewBatch, errors);
  }

  private enum Outcome { CREATED, DUPLICATE, HELD }

  private Outcome importOne(AuthenticatedPrincipal principal, ProblemGeneratorMaterial material,
      Subject subject, QuestionCategory grammarCategory, List<GrammarTag> allTags,
      String defaultRightsStatus, Map<String, Object> record, int orderSeq,
      Map<String, Passage> passageCache) {
    String stem = firstText(record, "stem", "question_text", "question", "text", "발문");
    if (stem == null || stem.isBlank()) {
      throw new IllegalArgumentException("stem/question_text 필수");
    }
    String externalRef = firstText(record, "external_ref", "externalRef", "ref");
    String sourcePosition = resolvePosition(record);
    String contentHash = sha256(normalizeForHash(stem));

    // 중복 방지 1: 정규화 본문 해시 (동일 문항)
    if (profileRepository.findByContentHash(contentHash).isPresent()) {
      return Outcome.DUPLICATE;
    }
    // 중복 방지 2: 같은 파일 재등록 (external_ref / source_position)
    if (material != null) {
      if (externalRef != null && profileRepository
          .findByMaterial_MaterialIdAndExternalRef(material.getMaterialId(), externalRef).isPresent()) {
        return Outcome.DUPLICATE;
      }
      if (sourcePosition != null && profileRepository
          .findByMaterial_MaterialIdAndSourcePosition(material.getMaterialId(), sourcePosition).isPresent()) {
        return Outcome.DUPLICATE;
      }
    }

    List<String> choices = firstList(record, "choices", "options");
    String answer = firstText(record, "answer", "correct_answer", "correctAnswer", "정답");
    String explanation = firstText(record, "explanation", "commentary", "solution", "해설");

    // 자동 분류: 외부 분류 우선, 없으면 문법 용어 등장 매칭(용어 등장≠실제 평가 유형이므로 신뢰도 깎기)
    Classification cls = classify(record, stem, choices, allTags);
    SchoolLevel level = parseSchoolLevel(firstText(record, "school_level", "schoolLevel", "grade", "학년"));
    GrammarQuestionFormat format = GrammarQuestionFormat
        .fromRaw(firstText(record, "format", "question_format", "q_kind", "type", "형식"));
    GrammarDifficulty difficulty = parseDifficulty(firstText(record, "difficulty", "level", "난이도"));
    String keywords = firstText(record, "keywords", "검색 키워드");
    GrammarRightsStatus rights = parseRights(
        firstText(record, "rights_status", "rightsStatus") != null
            ? firstText(record, "rights_status", "rightsStatus") : defaultRightsStatus);

    Passage passage = resolvePassage(record, subject, material, passageCache);

    Question question = questionRepository.save(Question.builder()
        .subject(subject)
        .category(grammarCategory)
        .passage(passage)
        .questionType(!choices.isEmpty() || "MULTIPLE_CHOICE".equals(format == null ? null : format.name())
            ? QuestionType.MULTIPLE_CHOICE : QuestionType.SHORT_ANSWER)
        .questionText(stem)
        .choices(choices.isEmpty() ? null : toJson(choices))
        .correctAnswer(answer)
        .explanation(explanation)
        .source(QuestionSource.READFILE)
        .orderNum(orderSeq)
        .questionLabel(numericLabel(sourcePosition))
        .build());

    BigDecimal confidence = cls.confidence();
    GrammarReviewStatus reviewStatus = (confidence == null || cls.tags().isEmpty()
        || confidence.compareTo(GrammarBankService.reviewThreshold()) < 0)
        ? GrammarReviewStatus.NEEDS_REVIEW : GrammarReviewStatus.AUTO;

    GrammarQuestionProfile profile = GrammarQuestionProfile.builder()
        .question(question)
        .schoolLevel(level)
        .questionFormat(format)
        .difficulty(difficulty)
        .keywords(keywords != null ? keywords : String.join(" ", cls.matchedNames()))
        .autoClassification(cls.raw() == null ? null : toJson(cls.raw()))
        .classificationConfidence(confidence)
        .reviewStatus(reviewStatus)
        .rightsStatus(rights != null ? rights : GrammarRightsStatus.UNKNOWN)
        .contentHash(contentHash)
        .material(material)
        .sourcePosition(sourcePosition)
        .externalRef(externalRef)
        .ownerUserId(principal.userId())
        .build();
    profileRepository.save(profile);

    for (MatchedTag mt : cls.tags()) {
      questionTagRepository.save(QuestionGrammarTag.builder()
          .question(question)
          .tag(mt.tag())
          .primary(mt.primary())
          .auto(true)
          .confidence(mt.confidence())
          .build());
    }
    return reviewStatus == GrammarReviewStatus.NEEDS_REVIEW ? Outcome.HELD : Outcome.CREATED;
  }

  // ---------- 자동 분류 ----------

  record MatchedTag(GrammarTag tag, boolean primary, BigDecimal confidence) {
  }

  record Classification(List<MatchedTag> tags, BigDecimal confidence, Map<String, Object> raw,
      List<String> matchedNames, boolean fromExternal) {
  }

  Classification classify(Map<String, Object> record, String stem, List<String> choices,
      List<GrammarTag> allTags) {
    List<String> external = firstListString(record, "grammar_tags", "grammarTags", "tags", "문법 유형");
    BigDecimal given = firstDecimal(record, "confidence", "classification_confidence");
    if (external != null && !external.isEmpty()) {
      List<MatchedTag> tags = new ArrayList<>();
      Map<String, GrammarTag> index = new LinkedHashMap<>();
      for (GrammarTag t : allTags) {
        index.put(t.getNameKo(), t);
        if (t.getAliases() != null) {
          for (String alias : t.getAliases().split(",")) {
            index.putIfAbsent(alias.strip().toLowerCase(), t);
          }
        }
      }
      int i = 0;
      for (String name : external) {
        GrammarTag tag = index.get(name.strip());
        if (tag == null) {
          tag = index.get(name.strip().toLowerCase());
        }
        if (tag == null) {
          tag = allTags.stream().filter(t -> t.getNameKo().contains(name) || name.contains(t.getNameKo()))
              .findFirst().orElse(null);
        }
        if (tag != null) {
          tags.add(new MatchedTag(tag, i == 0,
              given != null ? given : new BigDecimal("0.80")));
          i++;
        }
      }
      BigDecimal confidence = given != null ? given : (tags.isEmpty() ? null : new BigDecimal("0.80"));
      return new Classification(tags, confidence, null,
          tags.stream().map(t -> t.tag().getNameKo()).toList(), true);
    }
    // 용어 등장 기반 1차 자동 분류
    String haystack = (stem + " " + String.join(" ", choices)).toLowerCase();
    List<MatchedTag> hits = new ArrayList<>();
    List<String> names = new ArrayList<>();
    for (GrammarTag tag : allTags) {
      if ("기타 문법".equals(tag.getNameKo())) {
        continue;
      }
      List<String> terms = new ArrayList<>();
      terms.add(tag.getNameKo().toLowerCase());
      if (tag.getAliases() != null) {
        for (String alias : tag.getAliases().split(",")) {
          String a = alias.strip().toLowerCase();
          if (!a.isEmpty()) {
            terms.add(a);
          }
        }
      }
      int count = 0;
      for (String term : terms) {
        int idx = haystack.indexOf(term);
        while (idx >= 0) {
          count++;
          idx = haystack.indexOf(term, idx + term.length());
        }
      }
      if (count > 0) {
        BigDecimal conf = BigDecimal.valueOf(Math.min(0.9, 0.4 + 0.1 * count))
            .setScale(2, RoundingMode.HALF_UP);
        hits.add(new MatchedTag(tag, hits.isEmpty(), conf));
        names.add(tag.getNameKo());
      }
    }
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("method", "term-appearance");
    raw.put("matched", names);
    raw.put("note", "문법에 등장하는 용어 기준 1차 분류 — 실제 평가 유형과 다를 수 있어 검수 필요");
    if (hits.isEmpty()) {
      GrammarTag etc = allTags.stream()
          .filter(t -> "기타 문법".equals(t.getNameKo())).findFirst().orElse(null);
      if (etc != null) {
        hits.add(new MatchedTag(etc, true, new BigDecimal("0.30")));
      }
    }
    BigDecimal overall = hits.stream().map(MatchedTag::confidence)
        .max(BigDecimal::compareTo).orElse(null);
    return new Classification(hits, overall, raw, names, false);
  }

  // ---------- 파싱 헬퍼 ----------

  static String firstText(Map<String, Object> map, String... keys) {
    for (String key : keys) {
      Object v = map.get(key);
      if (v != null && !String.valueOf(v).isBlank()) {
        return String.valueOf(v).strip();
      }
    }
    return null;
  }

  static BigDecimal firstDecimal(Map<String, Object> map, String... keys) {
    for (String key : keys) {
      Object v = map.get(key);
      if (v instanceof Number n) {
        return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
      }
      if (v != null) {
        try {
          return new BigDecimal(String.valueOf(v).strip()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ignored) {
          // 다음 키
        }
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  static List<String> firstList(Map<String, Object> map, String... keys) {
    for (String key : keys) {
      Object v = map.get(key);
      if (v instanceof List<?> list) {
        List<String> out = new ArrayList<>();
        for (Object o : list) {
          if (o instanceof Map<?, ?> m) {
            Object text;
            if (m.containsKey("text")) {
              text = m.get("text");
            } else if (m.containsKey("label")) {
              text = m.get("label");
            } else {
              text = m.values().stream().map(String::valueOf).findFirst().orElse("");
            }
            out.add(String.valueOf(text));
          } else if (o != null) {
            out.add(String.valueOf(o));
          }
        }
        return out;
      }
      if (v instanceof String s && !s.isBlank()) {
        try {
          return new ObjectMapper().readValue(s, List.class);
        } catch (Exception e) {
          return List.of(s.split("\\s*[/|]\\s*"));
        }
      }
    }
    return List.of();
  }

  static List<String> firstListString(Map<String, Object> map, String... keys) {
    for (String key : keys) {
      Object v = map.get(key);
      if (v instanceof List<?> list) {
        List<String> out = new ArrayList<>();
        for (Object o : list) {
          if (o instanceof Map<?, ?> m) {
            Object name;
            if (m.containsKey("name")) {
              name = m.get("name");
            } else if (m.containsKey("tag")) {
              name = m.get("tag");
            } else {
              name = m.get("name_ko");
            }
            if (name != null) {
              out.add(String.valueOf(name));
            }
          } else if (o != null) {
            out.add(String.valueOf(o));
          }
        }
        return out;
      }
      if (v instanceof String s && !s.isBlank()) {
        return List.of(s.split("[,;/]\\s*"));
      }
    }
    return null;
  }

  static String resolvePosition(Map<String, Object> record) {
    String pos = firstText(record, "source_position", "sourcePosition", "position");
    if (pos != null) {
      return pos;
    }
    String page = firstText(record, "page", "페이지");
    String qno = firstText(record, "question_number", "questionNumber", "문항번호", "no");
    if (page != null || qno != null) {
      return "p" + (page != null ? page : "?") + "-q" + (qno != null ? qno : "?");
    }
    return null;
  }

  static SchoolLevel parseSchoolLevel(String raw) {
    if (raw == null) {
      return SchoolLevel.UNKNOWN;
    }
    String v = raw.strip().toUpperCase();
    return switch (v) {
      case "중1", "M1", "MIDDLE_1", "1학년(중)" -> SchoolLevel.M1;
      case "중2", "M2", "MIDDLE_2" -> SchoolLevel.M2;
      case "중3", "M3", "MIDDLE_3" -> SchoolLevel.M3;
      case "고1", "H1", "HIGH_1" -> SchoolLevel.H1;
      case "고2", "H2", "HIGH_2" -> SchoolLevel.H2;
      case "고3", "H3", "HIGH_3" -> SchoolLevel.H3;
      case "공통", "COMMON" -> SchoolLevel.COMMON;
      default -> {
        if (v.startsWith("중1")) {
          yield SchoolLevel.M1;
        } else if (v.startsWith("중2")) {
          yield SchoolLevel.M2;
        } else if (v.startsWith("중3")) {
          yield SchoolLevel.M3;
        } else if (v.startsWith("고1")) {
          yield SchoolLevel.H1;
        } else if (v.startsWith("고2")) {
          yield SchoolLevel.H2;
        } else if (v.startsWith("고3")) {
          yield SchoolLevel.H3;
        }
        yield SchoolLevel.UNKNOWN;
      }
    };
  }

  static GrammarDifficulty parseDifficulty(String raw) {
    if (raw == null) {
      return null;
    }
    String v = raw.strip().toUpperCase();
    if (v.contains("쉬") || v.contains("EASY") || v.equals("1") || v.equals("2")) {
      return GrammarDifficulty.EASY;
    }
    if (v.contains("어렵") || v.contains("_HARD") || v.contains("HARD") || v.equals("4") || v.equals("5")) {
      return GrammarDifficulty.HARD;
    }
    if (v.contains("중") || v.contains("MEDIUM") || v.equals("3")) {
      return GrammarDifficulty.MEDIUM;
    }
    return null;
  }

  static GrammarRightsStatus parseRights(String raw) {
    if (raw == null) {
      return null;
    }
    try {
      return GrammarRightsStatus.valueOf(raw.strip().toUpperCase());
    } catch (IllegalArgumentException e) {
      return GrammarRightsStatus.UNKNOWN;
    }
  }

  static String numericLabel(String sourcePosition) {
    if (sourcePosition == null) {
      return null;
    }
    String digits = sourcePosition.replaceAll("[^0-9]", "");
    return digits.isEmpty() ? null : digits;
  }

  private Passage resolvePassage(Map<String, Object> record, Subject subject,
      ProblemGeneratorMaterial material, Map<String, Passage> cache) {
    String content = firstText(record, "passage", "passage_content", "passageContent", "지문");
    if (content == null) {
      return null;
    }
    String key = sha256(normalizeForHash(content));
    Passage cached = cache.get(key);
    if (cached != null) {
      return cached;
    }
    Passage passage = passageRepository.save(Passage.builder()
        .subject(subject)
        .title(Optional.ofNullable(firstText(record, "passage_title", "passageTitle")).orElse(
            material != null ? material.getOriginalFileName() : "수입 지문"))
        .content(content)
        .sourceOrigin(material != null ? material.getOriginalFileName() : "grammar-bank-import")
        .build());
    cache.put(key, passage);
    return passage;
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("JSON 직렬화 실패");
    }
  }

  static String normalizeForHash(String text) {
    return text.toLowerCase().replaceAll("[\\s\\p{Punct}]+", "").replaceAll("[·—–…]", "");
  }

  static String sha256(String text) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** 따옴표 지원 CSV 파싱 (header → Map 행). */
  public static List<Map<String, Object>> parseCsv(String csv) {
    List<List<String>> rows = new ArrayList<>();
    List<String> current = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean inQuotes = false;
    String text = csv.replace("\r\n", "\n").replace("\r", "\n");
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (inQuotes) {
        if (c == '"') {
          if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
            field.append('"');
            i++;
          } else {
            inQuotes = false;
          }
        } else {
          field.append(c);
        }
      } else if (c == '"') {
        inQuotes = true;
      } else if (c == ',') {
        current.add(field.toString());
        field.setLength(0);
      } else if (c == '\n') {
        current.add(field.toString());
        field.setLength(0);
        if (current.stream().anyMatch(s -> !s.isBlank())) {
          rows.add(new ArrayList<>(current));
        }
        current.clear();
      } else {
        field.append(c);
      }
    }
    if (field.length() > 0 || current.stream().anyMatch(s -> !s.isBlank())) {
      current.add(field.toString());
      if (current.stream().anyMatch(s -> !s.isBlank())) {
        rows.add(current);
      }
    }
    if (rows.size() < 2) {
      return List.of();
    }
    List<String> header = rows.get(0).stream().map(h -> h.strip().replace("\uFEFF", "")).toList();
    List<Map<String, Object>> out = new ArrayList<>();
    for (int r = 1; r < rows.size(); r++) {
      List<String> row = rows.get(r);
      Map<String, Object> map = new LinkedHashMap<>();
      for (int c = 0; c < header.size(); c++) {
        String value = c < row.size() ? row.get(c).strip() : "";
        if (!value.isEmpty()) {
          map.put(header.get(c), value);
        }
      }
      out.add(map);
    }
    return out;
  }

  private void requireImporter(AuthenticatedPrincipal principal) {
    if (!Role.ADMIN.name().equals(principal.role()) && !Role.TUTOR.name().equals(principal.role())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "문제은행 수입은 TUTOR/ADMIN만 가능합니다.");
    }
  }
}
