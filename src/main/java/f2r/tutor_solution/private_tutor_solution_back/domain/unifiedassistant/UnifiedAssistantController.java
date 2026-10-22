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
  public record ProgressRequest(int step,boolean completed,long version) {}
  public record DraftRequest(Long academyId,Long businessId,String operation,Map<String,Object> input) {}
  public record DecisionRequest(String planHash) {}
  public record CommandRequest(String message) {}
  @ModelAttribute
  void headers(HttpServletRequest req,org.springframework.web.context.request.NativeWebRequest web) {
    var response=web.getNativeResponse(jakarta.servlet.http.HttpServletResponse.class);
    if(response!=null)response.setHeader("Cache-Control","no-store");
    if(!Set.of("GET","HEAD").contains(req.getMethod())) {
      String origin=req.getHeader("Origin");
      // Fixed deployment origins; never trust Host or forwarded headers as approval authority.
      if(!Set.of("http://localhost:8080","https://feel2real.co.kr").contains(origin==null?"":origin)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"동일 출처 요청만 허용합니다.");
    }
  }
  @GetMapping("/progress") public Object progress(@AuthenticationPrincipal AuthenticatedPrincipal p) { return service.state(p); }
  @PutMapping("/progress") public Object save(@AuthenticationPrincipal AuthenticatedPrincipal p,@RequestBody ProgressRequest r) {return service.saveProgress(p,r.step(),r.completed(),r.version());}
  @GetMapping("/overview") public Object overview(@AuthenticationPrincipal AuthenticatedPrincipal p,@RequestParam(required=false) Long businessId,@RequestParam(required=false) Long academyId) {return AssistantJson.plain(service.overview(p,businessId,academyId));}
  @GetMapping("/catalog/{kind}") public Object catalog(@AuthenticationPrincipal AuthenticatedPrincipal p,@PathVariable String kind,@RequestParam(required=false) Long academyId) {return AssistantJson.plain(service.catalog(p,kind,academyId));}
  @PostMapping("/command") public Object command(@AuthenticationPrincipal AuthenticatedPrincipal p,@RequestBody CommandRequest r) {service.actor(p);return parser.parse(r.message());}
  @PostMapping("/drafts") public Object draft(@AuthenticationPrincipal AuthenticatedPrincipal p,@RequestBody DraftRequest r) {return view(service.draft(p,r.academyId(),r.businessId(),r.operation(),AssistantJson.read(AssistantJson.write(r.input()))));}
  @PostMapping(value="/imports",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
  public Object upload(@AuthenticationPrincipal AuthenticatedPrincipal p,@RequestParam Long businessId,@RequestPart("file") MultipartFile file) throws IOException {
    service.actor(p); if(file.getSize()>262144)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"CSV는 256KB 이하로 올려주세요.");
    return view(service.upload(p,businessId,file.getOriginalFilename(),file.getBytes()));
  }
  @GetMapping("/drafts") public Object history(@AuthenticationPrincipal AuthenticatedPrincipal p) {return service.history(p).stream().map(this::view).toList();}
  @PostMapping("/drafts/{id}/approve") public Object approve(@AuthenticationPrincipal AuthenticatedPrincipal p,@PathVariable String id,@RequestBody DecisionRequest r) {return view(service.decide(p,id,r.planHash(),false));}
  @PostMapping("/drafts/{id}/cancel") public Object cancel(@AuthenticationPrincipal AuthenticatedPrincipal p,@PathVariable String id,@RequestBody DecisionRequest r) {return view(service.decide(p,id,r.planHash(),true));}
  public record AuditView(Long id,Long ownerId,String draftId,String event,String createdAt) {}
  @GetMapping("/audit") public Object audit(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return service.audit(p).stream().map(row->new AuditView(row.getId(),row.getOwnerId(),row.getDraftId(),row.getEvent(),row.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toString())).toList();
  }
  private Map<String,Object> view(AssistantDraft d) {
    var v=new LinkedHashMap<String,Object>(); v.put("id",d.getId());v.put("operation",d.getOperation());v.put("academyId",d.getAcademyId());v.put("businessId",d.getBusinessId());
    v.put("preview",AssistantJson.plain(AssistantJson.read(d.getPreview())));v.put("planHash",d.getPlanHash());v.put("status",d.getStatus());v.put("createdAt",d.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toString());v.put("expiresAt",d.getExpiresAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toString());v.put("result",d.getResult()==null?null:AssistantJson.plain(AssistantJson.read(d.getResult())));return v;
  }
}
