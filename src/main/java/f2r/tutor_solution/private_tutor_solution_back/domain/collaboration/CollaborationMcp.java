package f2r.tutor_solution.private_tutor_solution_back.domain.collaboration;

import static f2r.tutor_solution.private_tutor_solution_back.domain.collaboration.CollaborationService.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.mcp.McpOAuth;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** Only explicitly bound connections can append DRAFTs. Existing read-only tools are untouched. */
@Component
public class CollaborationMcp {
  private final JdbcTemplate db;
  private final CollaborationWorkspaces spaces;
  private final CollaborationService tasks;
  private final CollaborationFiles files;
  private final CollaborationSources sources;

  @Value("${app.collaboration.connector-append-enabled:false}")
  private boolean appendEnabled;

  public CollaborationMcp(
      JdbcTemplate db,
      CollaborationWorkspaces spaces,
      CollaborationService tasks,
      CollaborationFiles files,
      CollaborationSources sources) {
    this.db = db;
    this.spaces = spaces;
    this.tasks = tasks;
    this.files = files;
    this.sources = sources;
  }

  public record Content(List<Map<String, Object>> blocks) {}

  public List<Map<String, Object>> connections(AuthenticatedPrincipal p) {
    if (p == null) throw error(401, "로그인이 필요합니다.");
    return db.queryForList(
        "SELECT g.id,c.name,g.scopes,g.revoked,g.expires_at,b.workspace_id,b.agent FROM mcp_grants"
            + " g JOIN mcp_clients c ON c.id=g.client_id LEFT JOIN mcp_collaboration_bindings b ON"
            + " b.grant_id=g.id WHERE g.user_id=? ORDER BY g.created_at DESC",
        p.userId());
  }

  public record Bind(
      String grantId, String workspaceId, String agent, boolean acknowledgeDraftAppend) {}

  @Transactional
  public void bind(AuthenticatedPrincipal p, Bind r) {
    spaces.require(p, r.workspaceId(), false, false);
    if (!ORDER.contains(r.agent())) throw error(400, "AI 역할 오류");
    var grant =
        db
            .queryForList(
                "SELECT * FROM mcp_grants WHERE id=? AND user_id=? AND revoked=FALSE AND"
                    + " expires_at>CURRENT_TIMESTAMP FOR UPDATE",
                r.grantId(),
                p.userId())
            .stream()
            .findFirst()
            .orElseThrow(() -> error(403, "본인 연결을 확인하세요."));
    var scopes = Set.of(grant.get("scopes").toString().split(" +"));
    if (!scopes.contains("studio:read")) throw error(403, "제작 자료 읽기 범위를 먼저 승인하세요.");
    if (scopes.contains("collaboration:append")) {
      if (!appendEnabled || !r.acknowledgeDraftAppend())
        throw error(400, "이 연결의 AI·프로젝트 DRAFT 추가 권한을 별도로 확인하세요.");
      spaces.require(p, r.workspaceId(), true, false);
    }
    db.update(
        "INSERT INTO mcp_collaboration_bindings(grant_id,workspace_id,agent,bound_by)"
            + " VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE"
            + " workspace_id=VALUES(workspace_id),agent=VALUES(agent),bound_by=VALUES(bound_by),created_at=CURRENT_TIMESTAMP",
        r.grantId(),
        r.workspaceId(),
        r.agent(),
        p.userId());
    db.update(
        "INSERT INTO ai_collaboration_workspace_events(workspace_id,actor_id,event_type,body)"
            + " VALUES(?,?,'CONNECTOR_BOUND',?)",
        r.workspaceId(),
        p.userId(),
        r.agent()
            + " / "
            + r.grantId()
            + " / "
            + (scopes.contains("collaboration:append") ? "DRAFT 추가 허용" : "읽기 전용"));
  }

  @Transactional
  public void unbind(AuthenticatedPrincipal p, String grant) {
    var rows =
        db.queryForList(
            "SELECT b.* FROM mcp_collaboration_bindings b JOIN mcp_grants g ON g.id=b.grant_id"
                + " WHERE g.id=? AND g.user_id=?",
            grant,
            p.userId());
    if (rows.isEmpty()) throw error(404, "본인 연결이 없습니다.");
    db.update("DELETE FROM mcp_collaboration_bindings WHERE grant_id=?", grant);
    String workspace = rows.getFirst().get("workspace_id").toString();
    db.update(
        "INSERT INTO ai_collaboration_workspace_events(workspace_id,actor_id,event_type,body)"
            + " VALUES(?,?,'CONNECTOR_REVOKED',?)",
        workspace,
        p.userId(),
        grant);
  }

