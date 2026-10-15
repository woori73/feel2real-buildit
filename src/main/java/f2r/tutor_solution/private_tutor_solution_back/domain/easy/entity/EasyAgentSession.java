package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
@Entity @Table(name="easy_agent_sessions") @Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class EasyAgentSession {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="session_id") private Long sessionId;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="business_id",nullable=false) private EasyBusiness business;
 @Column(name="owner_user_id",nullable=false) private Long ownerUserId;
 @CreationTimestamp @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
 public EasyAgentSession(EasyBusiness business,Long ownerUserId){this.business=business;this.ownerUserId=ownerUserId;}
}
