package f2r.tutor_solution.private_tutor_solution_back.domain.collaboration;

import static f2r.tutor_solution.private_tutor_solution_back.domain.collaboration.CollaborationService.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.studio.StudioService;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CollaborationFiles {
  public static final long MAX = 32L * 1024 * 1024;
  private final JdbcTemplate db;
  private final FileStorage storage;
  private final CollaborationWorkspaces spaces;
  private final CollaborationService tasks;
  private final TransactionTemplate tx;
  private final java.util.concurrent.Semaphore reads = new java.util.concurrent.Semaphore(2);

  public CollaborationFiles(
      JdbcTemplate db,
      FileStorage storage,
      CollaborationWorkspaces spaces,
      CollaborationService tasks,
      PlatformTransactionManager manager) {
    this.db = db;
    this.storage = storage;
    this.spaces = spaces;
    this.tasks = tasks;
    this.tx = new TransactionTemplate(manager);
  }

  public static String sha(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException("SHA unavailable");
    }
  }

  static String filename(String raw) {
    String name = CollaborationService.text(raw, 255).replaceAll("[\\\\/\\p{Cntrl}]", "_");
    extension(name);
    return name;
  }

  static String extension(String name) {
    int dot = name.lastIndexOf('.');
    String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    if (!Set.of("pdf", "html", "json", "md", "zip", "png", "mp4", "md5").contains(ext))
      throw error(400, "PDF·HTML·JSON·MD·ZIP·PNG·MP4·MD5만 가능합니다. MD5는 검증 메타자료입니다.");
    return ext;
  }

  Map<String, Object> raw(String id) {
    return db.queryForList("SELECT * FROM ai_collaboration_files WHERE id=?", id).stream()
        .findFirst()
        .orElseThrow(() -> error(404, "첨부가 없습니다."));
  }

  public Map<String, Object> metadata(AuthenticatedPrincipal p, String id) {
    var file = raw(id);
    spaces.require(p, file.get("workspace_id").toString(), false, false);
    var safe = new LinkedHashMap<>(file);
    safe.remove("storage_key");
    safe.remove("source_grant_id");
    var provenance =
        db.queryForList(
            "SELECT s.source_path,s.version_label,s.category,s.source_approval,i.source_ref FROM"
                + " ai_collaboration_sources s JOIN ai_collaboration_imports i ON i.id=s.import_id"
                + " WHERE s.file_id=?",
            id);
    if (!provenance.isEmpty()) safe.putAll(provenance.getFirst());
    return safe;
  }

  public byte[] bytes(AuthenticatedPrincipal p, String id) {
    var file = raw(id);
    spaces.require(p, file.get("workspace_id").toString(), false, false);
    if (!reads.tryAcquire()) throw error(429, "다른 원본 조회 중입니다.");
    try {
      return storage.readSingleLimited(file.get("storage_key").toString(), "UNKNOWN", MAX);
    } catch (IOException e) {
      throw error(422, "첨부 원본을 읽을 수 없습니다.");
    } finally {
      reads.release();
    }
  }

  Map<String, Object> store(
      AuthenticatedPrincipal p,
      String workspace,
      String name,
      byte[] bytes,
      String parent,
      String agent,
      String grant) {
    spaces.require(p, workspace, true, false);
    if (bytes.length < 1 || bytes.length > MAX) throw error(413, "첨부는 1 byte~32 MiB까지 가능합니다.");
    name = filename(name);
    String ext = extension(name), digest = sha(bytes), id = UUID.randomUUID().toString();
    if (parent != null) {
      var old = metadata(p, parent);
      if (!old.get("workspace_id").equals(workspace)) throw error(403, "수정 제안의 원본 프로젝트가 다릅니다.");
    }
    Path temp = null;
    try {
      temp = Files.createTempFile("f2r-collab-validate-", "." + ext);
      Files.write(temp, bytes);
      StudioService.validateType(temp, ext.equals("md5") ? "md" : ext);
    } catch (IOException e) {
      throw error(422, "첨부 형식 검사 실패");
    } finally {
      if (temp != null)
        try {
          Files.deleteIfExists(temp);
        } catch (IOException ignored) {
        }
    }
    db.queryForList("SELECT id FROM ai_collaboration_workspaces WHERE id=? FOR UPDATE", workspace);
    var duplicate =
        db.queryForList(
            "SELECT storage_key FROM ai_collaboration_files WHERE workspace_id=? AND sha256=? LIMIT"
                + " 1",
            workspace,
            digest);
    String key;
    if (duplicate.isEmpty()) {
      try {
        key =
            storage.saveRaw(
                p.userId(),
                "collaboration-" + id + "." + ext,
                new ByteArrayInputStream(bytes),
                bytes.length);
      } catch (IOException e) {
        throw error(422, "첨부 원본 저장 실패");
      }
      String newKey = key;
      TransactionalFileDeletion.onRollback(
          () -> {
            try {
              storage.deleteReported(newKey, "UNKNOWN");
            } catch (RuntimeException ignored) {
              org.slf4j.LoggerFactory.getLogger(getClass())
                  .warn("Collaboration draft rollback cleanup failed fileId={}", id);
            }
          });
    } else key = duplicate.getFirst().get("storage_key").toString();
    db.update(
        "INSERT INTO"
            + " ai_collaboration_files(id,workspace_id,parent_id,storage_key,filename,extension,sha256,size_bytes,status,registered_by,registered_agent,source_grant_id)"
            + " VALUES(?,?,?,?,?,?,?,?,'DRAFT',?,?,?)",
        id,
        workspace,
        parent,
        key,
        name,
        ext,
        digest,
        bytes.length,
        p.userId(),
        agent,
        grant);
    spaces.memory(
        p, workspace, "FILE_DRAFT", "새 후보 " + id + " / " + name + " / SHA256 " + digest, agent);
    var result = new LinkedHashMap<>(metadata(p, id));
    result.put("duplicateContent", !duplicate.isEmpty());
    return result;
  }

  public Map<String, Object> document(
      AuthenticatedPrincipal p,
      String grant,
      String workspace,
      String agent,
      String task,
      String name,
      String content,
      String parent) {
    String ext = extension(filename(name));
    if (!Set.of("md", "json", "html").contains(ext))
      throw error(400, "텍스트 첨부는 MD·JSON·HTML을 사용하세요.");
    byte[] bytes =
        CollaborationService.text(content, 16000).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    return tx.execute(
        t -> {
          writableTask(p, workspace, agent, task);
          return store(p, workspace, name, bytes, parent, agent, grant);
        });
  }

  public Map<String, Object> upload(
      AuthenticatedPrincipal p, String workspace, MultipartFile file, String parent) {
    spaces.require(p, workspace, true, false);
    if (file.getSize() > MAX) throw error(413, "첨부는 32 MiB 이하입니다.");
    byte[] bytes;
    try (var in = file.getInputStream()) {
      bytes = in.readNBytes((int) MAX + 1);
    } catch (IOException e) {
      throw error(422, "첨부를 읽을 수 없습니다.");
    }
    return tx.execute(
        t ->
            store(
                p,
                workspace,
                Objects.toString(file.getOriginalFilename(), "file"),
                bytes,
                parent,
                null,
                null));
  }

  public void review(AuthenticatedPrincipal p, String id, String decision) {
    tx.executeWithoutResult(
        t -> {
          var file = raw(id);
          String workspace = file.get("workspace_id").toString();
          spaces.require(p, workspace, true, true);
          if (!Set.of("APPROVED", "REJECTED").contains(decision)) throw error(400, "검수 상태 오류");
          if (db.update(
                  "UPDATE ai_collaboration_files SET status=? WHERE id=? AND status='DRAFT'",
                  decision,
                  id)
              != 1) throw error(409, "이미 검수된 후보입니다. 새 수정 제안을 등록하세요.");
          spaces.memory(
              p,
              workspace,
              "FILE_REVIEW",
              id + " / " + decision + " / SHA256 " + file.get("sha256"),
              null);
        });
  }

  void writableTask(AuthenticatedPrincipal p, String workspace, String agent, String task) {
    var row = tasks.task(task, true);
    spaces.require(p, workspace, true, false);
    if (!workspace(row).equals(workspace)
        || !participants(row).contains(agent)
        || !Set.of("DRAFT", "WAITING_AGENT", "PAUSED").contains(row.get("status")))
      throw error(403, "선택된 AI의 진행 중 프로젝트 작업에만 후보를 추가하세요.");
  }

  public Map<String, Object> begin(
      AuthenticatedPrincipal p,
      String grant,
      String workspace,
      String agent,
      String task,
      String name,
      long size,
      String hash,
      String parent) {
    return tx.execute(
        t -> {
          writableTask(p, workspace, agent, task);
          String clean = filename(name);
          if (size < 1 || size > MAX || hash == null || !hash.matches("[a-f0-9]{64}"))
            throw error(400, "파일 크기·SHA256 오류");
          if (parent != null && !metadata(p, parent).get("workspace_id").equals(workspace))
            throw error(403, "수정 제안 프로젝트 불일치");
          // Lock the grant before counting to keep concurrent upload reservations bounded.
          db.queryForList("SELECT id FROM mcp_grants WHERE id=? FOR UPDATE", grant);
          if (db.queryForObject(
                  "SELECT COUNT(*) FROM ai_collaboration_uploads WHERE grant_id=? AND"
                      + " status='UPLOADING'",
                  Integer.class,
                  grant)
              >= 5) throw error(429, "진행 중 업로드는 연결당5개까지입니다.");
          String id = UUID.randomUUID().toString();
          db.update(
              "INSERT INTO"
                  + " ai_collaboration_uploads(id,grant_id,workspace_id,task_id,agent,filename,expected_sha,expected_size,parent_id)"
                  + " VALUES(?,?,?,?,?,?,?,?,?)",
              id,
              grant,
              workspace,
              task,
              agent,
              clean,
              hash,
              size,
              parent);
          return Map.<String, Object>of(
              "uploadId",
              id,
              "chunkBytes",
              32768,
              "maxBytes",
              MAX,
              "status",
              "UPLOADING",
              "note",
              "후보 완성 전까지 정본/공유 파일에 반영되지 않습니다.");
        });
  }

  Map<String, Object> session(String grant, String workspace, String agent, String id) {
    var row =
        db.queryForList("SELECT * FROM ai_collaboration_uploads WHERE id=? FOR UPDATE", id).stream()
            .findFirst()
            .orElseThrow(() -> error(404, "업로드가 없습니다."));
    if (!row.get("grant_id").equals(grant)
        || !row.get("workspace_id").equals(workspace)
        || !row.get("agent").equals(agent)) throw error(403, "다른 AI·프로젝트의 업로드입니다.");
    return row;
  }

  public Map<String, Object> chunk(
      AuthenticatedPrincipal p,
      String grant,
      String workspace,
      String agent,
      String id,
      int index,
      String encoded) {
    spaces.require(p, workspace, true, false);
    if (encoded == null || encoded.length() > 43692 || index < 0 || index > 1024)
      throw error(413, "청크는 32768 byte 이하입니다.");
    byte[] data;
    try {
      data = Base64.getDecoder().decode(encoded);
    } catch (IllegalArgumentException invalid) {
      throw error(400, "base64 오류");
    }
    if (data.length < 1 || data.length > 32768) throw error(413, "청크 크기 오류");
    String hash = sha(data);
    return tx.execute(
        t -> {
          var row = session(grant, workspace, agent, id);
          if (!row.get("status").equals("UPLOADING")) throw error(409, "이미 완료된 업로드입니다.");
          var prior =
              db.queryForList(
                  "SELECT sha256 FROM ai_collaboration_chunks WHERE upload_id=? AND chunk_index=?",
                  id,
                  index);
          if (!prior.isEmpty()) {
            if (!prior.getFirst().get("sha256").equals(hash))
              throw error(409, "기존 청크를 덮어쓸 수 없습니다.");
            return Map.<String, Object>of(
                "receivedBytes",
                row.get("received_size"),
                "nextChunk",
                row.get("next_chunk"),
                "duplicate",
                true);
          }
          if (number(row, "next_chunk") != index
              || number(row, "received_size") + data.length > number(row, "expected_size"))
            throw error(409, "청크 순서/크기를 확인하세요.");
          db.update("INSERT INTO ai_collaboration_chunks VALUES(?,?,?,?)", id, index, hash, data);
          db.update(
              "UPDATE ai_collaboration_uploads SET"
                  + " received_size=received_size+?,next_chunk=next_chunk+1 WHERE id=?",
              data.length,
              id);
          return Map.<String, Object>of(
              "receivedBytes", number(row, "received_size") + data.length, "nextChunk", index + 1);
        });
  }

  public Map<String, Object> commit(
      AuthenticatedPrincipal p, String grant, String workspace, String agent, String id) {
    return tx.execute(
        t -> {
          spaces.require(p, workspace, true, false);
          var row = session(grant, workspace, agent, id);
          if (row.get("status").equals("COMPLETED"))
            return metadata(p, row.get("file_id").toString());
          writableTask(p, workspace, agent, row.get("task_id").toString());
          if (number(row, "received_size") != number(row, "expected_size"))
            throw error(409, "파일 수신이 완료되지 않았습니다.");
          var output = new ByteArrayOutputStream();
          for (var chunk :
              db.queryForList(
                  "SELECT data FROM ai_collaboration_chunks WHERE upload_id=? ORDER BY chunk_index",
                  id)) output.writeBytes((byte[]) chunk.get("data"));
          byte[] bytes = output.toByteArray();
          if (!sha(bytes).equals(row.get("expected_sha")))
            throw error(422, "원본 SHA256이 일치하지 않습니다.");
          var result =
              store(
                  p,
                  workspace,
                  row.get("filename").toString(),
                  bytes,
                  (String) row.get("parent_id"),
                  agent,
                  grant);
          db.update(
              "UPDATE ai_collaboration_uploads SET status='COMPLETED',file_id=? WHERE id=?",
              result.get("id"),
              id);
          db.update("DELETE FROM ai_collaboration_chunks WHERE upload_id=?", id);
          return result;
        });
  }

  public Map<String, Object> text(AuthenticatedPrincipal p, String id) {
    var file = metadata(p, id);
    byte[] bytes = bytes(p, id);
    String ext = file.get("extension").toString(), content;
    try {
      if (ext.equals("pdf")) {
        try (var pdf = org.apache.pdfbox.Loader.loadPDF(bytes)) {
          var extractor = new org.apache.pdfbox.text.PDFTextStripper();
          extractor.setEndPage(Math.min(pdf.getNumberOfPages(), 100));
          content = extractor.getText(pdf);
        }
      } else if (Set.of("md", "json", "html", "md5").contains(ext))
        content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
      else return Map.of("metadata", file, "note", "이미지·영상·ZIP 원본은 파일 열람으로 확인하세요.");
    } catch (IOException e) {
      throw error(422, "텍스트 열람 실패");
    }
    return Map.of(
        "metadata",
        file,
        "text",
        content.substring(0, Math.min(content.length(), 200000)),
        "truncated",
        content.length() > 200000);
  }
}
