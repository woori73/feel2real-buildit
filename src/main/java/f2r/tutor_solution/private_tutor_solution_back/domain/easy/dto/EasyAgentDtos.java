package f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.*;
import java.time.LocalDateTime;
import java.util.List;
public final class EasyAgentDtos {
 private EasyAgentDtos(){}
 public record CreateSessionResponse(Long sessionId,Long businessId){}
 public record ChatRequest(String message){}
 public record MessageResponse(Long messageId,String role,String kind,String content,String payloadJson,LocalDateTime createdAt){
  public static MessageResponse from(EasyAgentMessage m){return new MessageResponse(m.getMessageId(),m.getRole(),m.getKind(),m.getContent(),m.getPayloadJson(),m.getCreatedAt());}
 }
 public record ActionResponse(Long actionId,String type,String status,String summary,Long batchId,Long rowId,Long accountId,LocalDateTime expiresAt,String failureReason){
  public static ActionResponse from(EasyAgentAction a){return new ActionResponse(a.getActionId(),a.getActionType().name(),a.getStatus().name(),a.getSummary(),a.getBatchId(),a.getRowId(),a.getAccountId(),a.getExpiresAt(),a.getFailureReason());}
 }
 public record ChatResponse(MessageResponse message,ActionResponse pendingAction){}
 public record SessionResponse(Long sessionId,Long businessId,List<MessageResponse> messages){}
}
