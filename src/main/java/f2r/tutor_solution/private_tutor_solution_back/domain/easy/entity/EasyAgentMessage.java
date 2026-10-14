package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
@Entity @Table(name="easy_agent_messages") @Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class EasyAgentMessage {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="message_id") private Long messageId;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="session_id",nullable=false) private EasyAgentSession session;
 @Column(name="role",nullable=false,length=10) private String role;
 @Column(name="kind",nullable=false,length=20) private String kind;
 @Column(name="content",nullable=false,columnDefinition="TEXT") private String content;
 @Column(name="payload_json",columnDefinition="TEXT") private String payloadJson;
 @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
 public EasyAgentMessage(EasyAgentSession session,String role,String kind,String content,String payloadJson){
  this.session=session;this.role=role;this.kind=kind;this.content=content;this.payloadJson=payloadJson;
 }
}
