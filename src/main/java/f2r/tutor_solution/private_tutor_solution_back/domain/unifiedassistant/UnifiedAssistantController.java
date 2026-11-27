package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import com.fasterxml.jackson.databind.JsonNode;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/dashboard/assistant/v1")
@PreAuthorize("hasAnyRole('ADMIN','TUTOR')") @RequiredArgsConstructor
public class UnifiedAssistantController {
  private final UnifiedAssistantService service;
  private final AssistantCommandParser parser;
  private final AssistantConversationService conversation;
  private final CodiGeneralConversation general;
  @org.springframework.beans.factory.annotation.Value("${buildit.unified-assistant.allowed-origins:http://localhost:8080,https://feel2real.co.kr}")
  private List<String> allowedOrigins;
  public record ProgressRequest(int step,boolean completed,long version) {}
  public record DraftRequest(Long academyId,Long businessId,String operation,Map<String,Object> input) {}
  public record DecisionRequest(String planHash) {}
  public record CommandRequest(String message) {}
  public record ConversationRequest(Long academyId,Long businessId,String message,String mode,UUID conversationId,Boolean webSearch) {}
  @ModelAttribute
  void headers(HttpServletRequest req,org.springframework.web.context.request.NativeWebRequest web) {
    var response=web.getNativeResponse(jakarta.servlet.http.HttpServletResponse.class);
    if(response!=null)response.setHeader("Cache-Control","no-store");
    if(!Set.of("GET","HEAD").contains(req.getMethod())) {
      String origin=req.getHeader("Origin");
      // Configured deployment origins; never trust Host or forwarded headers as approval authority.
      if(!allowedOrigins.contains(origin==null?"":origin)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"동일 출처 요청만 허용합니다.");
    }
  }
  @GetMapping("/progress") public Object progress(@AuthenticationPrincipal AuthenticatedPrincipal p) { return service.state(p); }
  @PutMapping("/progress") public Object save(@AuthenticationPrincipal AuthenticatedPrincipal p,@RequestBody ProgressRequest r) {return service.saveProgress(p,r.step(),r.completed(),r.version());}
  @GetMapping("/overview") public Object overview(@AuthenticationPrincipal AuthenticatedPrincipal p,@RequestParam(required=false) Long businessId,@RequestParam(required=false) Long academyId) {return AssistantJson.plain(service.overview(p,businessId,academyId));}
  @GetMapping("/catalog/{kind}") public Object catalog(@AuthenticationPrincipal AuthenticatedPrincipal p,@PathVariable String kind,@RequestParam(required=false) Long academyId) {return AssistantJson.plain(service.catalog(p,kind,academyId));}
  @PostMapping("/command") public Object command(@AuthenticationPrincipal AuthenticatedPrincipal p,@RequestBody CommandRequest r) {service.actor(p);return parser.parse(r.message());}
  @PostMapping("/conversation") public ResponseEntity<?> conversation(@AuthenticationPrincipal AuthenticatedPrincipal p,@RequestBody ConversationRequest r) {
    if ("general".equals(r.mode())) {
      try {
        return ResponseEntity.ok(turn(general.send(p,r.academyId(),r.message(),r.conversationId(),!Boolean.FALSE.equals(r.webSearch()))));
      } catch (ResponseStatusException e) {
        String reason = e.getStatusCode().value() == 403 ? "선택한 학원에 연결된 원장만 일반 대화를 사용할 수 있습니다." : e.getReason();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", Objects.requireNonNullElse(reason, "일반 대화 요청을 처리하지 못했습니다.")));
      }
    }
    if (r.mode()!=null && !"work".equals(r.mode())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"대화 모드를 확인해 주세요.");
    return ResponseEntity.ok(turn(conversation.send(p,r.academyId(),r.businessId(),r.message())));
  }
  @PostMapping(value="/materials",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public Object material(@AuthenticationPrincipal AuthenticatedPrincipal p,@RequestParam(required=false) Long academyId,@RequestParam(required=false) Long businessId,@RequestPart("file") MultipartFile file) throws IOException {
    service.actor(p);
    String filename = file.getOriginalFilename();
    if (file.isEmpty()) throw new IllegalArgumentException("파일이 비어 있습니다.");
    int limit = AssistantMaterialAnalyzer.sizeLimit(AssistantMaterialAnalyzer.extension(filename));
    if (file.getSize() > limit) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, AssistantMaterialAnalyzer.sizeMessage(limit));
    return turn(conversation.upload(p,academyId,businessId,filename,file.getBytes()));
  }
  @PostMapping("/drafts") public Object draft(@AuthenticationPrincipal AuthenticatedPrincipal p,@RequestBody DraftRequest r) {return view(service.draft(p,r.academyId(),r.businessId(),r.operation(),AssistantJson.read(AssistantJson.write(r.input()))));}
  @PostMapping(value="/imports",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public Object upload(@AuthenticationPrincipal AuthenticatedPrincipal p,@RequestParam Long businessId,@RequestPart("file") MultipartFile file) throws IOException {
    service.actor(p); if(file.getSize()>262144)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"CSV는 256KB 이하로 올려주세요.");
    return view(service.upload(p,businessId,file.getOriginalFilename(),file.getBytes()));
  }
  @GetMapping("/drafts") public Object history(@AuthenticationPrincipal AuthenticatedPrincipal p) {return service.history(p).stream().map(this::view).toList();}
  @PostMapping("/drafts/{id}/approve") public Object approve(@AuthenticationPrincipal AuthenticatedPrincipal p,@PathVariable String id,@RequestBody DecisionRequest r) {return view(service.decide(p,id,r.planHash(),false));}
  @PostMapping("/drafts/{id}/cancel") public Object cancel(@AuthenticationPrincipal AuthenticatedPrincipal p,@PathVariable String id,@RequestBody DecisionRequest r) {return view(service.decide(p,id,r.planHash(),true));}
  public record AuditView(Long id,Long ownerId,String draftId,String event,String operation,Long academyId,Long businessId,String createdAt) {}
  @GetMapping("/audit") public Object audit(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return service.audit(p).stream().map(row->new AuditView(row.getId(),row.getOwnerId(),row.getDraftId(),row.getEvent(),row.getOperation(),row.getAcademyId(),row.getBusinessId(),row.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toString())).toList();
  }
  private Map<String,Object> turn(AssistantConversationService.Turn t) {
    var out=new LinkedHashMap<String,Object>();out.put("message",t.message());out.put("analysis",t.analysis());out.put("suggestion",t.suggestion());
    out.put("drafts",t.drafts().stream().map(this::view).toList());out.put("issues",t.issues());out.put("overview",t.overview()==null?null:AssistantJson.plain(t.overview()));out.put("data",t.data());return out;
  }
  private Map<String,Object> view(AssistantDraft d) {
    var v=new LinkedHashMap<String,Object>(); v.put("id",d.getId());v.put("operation",d.getOperation());v.put("academyId",d.getAcademyId());v.put("businessId",d.getBusinessId());
    v.put("preview",AssistantJson.plain(AssistantJson.read(d.getPreview())));v.put("planHash",d.getPlanHash());v.put("status",d.getStatus());v.put("createdAt",d.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toString());v.put("expiresAt",d.getExpiresAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toString());v.put("result",d.getResult()==null?null:AssistantJson.plain(AssistantJson.read(d.getResult())));return v;
  }
}
