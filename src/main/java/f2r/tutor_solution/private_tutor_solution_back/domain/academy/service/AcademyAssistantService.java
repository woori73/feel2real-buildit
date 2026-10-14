package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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

  /** 한 문장 시작(instant)에서 사용자가 말하지 않은 값을 채우는 임시 값. 초안에서만 고칠 수 있다. */
  static final String PROVISIONAL_REGION = "지역 미정";
  static final Set<TargetGroup> PROVISIONAL_TARGETS =
      Set.copyOf(EnumSet.of(TargetGroup.ELEMENTARY, TargetGroup.MIDDLE, TargetGroup.HIGH));

  private final AcademyAiCreateService createService;
  private final AcademyService academyService;
  private final AcademyWorkingCopyService workingCopyService;
  private final AcademyAssistantMessageRepository messageRepository;
  private final SubjectRepository subjectRepository;
  private final ObjectMapper objectMapper;
  private final AcademyAssistantEditService editService;
  private final PlatformTransactionManager txManager;
  private final AssistantAcademyMode assistantMode;
  private final AcademyPageVersionService versionService;

  /** 한 번의 대화 결과. needs가 비어 있지 않으면 학원은 아직 만들어지지 않았다. */
  public record TurnResult(
      String reply, List<String> needs, boolean created, Long academyId,
      Long revision, String designPreset, List<String> notes,
      AcademyAssistantEditService.EditOutcome edit) {}

  public record PublishResult(boolean firstPublish, long revision) {}

  /** adopted=false면 이미 AI 제작 학원이라 아무것도 바꾸지 않았다. */
  public record AdoptResult(boolean adopted, Long versionId, Long revision) {}

  public record StateView(
      boolean hasAcademy, String status, List<AcademyAssistantMessage> messages,
      Long revision, boolean dirty, String publicUrl, int undoDepth) {}

  public List<Sample> samples() {
    return AcademyAssistantSamples.all();
  }

  /** 화면 복원용 상태. 학원이 없으면 빈 대화다. 공개 내용은 바꾸지 않는다(작업본이 없으면 공개 내용으로 복제만 한다). */
  @Transactional
  public StateView state(JwtTokenProvider.AuthenticatedPrincipal principal) {
    Optional<AcademyResponse> mine = academyService.getMine(principal);
    if (mine.isEmpty()) {
      return new StateView(false, null, List.of(), null, false, null, 0);
    }
    AcademyResponse academy = mine.get();
    State wc = workingCopyService.open(principal);
    return new StateView(
        true, academy.status().name(),
        messageRepository.findByAcademyIdOrderByMessageIdAsc(academy.academyId()),
        wc.revision(), wc.dirty(), academy.publicUrl(), editService.undoDepth(academy.academyId()));
  }

  /**
   * 대화 한 턴을 처리한다. 학원이 없으면 '최초 생성', 있으면 '수정 턴'(비공개 작업본에만 적용, 되돌리기 가능)이다.
   *
   * @param academyName 신규 사용자의 학원 이름
   * @param sampleKey 시작 화면에서 고른 샘플 키(선택)
   * @param subject 사용자가 말한 과목 이름(예: 영어). 샘플이 과목을 말하지 않으면 되묻는다
   * @param target 사용자가 말한 대상(예: 중학생). 없으면 되묻는다
   * @param region 학원 지역(시/도 등). 없으면 대화로 되묻는다(주소를 지어내지 않는다)
   */
  public TurnResult turn(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      String message, String academyName, String sampleKey, String region,
      String subject, String target) {
    return turn(principal, message, academyName, sampleKey, region, subject, target, null);
  }

  public TurnResult turn(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      String message, String academyName, String sampleKey, String region,
      String subject, String target, Long revision) {
    return turn(principal, message, academyName, sampleKey, region, subject, target, revision, false);
  }

  /**
   * @param revision 수정 턴에서 클라이언트가 마지막으로 본 작업본 revision(동시 수정 감지).
   * @param instant true면 최초 생성에서 학원 이름·지역·대상이 없어도 임시 값으로 바로 만든다(Lovable식 한 문장 시작).
   *     임시 값은 초안일 때만 {@code /profile}로 고칠 수 있고, 과목은 문장에서 읽히지 않으면 여전히 되묻는다.
   */
  public TurnResult turn(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      String message, String academyName, String sampleKey, String region,
      String subject, String target, Long revision, boolean instant) {
    String text = message == null ? "" : message.trim();
    if (text.length() > MAX_MESSAGE_LENGTH) {
      throw new IllegalArgumentException("메시지는 " + MAX_MESSAGE_LENGTH + "자 이하로 입력해 주세요.");
    }
    if (isDangerous(text)) {
      throw new IllegalArgumentException("허용되지 않은 내용이 포함되어 있습니다.");
    }
    Optional<AcademyResponse> mine = academyService.getMine(principal);
    if (mine.isPresent()) {
      if (text.isEmpty()) {
        return new TurnResult("어떻게 바꿀까요? 예: \"더 고급스럽게 바꿔 줘\", \"주색을 파란색으로\", \"수업 영상 섹션 숨겨 줘\"",
            List.of(), false, mine.get().academyId(), revision, null, List.of(), null);
      }
      AcademyAssistantEditService.EditOutcome out = editService.edit(principal, text, revision);
      return new TurnResult(out.reply(), List.of(), false, mine.get().academyId(), out.revision(), null, List.of(), out);
    }
    return new TransactionTemplate(txManager).execute(
        status -> createFirstDraft(principal, text, academyName, sampleKey, region, subject, target, instant));
  }

  /** 버전 기록 목록(게시 전 공개본·AI 생성 이력). */
  public List<AcademyAiChatDtos.VersionInfo> versions(JwtTokenProvider.AuthenticatedPrincipal principal) {
    return versionService.list(principal);
  }

  /**
   * 버전 기록을 비공개 작업본에 불러온다(공개본 불변). 이전 수정의 되돌리기 정보는 더는 맞지 않으므로 대화 기록에
   * RESTORED를 남겨 되돌리기 스택을 비운다.
   */
  @Transactional
  public State restoreVersion(
      JwtTokenProvider.AuthenticatedPrincipal principal, long expectedRevision, Long versionId) {
    State state = workingCopyService.restoreFromVersion(principal, expectedRevision, versionId);
    academyService.getMine(principal).ifPresent(a -> remember(
        a.academyId(), ROLE_ASSISTANT, AcademyAssistantEditService.KIND_RESTORED,
        "이전 버전을 작업본에 불러왔어요. 홈페이지 정식 배포 등록 전까지 공개 홈페이지는 그대로예요.",
        toJson(Map.of("versionId", versionId, "revisionAfter", state.revision()))));
    return state;
  }

  /** 초안 학원의 이름·지역·대상 수정(임시 값을 실제 값으로). 게시된 학원은 거절한다. */
  @Transactional
  public void updateProfile(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      String name, String region, Set<TargetGroup> targets) {
    academyService.updateDraftProfile(principal, name, region, null, targets);
  }

  /** 화면 모드: NONE(학원 없음) / ASSISTANT(AI 제작 학원) / CLASSIC(기존 방식 학원, 전환 가능). */
  public String modeOf(JwtTokenProvider.AuthenticatedPrincipal principal) {
    Optional<AcademyResponse> mine = academyService.getMine(principal);
    if (mine.isEmpty()) return "NONE";
    return assistantMode.isAssistantAcademy(mine.get().academyId()) ? "ASSISTANT" : "CLASSIC";
  }

  /**
   * 기존 방식 학원을 'AI로 이어서 만들기'로 명시 전환한다. 사용자의 확인(컨트롤러가 요구)이 있어야만 호출된다.
   *
   * <p>전환 전 공개 내용을 버전 기록에 남기고(복구 기준점), 작업본을 준비하며, ADOPTED 기록을 남긴다.
   * 공개 홈페이지는 바꾸지 않는다 — 이후 수정도 게시하기 전까지 작업본에만 닿는다. 이미 AI 제작 학원이면 변경 없다.
   */
  @Transactional
  public AdoptResult adopt(JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (academyService.getMine(principal).isEmpty()) {
      throw new IllegalArgumentException("전환할 홈페이지가 없습니다. 먼저 홈페이지를 만들어 주세요.");
    }
    var academy = academyService.lockMyAcademyForUpdate(principal);
    Long academyId = academy.getAcademyId();
    if (assistantMode.isAssistantAcademy(academyId)) {
      return new AdoptResult(false, null, workingCopyService.open(principal).revision());
    }
    Long versionId = workingCopyService.snapshotLive(principal, "AI로 이어서 만들기 전 공개본");
    State state = workingCopyService.open(principal);
    remember(academyId, ROLE_ASSISTANT, AssistantAcademyMode.KIND_ADOPTED,
        "AI로 이어서 만들기를 시작했어요. 지금 공개된 홈페이지는 버전 기록에 보관했고, 앞으로의 수정은 홈페이지 정식 배포 등록 전까지 공개에 반영되지 않아요. 기존 빌더는 화면 위쪽 '기존 빌더' 링크로 언제든 다시 열 수 있어요.",
        toJson(Map.of("versionId", versionId, "revision", state.revision())));
    return new AdoptResult(true, versionId, state.revision());
  }

  // ───────────── (아래: 최초 생성 이후 공개 API) ─────────────

  /**
   * '게시하기' 하나로 작업본을 공개 홈페이지에 반영한다. 최초 게시면 DRAFT 학원을 PUBLISHED로 전환한다.
   * 작업본 게시와 상태 전환은 한 트랜잭션이다(둘 중 하나만 반영되지 않는다).
   */
  @Transactional
  public PublishResult publish(
      JwtTokenProvider.AuthenticatedPrincipal principal, long expectedRevision, boolean force) {
    State state = workingCopyService.publish(principal, expectedRevision, force);
    boolean first = academyService.publishIfDraft(principal);
    // 게시하면 그 이전 수정은 되돌리기 대상에서 빠진다(공개된 내용은 버전 기록으로 복원한다).
    academyService.getMine(principal).ifPresent(a -> remember(
        a.academyId(), ROLE_ASSISTANT, AcademyAssistantEditService.KIND_PUBLISHED,
        first ? "홈페이지가 공개됐어요. 이제 누구나 홈페이지를 볼 수 있어요." : "수정한 내용을 공개 홈페이지에 반영했어요.",
        toJson(Map.of("revisionAfter", state.revision()))));
    return new PublishResult(first, state.revision());
  }

  // ───────────────────────── 내부 ─────────────────────────

  private TurnResult createFirstDraft(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      String text, String academyName, String sampleKey, String region,
      String subject, String target, boolean instant) {
    Sample sample = AcademyAssistantSamples.find(sampleKey).orElse(null);
    String name = academyName == null ? "" : academyName.trim();
    boolean provisionalName = false;

    if (name.isEmpty() && instant) {
      name = provisionalName(inferSubjectName(text, subjectRepository.findAll().stream().map(Subject::getNameKo).toList()));
      provisionalName = true;
    }
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

    CreateDraft draft = createService.generateDraft(request, name, null);
    Map<String, String> generated = new HashMap<>(draft.builderDraft());
    String preset = sample != null ? sample.preset() : draft.designPreset();
    generated.put("designPreset", preset);

    // BuildIt2 디자인 자산(레시피) 선택 — 요청의 분위기·학원 유형에 맞는 검증된 값 묶음을 얹는다.
    // 샘플로 이미 정한 프리셋은 뒤집지 않고, 같은 연산 검증(AssistantPatchApplier)을 그대로 거친다.
    AssistantAssetCatalog.Asset recipe =
        AssistantAssetCatalog.recommendRecipe(request, name, preset, sample != null);
    if (recipe != null) {
      AssistantPatchApplier.Snapshot base = new AssistantPatchApplier.Snapshot("", generated, null, null);
      AssistantPatchApplier.Plan plan = AssistantPatchApplier.plan(
          List.of(AssistantPatchApplier.Op.section("applyAsset", recipe.id())), base, "");
      AssistantPatchApplier.Applied applied = AssistantPatchApplier.apply(base, plan.accepted());
      applied.draftPatch().forEach((k, v) -> {
        if (v == null) generated.remove(k);
        else generated.put(k, v);
      });
      preset = generated.get("designPreset");
    }

    // 과목·대상은 사용자가 말했거나 샘플 문구가 직접 말한 경우에만 확정한다.
    // 샘플 문구에서 분석기가 뽑아낸 값은 사용자가 한 말이 아니므로 쓰지 않는다.
    Set<Integer> subjectIds = new java.util.HashSet<>();
    if (subject != null && !subject.isBlank()) {
      subjectRepository.findByNameKo(subject.trim()).map(Subject::getSubjectId).ifPresent(subjectIds::add);
    }
    if (subjectIds.isEmpty()) {
      for (Integer id : parseSubjectIds(generated.get("subjectIds"))) {
        boolean saidByUser = sample == null || subjectRepository.findById(id)
            .map(sub -> text.contains(sub.getNameKo())).orElse(false);
        if (saidByUser) subjectIds.add(id);
      }
    }
    if (subjectIds.isEmpty() && sample != null && sample.subjectNameKo() != null) {
      subjectRepository.findByNameKo(sample.subjectNameKo())
          .map(Subject::getSubjectId).ifPresent(subjectIds::add);
    }
    Set<TargetGroup> targets = parseTargets(target);
    if (targets.isEmpty()) targets = parseTargets(text);
    if (targets.isEmpty() && sample != null && sample.targets() != null) {
      targets = EnumSet.copyOf(sample.targets());
    }
    if (targets.isEmpty() && sample == null) targets = parseTargets(generated.get("targetGroups"));

    if (instant && subjectIds.isEmpty()) {
      return ask(List.of("subject"), "어떤 과목을 가르치는 학원인가요? 예: 영어, 수학");
    }
    boolean provisionalTargets = false;
    if (instant && targets.isEmpty() && !subjectIds.isEmpty()) {
      targets = EnumSet.copyOf(PROVISIONAL_TARGETS);
      provisionalTargets = true;
    }
    List<String> needs = new ArrayList<>();
    if (subjectIds.isEmpty()) needs.add("subject");
    if (targets.isEmpty()) needs.add("target");
    // 지역은 사용자가 직접 한 말에서만 받는다. 샘플 문구에서 분석기가 뽑은 지역은 쓰지 않는다.
    String analyzed = generated.get("regionLocation");
    String regionText = region != null && !region.isBlank() ? region.trim()
        : (analyzed != null && text.contains(analyzed) ? analyzed : null);
    boolean provisionalRegion = false;
    if (instant && (regionText == null || regionText.isBlank()) && !subjectIds.isEmpty()) {
      regionText = PROVISIONAL_REGION;
      provisionalRegion = true;
    }
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
    notes.add("주소·전화번호·강사·수강료는 알려 주시기 전까지 비워 둡니다.");
    List<String> provisional = new ArrayList<>();
    if (provisionalName) provisional.add("학원 이름");
    if (provisionalRegion) provisional.add("지역");
    if (provisionalTargets) provisional.add("대상 학생");
    if (!provisional.isEmpty()) {
      notes.add("임시로 채운 정보: " + String.join(", ", provisional)
          + ". 화면 위쪽 '학원 정보'에서 실제 값으로 바꿀 수 있어요(정식 배포 등록 전까지).");
    }
    if (recipe != null) {
      notes.add("디자인 자산 '" + recipe.label() + "'을(를) 적용했어요. 다른 느낌을 원하시면 말씀해 주세요.");
    }

    String reply = "'" + name + "' 홈페이지 초안을 만들었어요. 아직 공개되지 않았으니 미리보기로 확인해 보세요.";
    Long academyId = created.academyId();
    remember(academyId, ROLE_USER, KIND_TEXT, summaryOfRequest(name, text, sample), null);
    remember(academyId, ROLE_ASSISTANT, KIND_RESULT, reply, toJson(Map.of(
        "revision", saved.revision(), "designPreset", preset, "notes", notes)));
    return new TurnResult(reply, List.of(), true, academyId, saved.revision(), preset, notes, null);
  }

  private static String label(String need) {
    return switch (need) {
      case "subject" -> "가르치는 과목";
      case "target" -> "대상 학생(초등/중등/고등 등)";
      case "region" -> "지역";
      default -> need;
    };
  }

  private TurnResult ask(List<String> needs, String reply) {
    return new TurnResult(reply, needs, false, null, null, null, List.of(), null);
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

  /** 임시 학원 이름: 과목이 읽히면 "새 영어학원", 아니면 "새 학원". */
  static String provisionalName(String subjectNameKo) {
    return subjectNameKo == null || subjectNameKo.isBlank() ? "새 학원" : "새 " + subjectNameKo.trim() + "학원";
  }

  /** 문장에 들어 있는 과목 이름(가장 긴 일치)을 찾는다. 없으면 null. */
  static String inferSubjectName(String text, java.util.Collection<String> subjectNames) {
    if (text == null || text.isBlank() || subjectNames == null) return null;
    String best = null;
    for (String n : subjectNames) {
      if (n == null || n.isBlank() || !text.contains(n)) continue;
      if (best == null || n.length() > best.length()) best = n;
    }
    return best;
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
