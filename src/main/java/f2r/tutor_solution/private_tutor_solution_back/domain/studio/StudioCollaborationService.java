package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Shared state lives in existing immutable revisions/events, never in a private AI session. */
@Service
@RequiredArgsConstructor
public class StudioCollaborationService {
  private final StudioService studio;
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public record Memory(
      String expectedRevisionId,
      String lastDecision,
      String currentAi,
      String openIssues,
      String nextWork,
      Integer pr,
      String commitSha,
      List<String> sourceFileIds) {}

  public record Comment(String body, String sharedAi, String sourceFileId) {}

  public Object memory(AuthenticatedPrincipal p) {
    studio.require(p, false, false);
    var rows =
        db.queryForList(
            "SELECT r.* FROM studio_items i JOIN studio_revisions r ON r.id=i.head_id AND"
                + " r.project_id=i.project_id WHERE i.project_id=? AND i.item_key='project-memory'",
            studio.projectId());
    if (rows.isEmpty()) return Map.of("revisionId", "", "memory", Map.of(), "files", List.of());
    var row = rows.getFirst();
    try {
      var memory = json.readTree((String) row.get("content"));
      if (!memory.isObject())
        throw StudioService.error(422, "작업 기억의 JSON 형식을 확인하세요. 이전 버전으로 복원할 수 있습니다.");
      if (memory.has("sourceFileIds")
          && (!memory.path("sourceFileIds").isArray() || memory.path("sourceFileIds").size() > 50))
        throw StudioService.error(422, "작업 기억의 파일 목록을 확인하세요.");
      var files = new ArrayList<Map<String, Object>>();
      for (var id : memory.path("sourceFileIds")) {
        var f = new LinkedHashMap<>(studio.file(p, id.asText()));
        f.remove("storage_key");
        files.add(f);
      }
      return Map.of(
          "revisionId",
          row.get("id"),
          "updatedAt",
          row.get("created_at"),
          "updatedBy",
          row.get("created_by"),
          "memory",
          memory,
          "files",
          files);
    } catch (org.springframework.web.server.ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw StudioService.error(422, "작업 기억을 읽을 수 없습니다. 원본과 이전 버전을 확인하세요.");
    }
  }

  @Transactional
  public Object saveMemory(AuthenticatedPrincipal p, Memory input) {
    studio.require(p, true, false);
    if (input == null || input.lastDecision() == null || input.lastDecision().isBlank())
      throw StudioService.error(400, "마지막 결정을 기록하세요.");
    bounded(input.lastDecision(), 4000);
    bounded(input.currentAi(), 100);
    bounded(input.openIssues(), 8000);
    bounded(input.nextWork(), 4000);
    if (input.pr() != null && (input.pr() < 1 || input.pr() > 1000000))
      throw StudioService.error(400, "PR 번호를 확인하세요.");
    if (input.commitSha() != null
        && !input.commitSha().isBlank()
        && !input.commitSha().matches("[a-f0-9]{40}|[a-f0-9]{64}"))
      throw StudioService.error(400, "커밋 SHA 형식 오류");
    var ids = input.sourceFileIds() == null ? List.<String>of() : input.sourceFileIds();
    if (ids.size() > 50 || new HashSet<>(ids).size() != ids.size())
      throw StudioService.error(400, "중복 없이 파일 50개까지 연결하세요.");
    for (var id : ids) studio.file(p, id);
    var data = new LinkedHashMap<String, Object>();
    data.put("lastDecision", input.lastDecision());
    data.put("currentAi", StudioService.text(input.currentAi()));
    data.put("openIssues", StudioService.text(input.openIssues()));
    data.put("nextWork", StudioService.text(input.nextWork()));
    data.put("pr", input.pr());
    data.put("commitSha", StudioService.text(input.commitSha()));
    data.put("sourceFileIds", ids);
    try {
      studio.save(
          p,
          new StudioService.Revision(
              "project-memory",
              StudioService.blankNull(input.expectedRevisionId()),
              "memory-" + UUID.randomUUID().toString().substring(0, 12),
              "JOB",
              null,
              null,
              "USER_EDIT",
              "프로젝트 공동 작업 기억",
              json.writeValueAsString(data),
              "",
              "",
              "REVIEW",
              null));
      return memory(p);
    } catch (org.springframework.web.server.ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("작업 기억 저장 실패", e);
    }
  }