  Map<String, Object> binding(McpOAuth.Identity identity, boolean write) {
    if (!identity.scopes().contains(write ? "collaboration:append" : "studio:read")
        || (write && !appendEnabled)) throw error(403, "협업실 연결 권한이 없습니다.");
    var row =
        db
            .queryForList(
                "SELECT * FROM mcp_collaboration_bindings WHERE grant_id=? AND bound_by=?",
                identity.grantId(),
                identity.principal().userId())
            .stream()
            .findFirst()
            .orElseThrow(() -> error(403, "협업실에서 연결의 AI 역할과 프로젝트를 먼저 지정하세요."));
    spaces.require(identity.principal(), row.get("workspace_id").toString(), write, false);
    return row;
  }

  static Map<String, Object> string() {
    return Map.of("type", "string");
  }

  static Map<String, Object> integer() {
    return Map.of("type", "integer", "minimum", 0);
  }

  static Map<String, Object> tool(
      String name,
      String description,
      Map<String, Object> props,
      List<String> required,
      boolean write) {
    return Map.of(
        "name",
        name,
        "description",
        description,
        "inputSchema",
        Map.of(
            "type",
            "object",
            "properties",
            props,
            "required",
            required,
            "additionalProperties",
            false),
        "annotations",
        Map.of(
            "readOnlyHint",
            !write,
            "destructiveHint",
            false,
            "idempotentHint",
            !Set.of("collaboration_begin_upload", "collaboration_add_document").contains(name),
            "openWorldHint",
            false));
  }

  public List<Map<String, Object>> tools(McpOAuth.Identity identity) {
    if (db.queryForObject(
            "SELECT COUNT(*) FROM mcp_collaboration_bindings WHERE grant_id=?",
            Integer.class,
            identity.grantId())
        == 0) return List.of();
    var out = new ArrayList<Map<String, Object>>();
    if (identity.scopes().contains("studio:read"))
      out.add(
          tool(
              "collaboration_read",
              "사용자가 이 연결에 지정한 프로젝트 기억·공유 자료·내 인계 대기열 조회. taskId/fileId/query는 선택입니다. beforeId는 작업"
                  + " 이력10개/기억20개의 이전 페이지입니다. 앱 대화를 자동 실행하지 않습니다.",
              Map.of(
                  "taskId",
                  string(),
                  "fileId",
                  string(),
                  "query",
                  string(),
                  "filesBeforeId",
                  string(),
                  "filesQuery",
                  string(),
                  "beforeId",
                  integer(),
                  "offset",
                  integer(),
                  "limit",
                  integer()),
              List.of(),
              false));
    if (appendEnabled && identity.scopes().contains("collaboration:append")) {
      out.add(
          tool(
              "collaboration_submit",
              "현재 담당 AI의 수정 제안을 DRAFT로 등록하고 다음 담당자에게 자료/검수 의견을 자동 인계합니다. 사용자 승인·정본 변경·삭제·배포는"
                  + " 불가능합니다. requestId로 중복 재시도를 구분합니다.",
              Map.of(
                  "requestId",
                  string(),
                  "taskId",
                  string(),
                  "version",
                  integer(),
                  "text",
                  string(),
                  "decision",
                  Map.of("type", "string", "enum", List.of("INFO", "PASS", "CHANGES_REQUESTED")),
                  "fileIds",
                  Map.of("type", "array", "items", string(), "maxItems", 20)),
              List.of("requestId", "taskId", "version", "text", "decision"),
              true));
      out.add(
          tool(
              "collaboration_add_document",
              "선택된 작업의 문서를 UTF-8 MD·HTML·JSON DRAFT 첨부로 저장합니다. 원본 SHA와 작성 AI·사용자·시각을 보존하며 기존 정본은"
                  + " 변경하지 않습니다.",
              Map.of(
                  "taskId", string(), "filename", string(), "text", string(), "parentId", string()),
              List.of("taskId", "filename", "text"),
              true));
      out.add(
          tool(
              "collaboration_begin_upload",
              "선택된 작업의 새 문서·이미지·콘티 원본을 DRAFT 업로드로 예약합니다. 기존 승인 원본을 바꾸지 않습니다. 최대32MiB, SHA256 필수."
                  + " 바이너리 전달 가능 여부는 클라이언트별로 다릅니다.",
              Map.of(
                  "taskId",
                  string(),
                  "filename",
                  string(),
                  "size",
                  integer(),
                  "sha256",
                  string(),
                  "parentId",
                  string()),
              List.of("taskId", "filename", "size", "sha256"),
              true));
      out.add(
          tool(
              "collaboration_upload_chunk",
              "예약한 신규 후보의 바이너리 청크를 base64로 전달합니다(32768byte). 같은 번호/같은SHA 재시도만 허용합니다. 큰 바이너리는 공식 MCP"
                  + " SDK의 로컬 파일 전송 도우미를 사용하세요.",
              Map.of("uploadId", string(), "index", integer(), "base64", string()),
              List.of("uploadId", "index", "base64"),
              true));
      out.add(
          tool(
              "collaboration_commit_upload",
              "SHA/파일 형식 검사 후 후보 파일을 불변 원본으로 저장합니다. 항상 DRAFT이며 기존 승인 정본은 바꾸지 않습니다.",
              Map.of("uploadId", string()),
              List.of("uploadId"),
              true));
    }
    return out;
  }

