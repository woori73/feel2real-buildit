package f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.entity.Worksheet;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.entity.WorksheetAssignment;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.entity.WorksheetFavorite;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.entity.WorksheetItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.repository.WorksheetAssignmentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.repository.WorksheetFavoriteRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.repository.WorksheetItemRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.repository.WorksheetRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service.WorksheetQuestionBank.Question;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학습지 만들기·고치기·휴지통·즐겨찾기. 사업체 접근 권한은 컨트롤러가 먼저 확인하고 들어온다.
 * 고치기·휴지통·복구·영구 삭제는 만든 사람과 관리자(ADMIN)만 한다.
 */
@Service
@Transactional
public class WorksheetService {

  public static final int MAX_QUESTIONS = 100;
  public static final List<String> TABS = List.of("ACADEMY", "MINE", "FAVORITE", "TRASH");

  private final WorksheetRepository worksheets;
  private final WorksheetItemRepository items;
  private final WorksheetFavoriteRepository favorites;
  private final WorksheetAssignmentRepository assignments;
  private final WorksheetQuestionBank bank;
  private final UserRepository users;

  public WorksheetService(WorksheetRepository worksheets, WorksheetItemRepository items,
      WorksheetFavoriteRepository favorites, WorksheetAssignmentRepository assignments, WorksheetQuestionBank bank,
      UserRepository users) {
    this.worksheets = worksheets;
    this.items = items;
    this.favorites = favorites;
    this.assignments = assignments;
    this.bank = bank;
    this.users = users;
  }

  public record SaveRequest(String title, String description, String status, List<String> refs) {}

  /** 배정 수(상태별). 학생 이름은 담지 않는다. */
  public record AssignCount(int notStarted, int inProgress, int done) {
    public int total() {
      return notStarted + inProgress + done;
    }
  }

  public record Row(Long worksheetId, String title, String description, String status, int questionCount,
      String ownerName, boolean mine, boolean canEdit, boolean favorite, AssignCount assigned, String updatedAt,
      String deletedAt) {}

  public record Detail(Row worksheet, List<Question> questions, int missingCount) {}

  /** 목록. tab = ACADEMY(우리 학원) / MINE(내 학습지) / FAVORITE(즐겨찾기) / TRASH(휴지통). */
  @Transactional(readOnly = true)
  public List<Row> list(Long businessId, String tab, AuthenticatedPrincipal p) {
    if (!TABS.contains(tab)) throw new IllegalArgumentException("목록 구분을 확인해 주세요.");
    boolean trash = "TRASH".equals(tab);
    List<Worksheet> found = trash
        ? worksheets.findAllByBusinessIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(businessId)
        : worksheets.findAllByBusinessIdAndDeletedAtIsNullOrderByUpdatedAtDesc(businessId);
    Set<Long> favoriteIds = favoriteIds(p);
    List<Worksheet> shown = found.stream().filter(w -> switch (tab) {
      case "MINE" -> isOwner(w, p);
      case "FAVORITE" -> favoriteIds.contains(w.getWorksheetId()) && visible(w, p);
      case "TRASH" -> canEdit(w, p);
      default -> visible(w, p);
    }).toList();
    Map<Long, AssignCount> counts = counts(shown.stream().map(Worksheet::getWorksheetId).toList());
    return shown.stream().map(w -> row(w, p, favoriteIds, counts.get(w.getWorksheetId()))).toList();
  }

  /** 상세(문항 포함). 출처에서 없어진 문항은 빠지고 missingCount로 알려 준다. */
  @Transactional(readOnly = true)
  public Detail detail(Long businessId, Long worksheetId, AuthenticatedPrincipal p) {
    Worksheet w = find(businessId, worksheetId);
    if (w.isDeleted() ? !canEdit(w, p) : !visible(w, p)) throw new AccessDeniedException("볼 수 없는 학습지입니다.");
    List<String> refs = items.findAllByWorksheetIdOrderByOrderNoAsc(worksheetId).stream().map(WorksheetItem::ref).toList();
    List<Question> questions = bank.resolve(refs);
    return new Detail(row(w, p, favoriteIds(p), counts(List.of(worksheetId)).get(worksheetId)), questions,
        refs.size() - questions.size());
  }

