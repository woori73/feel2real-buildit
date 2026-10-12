package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiCreateDtos.CreateDraft;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyPageSettingsUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyRegionRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyUpsertRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAssistantMessage;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyAssistantMessageRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyAssistantSamples.Sample;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyWorkingCopyService.State;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BuildIt 통합 화면의 대화 서비스(2-A). 새 AI 엔진을 만들지 않고 기존 생성기
 * ({@link AcademyAiCreateService})와 작업본({@link AcademyWorkingCopyService})을 조합한다.
 *
 * <ul>
 *   <li>생성 결과는 작업본에만 쓴다. 공개 홈페이지는 {@link #publish}에서만 바뀐다.
 *   <li>사용자가 말하지 않은 주소·전화번호·강사·수강료는 만들지 않는다(생성기의 허위 방지 규칙 그대로).
 *   <li>학원이 아직 없으면(신규) 학원 이름부터 받고, 부족한 정보는 대화로 되묻는다.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AcademyAssistantService {

  static final String ROLE_USER = "USER";
  static final String ROLE_ASSISTANT = "ASSISTANT";
  static final String KIND_TEXT = "TEXT";
  static final String KIND_QUESTION = "QUESTION";
  static final String KIND_RESULT = "RESULT";

  private static final int MAX_NAME_LENGTH = 100;
  private static final int MAX_MESSAGE_LENGTH = 2000;
  /** 작업본에 옮기지 않는 키: 생성 전용 전달 키와 학원 최상위 필드(intro). */
  private static final Set<String> NON_DRAFT_KEYS = Set.of(
      "academyName", "targetGroups", "ctaEnabled", "subjectIds", "regionLocation", "intro");

  private final AcademyAiCreateService createService;
  private final AcademyService academyService;
  private final AcademyWorkingCopyService workingCopyService;
  private final AcademyAssistantMessageRepository messageRepository;
  private final SubjectRepository subjectRepository;
  private final ObjectMapper objectMapper;

  /** 한 번의 대화 결과. needs가 비어 있지 않으면 학원은 아직 만들어지지 않았다. */
  public record TurnResult(
      String reply, List<String> needs, boolean created, Long academyId,
      Long revision, String designPreset, List<String> notes) {}

  public record PublishResult(boolean firstPublish, long revision) {}

  public record StateView(
      boolean hasAcademy, String status, List<AcademyAssistantMessage> messages,
      Long revision, boolean dirty) {}

  public List<Sample> samples() {
    return AcademyAssistantSamples.all();
  }

  /** 화면 복원용 상태. 학원이 없으면 빈 대화다. 조회만 하고 아무것도 만들지 않는다. */
  @Transactional(readOnly = true)
  public StateView state(JwtTokenProvider.AuthenticatedPrincipal principal) {
    Optional<AcademyResponse> mine = academyService.getMine(principal);
    if (mine.isEmpty()) {
      return new StateView(false, null, List.of(), null, false);
    }
    Long academyId = mine.get().academyId();
    return new StateView(
        true, mine.get().status().name(),
        messageRepository.findByAcademyIdOrderByMessageIdAsc(academyId), null, false);
  }

  /**
   * 대화 한 턴을 처리한다. 현재는 '최초 생성'만 지원한다(수정 턴은 2-C).
   *
   * @param academyName 신규 사용자의 학원 이름
   * @param sampleKey 시작 화면에서 고른 샘플 키(선택)
   * @param region 학원 지역(시/도 등). 없으면 대화로 되묻는다(주소를 지어내지 않는다)
   */
  @Transactional
  public TurnResult turn(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      String message, String academyName, String sampleKey, String region) {
    String text = message == null ? "" : message.trim();
    if (text.length() > MAX_MESSAGE_LENGTH) {
      throw new IllegalArgumentException("메시지는 " + MAX_MESSAGE_LENGTH + "자 이하로 입력해 주세요.");
    }
    if (isDangerous(text)) {
      throw new IllegalArgumentException("허용되지 않은 내용이 포함되어 있습니다.");
    }
    Optional<AcademyResponse> mine = academyService.getMine(principal);
    if (mine.isPresent()) {
      Long academyId = mine.get().academyId();
      remember(academyId, ROLE_USER, KIND_TEXT, text, null);
      String reply = "수정 요청은 다음 업데이트에서 지원됩니다. 지금은 만들어진 홈페이지를 미리보기로 확인하고 게시할 수 있습니다.";
      remember(academyId, ROLE_ASSISTANT, KIND_TEXT, reply, null);
      return new TurnResult(reply, List.of(), false, academyId, null, null, List.of());
    }
    return createFirstDraft(principal, text, academyName, sampleKey, region);
  }

  /**
   * '게시하기' 하나로 작업본을 공개 홈페이지에 반영한다. 최초 게시면 DRAFT 학원을 PUBLISHED로 전환한다.
   * 작업본 게시와 상태 전환은 한 트랜잭션이다(둘 중 하나만 반영되지 않는다).
   */
  @Transactional
  public PublishResult publish(
      JwtTokenProvider.AuthenticatedPrincipal principal, long expectedRevision, boolean force) {
    State state = workingCopyService.publish(principal, expectedRevision, force);
    boolean first = academyService.publishIfDraft(principal);
    return new PublishResult(first, state.revision());
  }

  // ───────────────────────── 내부 ─────────────────────────

  private TurnResult createFirstDraft(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      String text, String academyName, String sampleKey, String region) {
    Sample sample = AcademyAssistantSamples.find(sampleKey).orElse(null);
    String name = academyName == null ? "" : academyName.trim();

    if (name.isEmpty()) {
      return ask(List.of("academyName"), "학원 이름을 알려 주세요. 이름이 정해지면 바로 홈페이지를 만들어 드릴게요.");
    }
    if (name.length() > MAX_NAME_LENGTH || isDangerous(name)) {
      throw new IllegalArgumentException("학원 이름은 100자 이하의 일반 글자로 입력해 주세요.");
    }

    String request = ((sample == null ? "" : sample.seed() + " ") + text).trim();
    if (request.isEmpty()) {
      return ask(List.of("description"),
          "어떤 학원인지 한두 문장으로 알려 주세요. (예: 중학생 영어 내신 전문, 또는 아래 샘플 선택)");
    }

    CreateDraft draft = createService.generateDraft(request, name);
    Map<String, String> generated = new HashMap<>(draft.builderDraft());
    String preset = sample != null ? sample.preset() : draft.designPreset();
    generated.put("designPreset", preset);

    Set<Integer> subjectIds = new java.util.HashSet<>(parseSubjectIds(generated.get("subjectIds")));
    if (subjectIds.isEmpty() && sample != null) {
      subjectRepository.findByNameKo(sample.subjectNameKo())
          .map(Subject::getSubjectId).ifPresent(subjectIds::add);
    }
    Set<TargetGroup> targets = sample != null && generated.get("targetGroups") == null
        ? EnumSet.copyOf(sample.targets()) : parseTargets(generated.get("targetGroups"));

    List<String> needs = new ArrayList<>();
    if (subjectIds.isEmpty()) needs.add("subject");
    if (targets.isEmpty()) needs.add("target");
    // 지역은 사용자가 직접 한 말에서만 받는다. 샘플 문구에서 분석기가 뽑은 지역은 쓰지 않는다.
    String analyzed = generated.get("regionLocation");
    String regionText = region != null && !region.isBlank() ? region.trim()
        : (analyzed != null && text.contains(analyzed) ? analyzed : null);
    if (regionText == null || regionText.isBlank()) needs.add("region");
    if (!needs.isEmpty()) {
      return ask(needs, "홈페이지를 만들려면 " + String.join(", ", needs.stream().map(AcademyAssistantService::label).toList())
          + " 정보가 필요해요. 알려 주세요. (주소 전체가 아니라 '서울 강남구'처럼 지역만 있으면 됩니다.)");
    }
    if (regionText.length() > MAX_NAME_LENGTH || isDangerous(regionText)) {
      throw new IllegalArgumentException("지역은 100자 이하의 일반 글자로 입력해 주세요.");
    }

    String intro = generated.getOrDefault("intro", name);
    AcademyUpsertRequest upsert = new AcademyUpsertRequest(
        name, null, name + " 홈페이지를 준비하고 있습니다.", subjectIds, targets,
        List.of(new AcademyRegionRequest(regionText.trim(), "")), null);
    AcademyResponse created = academyService.createDraft(principal, upsert, "assistant-" + UUID.randomUUID());

    Map<String, String> builderDraft = new LinkedHashMap<>();
    generated.forEach((k, v) -> {
      if (!NON_DRAFT_KEYS.contains(k) && v != null) builderDraft.put(k, v);
    });
    State opened = workingCopyService.open(principal);
    State saved = workingCopyService.save(
        principal, opened.revision(), intro,
        new AcademyPageSettingsUpdateRequest(null, null, null, null, null, null, null, builderDraft));

    List<String> notes = new ArrayList<>(draft.warnings());
    if (sample != null && parseSubjectIds(generated.get("subjectIds")).isEmpty()) {
      notes.add("과목과 대상은 샘플 기본값(" + sample.subjectNameKo() + ")으로 임시 설정했습니다. 다르면 알려 주세요.");
    }
    notes.add("주소·전화번호·강사·수강료는 알려 주시기 전까지 비워 둡니다.");

    String reply = "'" + name + "' 홈페이지 초안을 만들었어요. 아직 공개되지 않았으니 미리보기로 확인해 보세요.";
    Long academyId = created.academyId();
    remember(academyId, ROLE_USER, KIND_TEXT, summaryOfRequest(name, text, sample), null);
    remember(academyId, ROLE_ASSISTANT, KIND_RESULT, reply, toJson(Map.of(
        "revision", saved.revision(), "designPreset", preset, "notes", notes)));
    return new TurnResult(reply, List.of(), true, academyId, saved.revision(), preset, notes);
  }

  private static String label(String need) {
    return switch (need) {
      case "subject" -> "과목";
      case "target" -> "대상 학생(초등/중등/고등 등)";
      case "region" -> "지역";
      default -> need;
    };
  }

  private TurnResult ask(List<String> needs, String reply) {
    return new TurnResult(reply, needs, false, null, null, null, List.of());
  }

  private String summaryOfRequest(String name, String text, Sample sample) {
    StringBuilder sb = new StringBuilder("학원 이름: ").append(name);
    if (sample != null) sb.append("\n샘플: ").append(sample.title());
    if (!text.isEmpty()) sb.append("\n").append(text);
    return sb.toString();
  }

  private void remember(Long academyId, String role, String kind, String content, String payload) {
    messageRepository.save(new AcademyAssistantMessage(academyId, role, kind, content, payload));
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("대화 부가 정보를 저장할 수 없습니다.", e);
    }
  }

  private static boolean isDangerous(String value) {
    String lower = value.toLowerCase();
    return lower.contains("<script") || lower.contains("javascript:")
        || lower.contains("onerror=") || lower.contains("onload=");
  }

  static Set<Integer> parseSubjectIds(String raw) {
    Set<Integer> result = new java.util.HashSet<>();
    if (raw == null || raw.isBlank()) return result;
    for (String part : raw.split(",")) {
      try {
        result.add(Integer.parseInt(part.trim()));
      } catch (NumberFormatException ignored) {
        // 잘못된 조각은 무시하고, 비면 되묻는다.
      }
    }
    return result;
  }

  static Set<TargetGroup> parseTargets(String ko) {
    Set<TargetGroup> result = EnumSet.noneOf(TargetGroup.class);
    if (ko == null || ko.isBlank()) return result;
    String t = ko.replaceAll("\\s+", "");
    if (t.contains("초등")) result.add(TargetGroup.ELEMENTARY);
    if (t.contains("중학") || t.contains("중등")) result.add(TargetGroup.MIDDLE);
    if (t.contains("고등")) result.add(TargetGroup.HIGH);
    if (t.contains("대학")) result.add(TargetGroup.UNIVERSITY);
    if (t.contains("성인")) result.add(TargetGroup.ADULT);
    return result;
  }
}
