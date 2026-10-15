package f2r.tutor_solution.private_tutor_solution_back.domain.wemade.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** 주주 정보(커뮤니티). 주가 사실 데이터와 분리 보관. 사실로 승격 금지. */
@Entity
@Table(name = "wemade_community_posts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WemadeCommunityPost extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDateTime postedAt;
    @Column(length = 500)
    private String title;
    @Column(length = 2000)
    private String excerpt;
    private Integer viewCount;
    private Integer likeCount;
    private Boolean holderVerified;
    @Column(length = 500)
    private String mentions;
    @Builder.Default
    private Boolean needsFactCheck = false;
    @Builder.Default
    @Column(length = 20)
    private String factCheckStatus = "NONE";
    @Builder.Default
    @Column(length = 100)
    private String source = "MANUAL";
    @Column(length = 1000)
    private String sourceUrl;
    @Column(length = 64, unique = true, nullable = false)
    private String contentHash;
    private LocalDateTime collectedAt;
}