  public Row create(Long businessId, SaveRequest req, AuthenticatedPrincipal p) {
    List<Question> picked = validated(req);
    LocalDateTime now = LocalDateTime.now();
    Worksheet w = worksheets.save(new Worksheet(businessId, p.userId(), ownerName(p), title(req),
        description(req), status(req), picked.size(), now));
    saveItems(w.getWorksheetId(), picked);
    return row(w, p, Set.of(), null);
  }

  public Row update(Long businessId, Long worksheetId, SaveRequest req, AuthenticatedPrincipal p) {
    Worksheet w = editable(businessId, worksheetId, p);
    if (w.isDeleted()) throw new IllegalArgumentException("휴지통에 있는 학습지는 복구한 뒤 고칠 수 있습니다.");
    List<Question> picked = validated(req);
    w.edit(title(req), description(req), status(req), picked.size(), LocalDateTime.now());
    items.deleteAllByWorksheetId(worksheetId);
    items.flush();
    saveItems(worksheetId, picked);
    return row(w, p, favoriteIds(p), counts(List.of(worksheetId)).get(worksheetId));
  }

  /** 휴지통으로 보낸다. 배정은 그대로 두고, 복구하면 다시 보인다. */
  public void trash(Long businessId, Long worksheetId, AuthenticatedPrincipal p) {
    editable(businessId, worksheetId, p).moveToTrash(p.userId(), LocalDateTime.now());
  }

  public void restore(Long businessId, Long worksheetId, AuthenticatedPrincipal p) {
    editable(businessId, worksheetId, p).restore(LocalDateTime.now());
  }

  /** 영구 삭제. 휴지통에 있는 것만, 배정 중인 학생이 없을 때만 지운다. */
  public void purge(Long businessId, Long worksheetId, AuthenticatedPrincipal p) {
    Worksheet w = editable(businessId, worksheetId, p);
    if (!w.isDeleted()) throw new IllegalArgumentException("휴지통으로 보낸 학습지만 영구 삭제할 수 있습니다.");
    if (!assignments.findAllByWorksheetIdInAndRemovedAtIsNull(List.of(worksheetId)).isEmpty()) {
      throw new IllegalArgumentException("배정된 학생이 있습니다. 배정을 먼저 빼 주세요.");
    }
    assignments.deleteAllByWorksheetId(worksheetId);
    favorites.deleteAllByWorksheetId(worksheetId);
    items.deleteAllByWorksheetId(worksheetId);
    worksheets.delete(w);
  }

  /** 즐겨찾기를 켜거나 끈다. 켠 뒤의 상태를 돌려준다. */
  public boolean favorite(Long businessId, Long worksheetId, boolean on, AuthenticatedPrincipal p) {
    Worksheet w = find(businessId, worksheetId);
    if (w.isDeleted() || !visible(w, p)) throw new AccessDeniedException("볼 수 없는 학습지입니다.");
    var existing = favorites.findByWorksheetIdAndUserId(worksheetId, p.userId());
    if (on && existing.isEmpty()) favorites.save(new WorksheetFavorite(worksheetId, p.userId(), LocalDateTime.now()));
    if (!on) existing.ifPresent(favorites::delete);
    return on;
  }

  /** 다른 사업체의 학습지 번호는 없는 것으로 본다. */
  Worksheet find(Long businessId, Long worksheetId) {
    return worksheets.findById(worksheetId).filter(w -> w.getBusinessId().equals(businessId))
        .orElseThrow(() -> new IllegalArgumentException("학습지를 찾을 수 없습니다."));
  }

  private Worksheet editable(Long businessId, Long worksheetId, AuthenticatedPrincipal p) {
    Worksheet w = find(businessId, worksheetId);
    if (!canEdit(w, p)) throw new AccessDeniedException("만든 사람과 관리자만 할 수 있습니다.");
    return w;
  }

