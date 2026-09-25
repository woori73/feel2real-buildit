package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.jipijigi.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** 서버측 게시 allowlist — 이 행(enabled, menu_id 확인됨) 외 게시판에는 게시할 수 없다. */
@Entity
@Table(name = "newstart_jipijigi_target_boards",
        uniqueConstraints = @UniqueConstraint(name = "uk_jipijigi_board", columnNames = {"club_id", "menu_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JipijigiTargetBoard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "board_row_id")
    private Long boardRowId;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    /** NAVER에서 운영자가 실제 확인한 값만 저장. 확인 전 NULL → 게시 준비 불가. */
    @Column(name = "menu_id")
    private Long menuId;

    @Column(name = "board_name", nullable = false, length = 100)
    private String boardName;

    @Column(name = "cafe_name", nullable = false, length = 100)
    private String cafeName;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "memo", length = 500)
    private String memo;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 게시 가능 조건: enable됨 + menuid를 NAVER에서 실제 확인 완료. */
    public boolean isPostable() {
        return enabled && menuId != null;
    }
}