  static void bounded(String value, int max) {
    if (value != null && value.length() > max) throw StudioService.error(400, "기록 길이 초과");
  }

  @Transactional
  public void comment(AuthenticatedPrincipal p, String revisionId, Comment input) {
    studio.require(p, true, false);
    studio.revision(p, revisionId);
    if (input == null || input.body() == null || input.body().isBlank())
      throw StudioService.error(400, "댓글 내용을 입력하세요.");
    bounded(input.body(), 1200);
    String ai = StudioService.blankNull(input.sharedAi()),
        source = StudioService.blankNull(input.sourceFileId());
    if (ai != null
        && (!Set.of("Claude", "Codex", "Muse", "ChatGPT").contains(ai) || source == null))
      throw StudioService.error(400, "AI 자료 공유는 작성 AI와 등록된 근거 파일이 필요합니다.");
    if (source != null) studio.file(p, source);
    var payload = new LinkedHashMap<String, Object>();
    payload.put("body", input.body());
    payload.put("authorType", ai == null ? "HUMAN" : "HUMAN_SHARED_AI_MATERIAL");
    payload.put("sharedAi", ai);
    payload.put("sourceFileId", source);
    String note;
    try {
      note = json.writeValueAsString(payload);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
    if (note.length() > 2000) throw StudioService.error(400, "댓글 기록 길이 초과");
    db.update(
        "INSERT INTO studio_events(project_id,revision_id,action,note,actor_id) VALUES"
            + " (?,?,'COMMENT',?,?)",
        studio.projectId(),
        revisionId,
        note,
        p.userId());
  }

  public Object comments(AuthenticatedPrincipal p, String revisionId, Long beforeId) {
    var revision = studio.revision(p, revisionId);
    if (beforeId != null && beforeId < 1) throw StudioService.error(400, "댓글 커서를 확인하세요.");
    return db.queryForList(
        "SELECT e.*,r.label,u.name AS actor_name FROM studio_events e JOIN studio_revisions r ON"
            + " r.id=e.revision_id AND r.project_id=e.project_id JOIN users u ON"
            + " u.user_id=e.actor_id WHERE e.project_id=? AND r.item_key=? AND e.action IN"
            + " ('COMMENT','APPROVE','REJECT','REQUEST_CHANGES','WITHDRAW_APPROVAL') AND e.id<?"
            + " ORDER BY e.id DESC LIMIT 100",
        studio.projectId(),
        revision.get("item_key"),
        beforeId == null ? Long.MAX_VALUE : beforeId);
  }

  public Object pending(AuthenticatedPrincipal p, int limit, int offset) {
    studio.require(p, false, false);
    if (limit < 1 || limit > 100 || offset < 0 || offset > 1000000)
      throw StudioService.error(400, "승인함 페이지 범위 오류");
    return db.queryForList(
        "SELECT * FROM (SELECT r.*,i.head_id,i.approved_id,(SELECT e.action FROM studio_events e"
            + " WHERE e.project_id=i.project_id AND e.revision_id=r.id AND e.action IN"
            + " ('APPROVE','REJECT','REQUEST_CHANGES','WITHDRAW_APPROVAL') ORDER BY e.id DESC LIMIT"
            + " 1) AS last_review FROM studio_items i JOIN studio_revisions r ON r.id=i.head_id AND"
            + " r.project_id=i.project_id WHERE i.project_id=? AND (i.approved_id IS NULL OR"
            + " i.head_id<>i.approved_id) AND r.kind<>'JOB' AND i.item_key NOT LIKE"
            + " 'source-09d62dee-%') pending WHERE COALESCE(last_review,'')<>'REJECT' ORDER BY"
            + " created_at DESC,id LIMIT ? OFFSET ?",
        studio.projectId(), limit, offset);
  }
}
