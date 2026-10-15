package f2r.tutor_solution.private_tutor_solution_back.domain.admissions.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "admissions_youtube_analyses",
        indexes = {
                @Index(name = "idx_admissions_yt_knowledge", columnList = "related_knowledge_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdmissionsYoutubeAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "analysis_id")
    private Long analysisId;

    @Column(name = "video_title", length = 300)
    private String videoTitle;

    @Column(name = "video_url", length = 1000, nullable = false)
    private String videoUrl;

    @Column(name = "channel", length = 200)
    private String channel;

    @Column(name = "analysis_summary", columnDefinition = "TEXT")
    private String analysisSummary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_knowledge_id")
    private AdmissionsKnowledgeItem relatedKnowledge;

    @Column(name = "reliability", length = 20)
    private String reliability;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
