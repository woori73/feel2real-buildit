package f2r.tutor_solution.private_tutor_solution_back.domain.wrongnote.entity;

import f2r.tutor_solution.private_tutor_solution_back.domain.BaseEntity;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.entity.Source;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "wrong_note_entry_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WrongNoteEntryFile extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "entry_file_id")
  private Long entryFileId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "entry_id", nullable = false)
  private WrongNoteEntry entry;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "source_id", nullable = false)
  private Source source;

  @Column(name = "order_num", nullable = false)
  private int orderNum;

  @Builder
  public WrongNoteEntryFile(WrongNoteEntry entry, Source source, int orderNum) {
    this.entry = entry;
    this.source = source;
    this.orderNum = orderNum;
  }
}
