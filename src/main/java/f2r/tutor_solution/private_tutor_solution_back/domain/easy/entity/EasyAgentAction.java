package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
@Entity @Table(name="easy_agent_actions") @Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class EasyAgentAction {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="action_id") private Long actionId;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="session_id",nullable=false) private EasyAgentSession session;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="business_id",nullable=false) private EasyBusiness business;
 @Column(name="owner_user_id",nullable=false) private Long ownerUserId;
 @Enumerated(EnumType.STRING) @Column(name="action_type",nullable=false,length=30) private EasyAgentActionType actionType;
 @Enumerated(EnumType.STRING) @Column(name="status",nullable=false,length=20) private EasyAgentActionStatus status=EasyAgentActionStatus.PROPOSED;
 @Column(name="batch_id") private Long batchId;
 @Column(name="row_id") private Long rowId;
 @Column(name="account_id") private Long accountId;
 @Column(name="expected_account_id") private Long expectedAccountId;
 @Column(name="expected_row_status",length=20) private String expectedRowStatus;
 @Column(name="row_ids_json",columnDefinition="TEXT") private String rowIdsJson;
 @Column(name="summary",nullable=false,length=500) private String summary;
 @Column(name="expires_at",nullable=false) private LocalDateTime expiresAt;
 @Column(name="applied_at") private LocalDateTime appliedAt;
 @Column(name="failure_reason",length=500) private String failureReason;
 @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
 @UpdateTimestamp @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
 public static EasyAgentAction selectAccount(EasyAgentSession s,EasyBusiness b,Long u,Long batch,Long row,
   Long account,Long expected,String expectedStatus,String summary,LocalDateTime expires){
  EasyAgentAction a=base(s,b,u,EasyAgentActionType.SELECT_ACCOUNT,summary,expires);
  a.batchId=batch;a.rowId=row;a.accountId=account;a.expectedAccountId=expected;a.expectedRowStatus=expectedStatus;return a;
 }
 public static EasyAgentAction confirmImport(EasyAgentSession s,EasyBusiness b,Long u,Long batch,
   String rows,String summary,LocalDateTime expires){
  EasyAgentAction a=base(s,b,u,EasyAgentActionType.CONFIRM_IMPORT,summary,expires);
  a.batchId=batch;a.rowIdsJson=rows;return a;
 }
 private static EasyAgentAction base(EasyAgentSession s,EasyBusiness b,Long u,EasyAgentActionType t,
   String summary,LocalDateTime expires){
  EasyAgentAction a=new EasyAgentAction();a.session=s;a.business=b;a.ownerUserId=u;
  a.actionType=t;a.summary=summary;a.expiresAt=expires;return a;
 }
 public void markApplied(){status=EasyAgentActionStatus.APPLIED;appliedAt=LocalDateTime.now();}
 public void markRejected(){status=EasyAgentActionStatus.REJECTED;}
 public void markStale(String reason){status=EasyAgentActionStatus.STALE;failureReason=reason;}
 public void markExpired(){status=EasyAgentActionStatus.EXPIRED;failureReason="제안 유효시간이 지났습니다.";}
 public void markFailed(String reason){status=EasyAgentActionStatus.FAILED;failureReason=reason;}
}