  private List<Question> validated(SaveRequest req) {
    if (req == null || title(req).isEmpty()) throw new IllegalArgumentException("학습지 이름을 적어 주세요.");
    if (title(req).length() > 120) throw new IllegalArgumentException("학습지 이름은 120자까지 적을 수 있습니다.");
    if (description(req) != null && description(req).length() > 500) {
      throw new IllegalArgumentException("설명은 500자까지 적을 수 있습니다.");
    }
    status(req);
    List<String> refs = req.refs() == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(req.refs()));
    if (refs.isEmpty()) throw new IllegalArgumentException("문항을 한 개 이상 골라 주세요.");
    if (refs.size() > MAX_QUESTIONS) {
      throw new IllegalArgumentException("한 학습지에는 " + MAX_QUESTIONS + "문항까지 넣을 수 있습니다.");
    }
    List<Question> picked = bank.resolve(refs);
    if (picked.size() != refs.size()) throw new IllegalArgumentException("찾을 수 없는 문항이 있습니다. 다시 골라 주세요.");
    return picked;
  }

  private void saveItems(Long worksheetId, List<Question> picked) {
    List<WorksheetItem> rows = new ArrayList<>();
    for (int i = 0; i < picked.size(); i++) {
      String ref = picked.get(i).ref();
      int cut = ref.indexOf(':');
      rows.add(new WorksheetItem(worksheetId, i + 1, ref.substring(0, cut), ref.substring(cut + 1)));
    }
    items.saveAll(rows);
  }

  private static String title(SaveRequest req) {
    return req.title() == null ? "" : req.title().strip();
  }

  private static String description(SaveRequest req) {
    return req.description() == null || req.description().isBlank() ? null : req.description().strip();
  }

  private static Worksheet.Status status(SaveRequest req) {
    if (req.status() == null || req.status().isBlank()) return Worksheet.Status.ACTIVE;
    try {
      return Worksheet.Status.valueOf(req.status());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("자료 상태를 확인해 주세요.");
    }
  }

  private String ownerName(AuthenticatedPrincipal p) {
    String name = users.findById(p.userId()).map(User::getName).orElse(null);
    String shown = name == null || name.isBlank() ? p.loginId() : name.strip();
    return shown == null ? "" : shown.substring(0, Math.min(50, shown.length()));
  }

  private Set<Long> favoriteIds(AuthenticatedPrincipal p) {
    return favorites.findAllByUserId(p.userId()).stream().map(WorksheetFavorite::getWorksheetId)
        .collect(Collectors.toSet());
  }

  private Map<Long, AssignCount> counts(List<Long> worksheetIds) {
    if (worksheetIds.isEmpty()) return Map.of();
    return assignments.findAllByWorksheetIdInAndRemovedAtIsNull(worksheetIds).stream()
        .collect(Collectors.groupingBy(WorksheetAssignment::getWorksheetId, Collectors.collectingAndThen(
            Collectors.toList(), WorksheetService::count)));
  }

  static AssignCount count(List<WorksheetAssignment> list) {
    int notStarted = 0;
    int inProgress = 0;
    int done = 0;
    for (WorksheetAssignment a : list) {
      switch (a.getStatus()) {
        case NOT_STARTED -> notStarted++;
        case IN_PROGRESS -> inProgress++;
        case DONE -> done++;
      }
    }
    return new AssignCount(notStarted, inProgress, done);
  }

  private Row row(Worksheet w, AuthenticatedPrincipal p, Set<Long> favoriteIds, AssignCount count) {
    return new Row(w.getWorksheetId(), w.getTitle(), w.getDescription(), w.getStatus().name(), w.getQuestionCount(),
        w.getOwnerName(), isOwner(w, p), canEdit(w, p), favoriteIds.contains(w.getWorksheetId()),
        count == null ? new AssignCount(0, 0, 0) : count, String.valueOf(w.getUpdatedAt()),
        w.getDeletedAt() == null ? null : String.valueOf(w.getDeletedAt()));
  }

  private static boolean isOwner(Worksheet w, AuthenticatedPrincipal p) {
    return w.getOwnerUserId().equals(p.userId());
  }

  private static boolean canEdit(Worksheet w, AuthenticatedPrincipal p) {
    return isOwner(w, p) || "ADMIN".equals(p.role());
  }

  /** 숨김 학습지는 만든 사람과 관리자만 본다. */
  private static boolean visible(Worksheet w, AuthenticatedPrincipal p) {
    return w.getStatus() != Worksheet.Status.HIDDEN || canEdit(w, p);
  }
}
