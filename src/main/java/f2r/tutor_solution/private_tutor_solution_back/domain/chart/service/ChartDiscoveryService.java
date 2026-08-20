package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartActivityResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartCandidateResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartDiscoveryRunResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartCandidate;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartCandidateActivity;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartCandidateStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartDiscoverySettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartDiscoveryZone;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartEducator;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartVerificationStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.repository.ChartCandidateActivityRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.repository.ChartCandidateRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.repository.ChartDiscoverySettingsRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.repository.ChartEducatorRepository;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChartDiscoveryService {

  private static final Pattern JSON_FENCE = Pattern.compile("^```(?:json)?\\s*|\\s*```$", Pattern.CASE_INSENSITIVE);
  private static final Set<String> VALID_ROLE_WORDS = Set.of("원장", "대표", "강사", "선생님");
  private static final Set<String> NAME_STOPWORDS = Set.of(
      "안내", "문구", "교육", "영어", "학원", "강남", "선생", "선생님", "대표", "원장", "강사",
      "상담", "문의", "등록", "수강", "이벤트", "혜택", "할인", "무료", "추천", "비교", "순위",
      "베스트", "정보", "소식", "후기", "리뷰", "모집", "신청", "학부모", "학생", "수업", "커리큘럼",
      "전문", "전문가", "교사", "직원", "담당자", "관리자", "초등부", "중등부", "고등부", "원어민", "채용");
  private static final String COMMON_KOREAN_SURNAMES =
      "김이박최정강조윤장임한오서신권황안송류전홍고문양손배백허남심노하곽성차주우구민유진지엄채원천방공현함변염여편경금옥육인맹제모동길표계태석온위탁국사설갈선용예봉관형";
  private static final List<String> HARD_EXCLUDE_TERMS = List.of(
      "메가스터디", "대성마이맥", "이투스", "EBS", "인강", "스타강사", "인터넷 강의", "채용", "구인", "프랜차이즈");

  private final ChartDiscoverySettingsRepository settingsRepository;
  private final ChartCandidateRepository candidateRepository;
  private final ChartCandidateActivityRepository activityRepository;
  private final ChartEducatorRepository educatorRepository;
  private final ChartPromptFactory promptFactory;
  private final ChartGeminiClient geminiClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Transactional
  public ChartDiscoverySettings getOrCreateSettings() {
    return settingsRepository.findById(ChartDiscoverySettings.SINGLETON_ID)
        .orElseGet(() -> settingsRepository.save(ChartDiscoverySettings.defaults()));
  }

  @Transactional
  public ChartDiscoverySettings updateSettings(String zoneId, String includeActivities, String excludedTargets,
      int candidateLimit, boolean deepSearchEnabled, String extraInstruction) {
    ChartDiscoverySettings settings = getOrCreateSettings();
    settings.update(zoneId, includeActivities, excludedTargets, candidateLimit, deepSearchEnabled, extraInstruction);
    return settings;
  }

  @Transactional
  public ChartDiscoverySettings restoreDefaultSettings() {
    ChartDiscoverySettings settings = getOrCreateSettings();
    settings.restoreDefaults();
    return settings;
  }

  public List<ChartCandidateResponse> listCandidates(String zoneId) {
    ChartDiscoveryZone.fromId(zoneId);
    List<ChartCandidate> candidates = candidateRepository.findByZoneId(zoneId, Sort.by(Sort.Direction.DESC, "candidateId"));
    return candidates.stream().map(this::toResponse).toList();
  }

  @Transactional
  public ChartCandidateResponse approve(Long candidateId) {
    ChartCandidate candidate = candidateRepository.findById(candidateId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "후보를 찾을 수 없습니다."));
    candidate.approve();
    LocalDateTime now = LocalDateTime.now();
    educatorRepository.findByPersonKey(candidate.getPersonKey())
        .ifPresentOrElse(existing -> existing.refreshFrom(candidate),
            () -> educatorRepository.save(ChartEducator.from(candidate, now)));
    return toResponse(candidate);
  }

  @Transactional
  public ChartCandidateResponse reject(Long candidateId) {
    ChartCandidate candidate = candidateRepository.findById(candidateId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "후보를 찾을 수 없습니다."));
    candidate.reject();
    return toResponse(candidate);
  }

  @Transactional
  public ChartDiscoveryRunResponse runDiscovery() {
    ChartDiscoverySettings settings = getOrCreateSettings();
    ChartDiscoveryZone zone = ChartDiscoveryZone.fromId(settings.getSearchZone());
    if (!zone.isEnabledForDiscovery()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이번 테스트는 압구정·신사 검색권역만 실행할 수 있습니다.");
    }
    if (!geminiClient.isReady()) {
      return empty(settings, zone, "GEMINI_API_KEY 환경변수를 설정하면 Google Discovery V2를 실행할 수 있습니다.");
    }

    String prompt = promptFactory.contentDiscoveryPrompt(zone, settings);
    ChartGeminiClient.GroundedResponse content = geminiClient.generateGrounded(prompt);
    if (!content.ok()) {
      return empty(settings, zone, content.errorCode() + ": " + content.errorMessage());
    }

    List<JsonNode> raw = parseArray(content.text()).stream().limit(settings.getCandidateLimit()).toList();
    MergeResult merge = mergeDuplicateCandidates(raw);
    int skippedExisting = 0;
    List<DraftCandidate> drafts = new ArrayList<>();
    List<ChartDiscoveryRunResponse.RejectedCandidate> excluded = new ArrayList<>();
    int deepCalls = 0;
    List<String> webQueries = new ArrayList<>(content.webSearchQueries());

    for (DraftCandidate draft : merge.drafts()) {
      if (educatorRepository.existsByPersonKey(draft.personKey())) {
        skippedExisting++;
        continue;
      }
      Classified initial = classify(draft, content.groundingChunks());
      if (initial.status().equals("excluded")) {
        excluded.add(new ChartDiscoveryRunResponse.RejectedCandidate(draft.name(), draft.role(), draft.academyName(), draft.sourceUrl(), initial.reason()));
        continue;
      }
      if (settings.isDeepSearchEnabled()) {
        String deepPrompt = promptFactory.deepSearchPrompt(draft.name(), draft.role(), draft.academyName(), zone, settings);
        ChartGeminiClient.GroundedResponse deep = geminiClient.generateGrounded(deepPrompt);
        deepCalls++;
        if (deep.ok()) {
          webQueries.addAll(deep.webSearchQueries());
          applyDeepSearch(draft, deep);
        } else {
          draft.deepSearchResult("Deep Search 실패: " + deep.errorCode() + " " + deep.errorMessage());
          draft.verificationStatus(ChartVerificationStatus.REVIEW_REQUIRED);
          draft.exclusionCheck("Deep Search 실패로 관리자 확인 필요");
        }
      }
      Classified afterDeep = classify(draft, content.groundingChunks());
      if (afterDeep.status().equals("excluded")) {
        excluded.add(new ChartDiscoveryRunResponse.RejectedCandidate(draft.name(), draft.role(), draft.academyName(), draft.sourceUrl(), afterDeep.reason()));
      } else {
        drafts.add(draft);
      }
    }

    List<ChartCandidateResponse> saved = saveDrafts(zone, prompt, drafts);
    int verified = (int) saved.stream().filter(c -> c.verificationStatus() == ChartVerificationStatus.VERIFIED).count();
    int review = saved.size() - verified;
    return new ChartDiscoveryRunResponse(true, null, geminiClient.model(), zone.getId(), zone.getLabel(),
        1 + deepCalls, 1, deepCalls, raw.size(), merge.duplicatesMergedCount(), skippedExisting,
        verified, review, excluded.size(), saved.size(), distinct(webQueries), saved, excluded);
  }

  private ChartDiscoveryRunResponse empty(ChartDiscoverySettings settings, ChartDiscoveryZone zone, String message) {
    return new ChartDiscoveryRunResponse(false, message, geminiClient.model(), zone.getId(), zone.getLabel(),
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of());
  }

  private List<ChartCandidateResponse> saveDrafts(ChartDiscoveryZone zone, String prompt, List<DraftCandidate> drafts) {
    LocalDateTime now = LocalDateTime.now();
    List<ChartCandidateResponse> saved = new ArrayList<>();
    for (DraftCandidate draft : drafts) {
      String hash = sha256(draft.sourceUrl());
      ChartCandidate candidate = candidateRepository.findByZoneIdAndPersonKeyAndSourceUrlHash(zone.getId(), draft.personKey(), hash)
          .orElseGet(() -> candidateRepository.save(ChartCandidate.discovered(zone.getId(), draft.personKey(), draft.name(), draft.role(),
              draft.academyName(), draft.activityRegion(), draft.contentType(), draft.contentSummary(), draft.sourceUrl(), draft.sourceTitle(),
              draft.discoveryReason(), draft.deepSearchResult(), draft.exclusionCheck(), draft.verificationStatus(), left(prompt, 500), now)));
      candidate.refresh(draft.role(), draft.academyName(), draft.contentType(), draft.contentSummary(), draft.sourceTitle(),
          draft.discoveryReason(), draft.deepSearchResult(), draft.exclusionCheck(), draft.verificationStatus(), left(prompt, 500), now);
      for (ActivityDraft activity : draft.activities()) {
        if (activity.sourceUrl() == null || activity.sourceUrl().isBlank()) continue;
        if (!activityRepository.existsByCandidateAndSourceUrl(candidate, activity.sourceUrl())) {
          activityRepository.save(ChartCandidateActivity.of(candidate, nvl(activity.contentType(), "미상"), nvl(activity.contentSummary(), ""), activity.sourceUrl(), now));
        }
      }
      saved.add(toResponse(candidate));
    }
    return saved;
  }

  private void applyDeepSearch(DraftCandidate draft, ChartGeminiClient.GroundedResponse deep) {
    JsonNode obj = parseObject(deep.text());
    List<ActivityDraft> grounded = new ArrayList<>();
    for (JsonNode item : obj.path("activities")) {
      String url = text(item, "sourceUrl");
      if (isSourceGrounded(url, deep.groundingChunks())) {
        grounded.add(new ActivityDraft(nvl(text(item, "contentType"), "미상"), text(item, "contentSummary"), url));
      }
    }
    draft.activities().addAll(grounded);
    Map<String, Object> compact = new LinkedHashMap<>();
    compact.put("currentRoleConfirmed", obj.path("currentRoleConfirmed").asBoolean(false));
    compact.put("currentAcademyName", text(obj, "currentAcademyName"));
    compact.put("activityCount", grounded.size());
    draft.deepSearchResult(writeJson(compact));
    if (text(obj, "currentAcademyName") != null && draft.academyName() == null) {
      draft.academyName(text(obj, "currentAcademyName"));
    }
    if (!obj.path("currentRoleConfirmed").asBoolean(false) || grounded.isEmpty()) {
      draft.verificationStatus(ChartVerificationStatus.REVIEW_REQUIRED);
      draft.exclusionCheck("Deep Search에서 현재 역할/추가 활동 근거가 충분히 확인되지 않아 관리자 확인 필요");
    }
    if (obj.path("isLargeFranchiseSignal").asBoolean(false) || obj.path("isMultiSiteSignal").asBoolean(false) || obj.path("isOnlineOnlySignal").asBoolean(false)) {
      draft.verificationStatus(ChartVerificationStatus.REVIEW_REQUIRED);
      draft.exclusionCheck("Deep Search에서 대형/다지점/인강 중심 가능성이 감지되어 관리자 확인 필요");
    }
  }

  private Classified classify(DraftCandidate draft, List<ChartGeminiClient.GroundingChunk> chunks) {
    if (blank(draft.name()) || blank(draft.activityRegion()) || blank(draft.contentSummary()) || blank(draft.sourceUrl())) {
      return new Classified("excluded", "필수 최소 근거(실명/지역/교육활동/원문링크) 중 일부 누락");
    }
    if (!isPlausiblePersonName(draft.name())) {
      return new Classified("excluded", "실명으로 보기 어려운 값");
    }
    if (!VALID_ROLE_WORDS.contains(draft.role())) {
      return new Classified("excluded", "원장/대표/강사/선생님 역할로 확인되지 않음");
    }
    String corpus = String.join(" ", draft.name(), draft.role(), nvl(draft.academyName(), ""), draft.activityRegion(), draft.contentType(), draft.contentSummary(), draft.discoveryReason());
    for (String term : HARD_EXCLUDE_TERMS) {
      if (corpus.contains(term)) return new Classified("excluded", "최우선 제외 조건 매칭: " + term);
    }
    if (!isSourceGrounded(draft.sourceUrl(), chunks)) {
      draft.verificationStatus(ChartVerificationStatus.REVIEW_REQUIRED);
      draft.exclusionCheck("원문 링크가 이번 Google grounding 결과와 자동 대조되지 않아 관리자 원문 확인 필요");
      return new Classified("review", draft.exclusionCheck());
    }
    if (draft.verificationStatus() == null) {
      draft.verificationStatus(blank(draft.academyName()) ? ChartVerificationStatus.REVIEW_REQUIRED : ChartVerificationStatus.VERIFIED);
    }
    if (blank(draft.exclusionCheck())) {
      draft.exclusionCheck(draft.verificationStatus() == ChartVerificationStatus.VERIFIED
          ? "실명 / 역할 / 학원명 / 지역 / Google 근거 링크가 확인됨"
          : "학원명 또는 맥락이 불명확해 관리자 확인 필요");
    }
    return new Classified("keep", draft.exclusionCheck());
  }

  private MergeResult mergeDuplicateCandidates(List<JsonNode> rawItems) {
    Map<String, DraftCandidate> merged = new LinkedHashMap<>();
    int dup = 0;
    for (JsonNode node : rawItems) {
      DraftCandidate draft = DraftCandidate.from(node);
      if (blank(draft.name()) || blank(draft.activityRegion())) continue;
      String key = personKey(draft.name(), draft.activityRegion());
      draft.personKey(key);
      if (merged.containsKey(key)) {
        dup++;
        DraftCandidate existing = merged.get(key);
        existing.activities().add(new ActivityDraft(draft.contentType(), draft.contentSummary(), draft.sourceUrl()));
        if (existing.academyName() == null && draft.academyName() != null) existing.academyName(draft.academyName());
      } else {
        draft.activities().add(new ActivityDraft(draft.contentType(), draft.contentSummary(), draft.sourceUrl()));
        merged.put(key, draft);
      }
    }
    return new MergeResult(new ArrayList<>(merged.values()), dup);
  }

  private ChartCandidateResponse toResponse(ChartCandidate candidate) {
    List<ChartActivityResponse> activities = activityRepository.findByCandidate_CandidateIdOrderByActivityIdAsc(candidate.getCandidateId())
        .stream().map(ChartActivityResponse::from).toList();
    return ChartCandidateResponse.from(candidate, activities);
  }

  private List<JsonNode> parseArray(String text) {
    try {
      JsonNode parsed = objectMapper.readTree(stripJson(text));
      if (!parsed.isArray()) return List.of();
      List<JsonNode> nodes = new ArrayList<>();
      parsed.forEach(nodes::add);
      return nodes;
    } catch (Exception ignored) {
      return List.of();
    }
  }

  private JsonNode parseObject(String text) {
    try { return objectMapper.readTree(stripJson(text)); } catch (Exception ignored) { return objectMapper.createObjectNode(); }
  }

  private String stripJson(String text) {
    return JSON_FENCE.matcher((text == null ? "" : text).trim()).replaceAll("").trim();
  }

  private boolean isSourceGrounded(String sourceUrl, List<ChartGeminiClient.GroundingChunk> chunks) {
    if (blank(sourceUrl)) return false;
    String targetHost = host(sourceUrl);
    for (ChartGeminiClient.GroundingChunk chunk : chunks) {
      if (sourceUrl.equals(chunk.uri())) return true;
      if (!blank(targetHost) && targetHost.equals(host(chunk.uri()))) return true;
    }
    return false;
  }

  private String host(String url) {
    try { return URI.create(url).getHost().toLowerCase(Locale.ROOT); } catch (Exception e) { return ""; }
  }

  private boolean isPlausiblePersonName(String name) {
    String normalized = Normalizer.normalize(nvl(name, ""), Normalizer.Form.NFC).replaceAll("\\s+", "");
    if (normalized.isBlank() || normalized.length() < 2 || normalized.length() > 4) return false;
    for (String stop : NAME_STOPWORDS) if (normalized.contains(stop)) return false;
    return COMMON_KOREAN_SURNAMES.indexOf(normalized.charAt(0)) >= 0;
  }

  private String personKey(String name, String region) {
    return Normalizer.normalize(nvl(name, ""), Normalizer.Form.NFC).replaceAll("\\s+", "") + "|"
        + Normalizer.normalize(nvl(region, ""), Normalizer.Form.NFC).replaceAll("\\s+", "");
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(nvl(value, "").getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder();
      for (byte b : bytes) builder.append(String.format("%02x", b));
      return builder.toString();
    } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }

  private String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (value.isMissingNode() || value.isNull()) return null;
    String text = value.asText(null);
    return blank(text) || "null".equalsIgnoreCase(text) ? null : text.trim();
  }

  private String writeJson(Object value) {
    try { return objectMapper.writeValueAsString(value); } catch (Exception e) { return "{}"; }
  }

  private List<String> distinct(List<String> values) { return new ArrayList<>(new LinkedHashSet<>(values)); }
  private String left(String value, int max) { return value == null ? "" : value.substring(0, Math.min(value.length(), max)); }
  private boolean blank(String value) { return value == null || value.isBlank(); }
  private String nvl(String value, String fallback) { return value == null ? fallback : value; }

  private record Classified(String status, String reason) {}
  private record MergeResult(List<DraftCandidate> drafts, int duplicatesMergedCount) {}
  private record ActivityDraft(String contentType, String contentSummary, String sourceUrl) {}

  private static final class DraftCandidate {
    private String personKey;
    private final String name;
    private String role;
    private String academyName;
    private final String activityRegion;
    private String contentType;
    private String contentSummary;
    private final String sourceUrl;
    private final String sourceTitle;
    private String discoveryReason;
    private String deepSearchResult = "";
    private String exclusionCheck = "";
    private ChartVerificationStatus verificationStatus;
    private final List<ActivityDraft> activities = new ArrayList<>();

    private DraftCandidate(JsonNode node) {
      this.name = textStatic(node, "name");
      this.role = textStatic(node, "role");
      this.academyName = textStatic(node, "academyName");
      this.activityRegion = textStatic(node, "activityRegion");
      this.contentType = nvlStatic(textStatic(node, "contentType"), "미상");
      this.contentSummary = nvlStatic(textStatic(node, "contentSummary"), "");
      this.sourceUrl = textStatic(node, "sourceUrl");
      this.sourceTitle = textStatic(node, "sourceTitle");
      this.discoveryReason = nvlStatic(textStatic(node, "discoveryReason"), "");
      if (node.path("isAmbiguous").asBoolean(false) || node.path("isLargeFranchiseSignal").asBoolean(false)
          || node.path("isMultiSiteSignal").asBoolean(false) || node.path("isOnlineOnlySignal").asBoolean(false)) {
        this.verificationStatus = ChartVerificationStatus.REVIEW_REQUIRED;
        this.exclusionCheck = "모델이 애매함/대형/다지점/인강 중심 가능성을 표시해 관리자 확인 필요";
      }
    }

    static DraftCandidate from(JsonNode node) { return new DraftCandidate(node); }
    String personKey() { return personKey; }
    void personKey(String value) { this.personKey = value; }
    String name() { return name; }
    String role() { return role; }
    String academyName() { return academyName; }
    void academyName(String value) { this.academyName = value; }
    String activityRegion() { return activityRegion; }
    String contentType() { return contentType; }
    String contentSummary() { return contentSummary; }
    String sourceUrl() { return sourceUrl; }
    String sourceTitle() { return sourceTitle; }
    String discoveryReason() { return discoveryReason; }
    String deepSearchResult() { return deepSearchResult; }
    void deepSearchResult(String value) { this.deepSearchResult = value; }
    String exclusionCheck() { return exclusionCheck; }
    void exclusionCheck(String value) { this.exclusionCheck = value; }
    ChartVerificationStatus verificationStatus() { return verificationStatus; }
    void verificationStatus(ChartVerificationStatus value) { this.verificationStatus = value; }
    List<ActivityDraft> activities() { return activities; }

    private static String textStatic(JsonNode node, String field) {
      JsonNode value = node.path(field);
      if (value.isMissingNode() || value.isNull()) return null;
      String text = value.asText(null);
      return text == null || text.isBlank() || "null".equalsIgnoreCase(text) ? null : text.trim();
    }
    private static String nvlStatic(String value, String fallback) { return value == null ? fallback : value; }
  }
}
