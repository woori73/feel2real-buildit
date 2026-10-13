package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyAdminAudit;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyPageSettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyAdminAuditRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 홈페이지 관리 감사 기록. 기록은 <b>호출한 작업의 트랜잭션에 참여</b>한다(REQUIRED) — 기록이 실패하면 작업도
 * 롤백되고, 작업이 롤백되면 기록도 남지 않아 둘이 항상 일치한다. 일반 회원 API에는 노출하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AcademyAdminAuditService {

  public static final String ACCESS = "ACCESS";
  public static final String SAVE = "SAVE";
  public static final String PUBLISH = "PUBLISH";
  public static final String RESTORE = "RESTORE";
  public static final String DISCARD = "DISCARD";

  private static final int MAX_VALUE_LENGTH = 300;
  private static final int MAX_CHANGES = 50;
  /** 값 대신 필드 이름만 남기는 항목(사업자 정보 등 민감할 수 있는 값). */
  private static final String[] SETTINGS_FIELDS = {
      "blogUrl", "storeUrl", "naverMapUrl", "heroYoutubeUrl", "sectionVisibility", "sectionOrder", "businessInfo"};

  private final AcademyAdminAuditRepository repository;
  private final ObjectMapper objectMapper;

  @Transactional
  public void record(
      JwtTokenProvider.AuthenticatedPrincipal actor, Academy academy, String action,
      Long revision, boolean published, String summary, Map<String, Object> details) {
    Long ownerId = academy.getTutor() == null ? null : academy.getTutor().getUserId();
    repository.save(new AcademyAdminAudit(
        actor.userId(), actor.loginId(), academy.getAcademyId(), ownerId, action, revision, published,
        truncate(summary, 500), details == null ? null : toJson(details)));
  }

  @Transactional(readOnly = true)
  public List<AcademyAdminAudit> recent(Long academyId, int limit) {
    int size = Math.max(1, Math.min(limit, 200));
    return repository.findByAcademyIdOrderByAuditIdDesc(academyId, PageRequest.of(0, size));
  }

  /**
   * 작업본 수정 전/후를 비교해 감사 기록용 상세를 만든다. builderDraft는 키별 전/후 값(길이 제한)을, 그 외 설정은
   * 바뀐 필드 이름만 남긴다.
   */
  static Map<String, Object> describeChange(
      String introBefore, String introAfter, AcademyPageSettings before, AcademyPageSettings after) {
    Map<String, Object> details = new LinkedHashMap<>();
    boolean introChanged = !Objects.equals(introBefore, introAfter);
    details.put("introChanged", introChanged);
    if (introChanged) {
      details.put("introBefore", truncate(introBefore, MAX_VALUE_LENGTH));
      details.put("introAfter", truncate(introAfter, MAX_VALUE_LENGTH));
    }
    List<String> changedSettings = new ArrayList<>();
    if (before == null || after == null) {
      if (before != after) changedSettings.add("pageSettings");
    } else {
      for (String f : SETTINGS_FIELDS) {
        if (!Objects.equals(field(before, f), field(after, f))) changedSettings.add(f);
      }
    }
    details.put("changedSettings", changedSettings);

    Map<String, String> b = before == null || before.builderDraft() == null ? Map.of() : before.builderDraft();
    Map<String, String> a = after == null || after.builderDraft() == null ? Map.of() : after.builderDraft();
    List<Map<String, String>> changes = new ArrayList<>();
    for (String key : new TreeSet<>(union(b, a))) {
      if (!Objects.equals(b.get(key), a.get(key))) {
        if (changes.size() >= MAX_CHANGES) break;
        Map<String, String> c = new LinkedHashMap<>();
        c.put("key", key);
        c.put("before", truncate(b.get(key), MAX_VALUE_LENGTH));
        c.put("after", truncate(a.get(key), MAX_VALUE_LENGTH));
        changes.add(c);
      }
    }
    details.put("builderDraftChanges", changes);
    return details;
  }

  private static java.util.Set<String> union(Map<String, String> a, Map<String, String> b) {
    java.util.Set<String> keys = new java.util.HashSet<>(a.keySet());
    keys.addAll(b.keySet());
    return keys;
  }

  private static Object field(AcademyPageSettings s, String name) {
    return switch (name) {
      case "blogUrl" -> s.blogUrl();
      case "storeUrl" -> s.storeUrl();
      case "naverMapUrl" -> s.naverMapUrl();
      case "heroYoutubeUrl" -> s.heroYoutubeUrl();
      case "sectionVisibility" -> s.sectionVisibility();
      case "sectionOrder" -> s.sectionOrder();
      case "businessInfo" -> s.businessInfo();
      default -> null;
    };
  }

  static String truncate(String value, int max) {
    if (value == null) return null;
    return value.length() <= max ? value : value.substring(0, max) + "…";
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("감사 기록 상세를 직렬화하지 못했습니다.", e);
    }
  }
}