  Map<String, Object> workspacePage(AuthenticatedPrincipal p, String workspace, long beforeId) {
    var snapshot = spaces.snapshot(p, workspace);
    var memory =
        beforeId == 0
            ? db.queryForList(
                "SELECT id,actor_id,actor_agent,event_type,body,created_at FROM"
                    + " ai_collaboration_workspace_events WHERE workspace_id=? ORDER BY id DESC"
                    + " LIMIT 20",
                workspace)
            : db.queryForList(
                "SELECT id,actor_id,actor_agent,event_type,body,created_at FROM"
                    + " ai_collaboration_workspace_events WHERE workspace_id=? AND id<? ORDER BY id"
                    + " DESC LIMIT 20",
                workspace,
                beforeId);
    snapshot.put("memory", memory);
    snapshot.put("memoryNextBeforeId", memory.isEmpty() ? 0 : memory.getLast().get("id"));
    return snapshot;
  }

  static String arg(JsonNode args, String name, int max) {
    return text(args.path(name).asText(null), max);
  }

  public Object execute(McpOAuth.Identity identity, String name, JsonNode args) {
    var definition =
        tools(identity).stream()
            .filter(t -> t.get("name").equals(name))
            .findFirst()
            .orElseThrow(() -> error(403, "이 연결에 허용된 도구가 아닙니다."));
    @SuppressWarnings("unchecked")
    var schema = (Map<String, Object>) definition.get("inputSchema");
    @SuppressWarnings("unchecked")
    var props = (Map<String, Object>) schema.get("properties");
    for (String required : (List<String>) schema.get("required"))
      if (!args.has(required)) throw error(400, "필수 인자 누락: " + required);
    for (var field : args.properties()) {
      if (!props.containsKey(field.getKey())) throw error(400, "허용되지 않은 인자");
      String type = ((Map<String, Object>) props.get(field.getKey())).get("type").toString();
      if ((type.equals("string") && !field.getValue().isTextual())
          || (type.equals("integer") && !field.getValue().isIntegralNumber())
          || (type.equals("array") && !field.getValue().isArray())
          || (type.equals("integer")
              && (!field.getValue().canConvertToLong()
                  || field.getValue().asLong() < 0
                  || field.getValue().asLong() > Integer.MAX_VALUE))) throw error(400, "인자 형식 오류");
    }
    var binding = binding(identity, !name.equals("collaboration_read"));
    String workspace = binding.get("workspace_id").toString(),
        agent = binding.get("agent").toString();
    var p = identity.principal();
    if (name.equals("collaboration_read")) {
      if (args.has("fileId")) {
        if (!identity.scopes().contains("originals:read")) throw error(403, "원본 제공 별도 동의가 필요합니다.");
        String id = arg(args, "fileId", 36);
        var meta = files.metadata(p, id);
        if (!meta.get("workspace_id").equals(workspace)) throw error(403, "연결 프로젝트의 파일만 열람하세요.");
        String ext = meta.get("extension").toString();
        if (Set.of("md", "json", "html", "pdf", "md5").contains(ext)) return files.text(p, id);
        byte[] bytes = files.bytes(p, id);
        if (ext.equals("png") && bytes.length <= 524288)
          return new Content(
              List.of(
                  Map.of("type", "text", "text", meta.toString()),
                  Map.of(
                      "type",
                      "image",
                      "mimeType",
                      "image/png",
                      "data",
                      Base64.getEncoder().encodeToString(bytes))));
        long offset = args.path("offset").asLong(0);
        int limit = args.path("limit").asInt(32768);
        if (offset < 0 || offset > bytes.length || limit < 1 || limit > 32768)
          throw error(400, "원본 범위 오류");
        return Map.of(
            "metadata",
            meta,
            "offset",
            offset,
            "base64",
            Base64.getEncoder()
                .encodeToString(
                    Arrays.copyOfRange(
                        bytes, (int) offset, (int) Math.min(bytes.length, offset + limit))),
            "totalBytes",
            bytes.length);
      }
      if (args.has("taskId")) {
        var task = tasks.detail(p, arg(args, "taskId", 36), 10, args.path("beforeId").asLong(0));
        if (!workspace(task).equals(workspace) || !participants(task).contains(agent))
          throw error(403, "이 AI가 지정된 작업만 조회하세요.");
        return task;
      }
      if (args.has("query")) return spaces.search(p, workspace, arg(args, "query", 200));
      if (args.has("filesBeforeId") || args.has("filesQuery"))
        return sources.files(
            p,
            workspace,
            args.path("filesBeforeId").asText(""),
            100,
            args.path("filesQuery").asText(""));
      return Map.of(
          "workspace",
          workspacePage(p, workspace, args.path("beforeId").asLong(0)),
          "agent",
          agent,
          "handoffInbox",
          tasks.inbox(p, workspace, agent),
          "serverAutoRun",
          false,
          "extraModelApiCost",
          0,
          "instructions",
          "대기 작업의 taskId로 지시·이력을 조회하고 후보를 등록하세요. 구독 앱의 시작/사용량 한도는 해당 제품에 따릅니다.");
    }
    if (name.equals("collaboration_submit")) {
      List<String> ids = new ArrayList<>();
      if (args.has("fileIds"))
        for (var file : args.path("fileIds")) {
          if (!file.isTextual()) throw error(400, "파일 ID 형식 오류");
          ids.add(file.asText());
        }
      return tasks.submit(
          p,
          workspace,
          agent,
          identity.grantId(),
          arg(args, "requestId", 64),
          arg(args, "taskId", 36),
          args.path("version").asLong(-1),
          arg(args, "text", 16000),
          arg(args, "decision", 30),
          ids);
    }
    if (name.equals("collaboration_add_document"))
      return files.document(
          p,
          identity.grantId(),
          workspace,
          agent,
          arg(args, "taskId", 36),
          arg(args, "filename", 255),
          arg(args, "text", 16000),
          args.has("parentId") ? arg(args, "parentId", 36) : null);
    if (name.equals("collaboration_begin_upload"))
      return files.begin(
          p,
          identity.grantId(),
          workspace,
          agent,
          arg(args, "taskId", 36),
          arg(args, "filename", 255),
          args.path("size").asLong(-1),
          arg(args, "sha256", 64),
          args.has("parentId") ? arg(args, "parentId", 36) : null);
    if (name.equals("collaboration_upload_chunk"))
      return files.chunk(
          p,
          identity.grantId(),
          workspace,
          agent,
          arg(args, "uploadId", 36),
          args.path("index").asInt(-1),
          arg(args, "base64", 43692));
    if (name.equals("collaboration_commit_upload"))
      return files.commit(p, identity.grantId(), workspace, agent, arg(args, "uploadId", 36));
    throw error(400, "지원하지 않는 도구");
  }
}
