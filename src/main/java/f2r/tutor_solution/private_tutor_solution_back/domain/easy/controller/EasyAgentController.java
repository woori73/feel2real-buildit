package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyAgentDtos.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/tutor/easy/agent") @RequiredArgsConstructor
@PreAuthorize("hasRole('TUTOR')")
public class EasyAgentController {
 private final EasyAgentService service;
 @PostMapping("/sessions")
 public ResponseEntity<CreateSessionResponse> create(@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal p,@RequestParam Long businessId){
  return ResponseEntity.ok(service.createSession(p,businessId));
 }
 @GetMapping("/sessions/{sessionId}")
 public ResponseEntity<SessionResponse> session(@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal p,@RequestParam Long businessId,@PathVariable Long sessionId){
  return ResponseEntity.ok(service.session(p,businessId,sessionId));
 }
 @PostMapping("/sessions/{sessionId}/messages")
 public ResponseEntity<ChatResponse> chat(@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal p,@RequestParam Long businessId,@PathVariable Long sessionId,@RequestBody ChatRequest request){
  return ResponseEntity.ok(service.chat(p,businessId,sessionId,request.message()));
 }
 @PostMapping("/actions/{actionId}/approve")
 public ResponseEntity<ActionResponse> approve(@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal p,@RequestParam Long businessId,@PathVariable Long actionId){
  return ResponseEntity.ok(service.approve(p,businessId,actionId));
 }
 @PostMapping("/actions/{actionId}/reject")
 public ResponseEntity<ActionResponse> reject(@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal p,@RequestParam Long businessId,@PathVariable Long actionId){
  return ResponseEntity.ok(service.reject(p,businessId,actionId));
 }
}
