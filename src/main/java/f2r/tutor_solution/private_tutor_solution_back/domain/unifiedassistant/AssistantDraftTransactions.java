package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Claim commits before a remote API call. A timeout/crash must never cause an automatic replay. */
@Service @RequiredArgsConstructor
public class AssistantDraftTransactions {
  private final AssistantDraftRepository drafts;
  private final AssistantAuditRepository audit;
  @Transactional
  public AssistantDraft claim(Long owner,String role,String id,String hash,boolean cancel) {
    var d=drafts.ownedForUpdate(id,owner).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    if(!d.getRole().equals(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    if(!d.calculatedHash().equals(d.getPlanHash()) || !d.getPlanHash().equals(hash)) throw new ResponseStatusException(HttpStatus.CONFLICT,"본 초안과 승인 해시가 다릅니다.");
    if(d.getStatus().equals("VERIFIED") || d.getStatus().equals("REVIEW_REQUIRED")) return d;
    if(!d.getStatus().equals("PENDING")) throw new ResponseStatusException(HttpStatus.CONFLICT,"이미 처리했거나 실행 결과 확인이 필요한 초안입니다.");
    if(d.getExpiresAt().isBefore(LocalDateTime.now())) {
      d.finish("EXPIRED",null); audit.save(new AssistantAudit(owner,id,"EXPIRED")); return d;
    }
    // OSIV may already hold a PENDING instance loaded before this lock. The conditional
    // database update is the authority even when that managed entity is stale.
    String next=cancel?"CANCELLED":"EXECUTING";
    if(drafts.claimPending(id,owner,next,LocalDateTime.now())!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"다른 요청에서 이미 처리 중인 초안입니다.");
    d.finish(next,null);
    audit.save(new AssistantAudit(owner,id,cancel?"CANCELLED":"APPROVED"));
    return d;
  }
  @Transactional
  public AssistantDraft finish(Long owner,String id,String status,String result) {
    var d=drafts.ownedForUpdate(id,owner).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    if(!d.getStatus().equals("EXECUTING")) throw new ResponseStatusException(HttpStatus.CONFLICT);
    d.finish(status,result); audit.save(new AssistantAudit(owner,id,status)); return d;
  }
}
