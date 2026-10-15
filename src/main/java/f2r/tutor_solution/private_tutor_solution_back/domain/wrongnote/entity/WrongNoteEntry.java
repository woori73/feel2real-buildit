package f2r.tutor_solution.private_tutor_solution_back.domain.wrongnote.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "wrong_note_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WrongNoteEntry extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "entry_id")
  private Long entryId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "conversation_id", nullable = false)
  private Conversation conversation;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_id", nullable = false)
  private Source source;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "uploaded_by", nullable = false)
  private User uploadedBy;

  @Column(name = "title", nullable = false, length = 150)
  private String title;

  @Column(name = "exam_name", length = 150)
  private String examName;

  @Column(name = "exam_scope", length = 255)
  private String examScope;

  @Lob
  @Column(name = "memo", columnDefinition = "TEXT")
  private String memo;

  @Column(name = "tags", length = 255)
  private String tags;

  @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("orderNum ASC")
  private List<WrongNoteEntryFile> files = new ArrayList<>();

  @Builder
  public WrongNoteEntry(Conversation conversation, Source source, User uploadedBy, String title,
      String examName, String examScope, String memo, String tags) {
    this.conversation = conversation;
    this.source = source;
    this.uploadedBy = uploadedBy;
    this.title = title;
    this.examName = examName;
    this.examScope = examScope;
    this.memo = memo;
    this.tags = tags;
  }

  public void addFile(Source source, int orderNum) {
    files.add(WrongNoteEntryFile.builder()
        .entry(this)
        .source(source)
        .orderNum(orderNum)
        .build());
  }
}
