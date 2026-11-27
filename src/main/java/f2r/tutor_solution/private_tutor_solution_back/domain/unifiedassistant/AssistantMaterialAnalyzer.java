package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import f2r.tutor_solution.private_tutor_solution_back.domain.academyhome.MaterialTextExtractor;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCsvFileAdapter;
import java.util.stream.Collectors;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Parses material only. All authority, staging and approval remain in the existing services. */
@Component @RequiredArgsConstructor
public class AssistantMaterialAnalyzer {
  private final AssistantIntentAnalyzer intents;
  public record Item(int row, AssistantCommandParser.Suggestion suggestion) {}
  static final int DOCUMENT_LIMIT = 5 * 1024 * 1024;
  static final int INSTRUCTION_LIMIT = 256 * 1024;
  private static final int PREVIEW_CHARS = 1200;
  public record DocumentPreview(String format, String status, String text, boolean truncated, Integer pageCount) {}
  public record DocumentRead(DocumentPreview preview, CardMerchantNameGuide.Guide guide, String message) {}
  public record Analysis(String kind, int rows, List<Item> items, List<String> issues, DocumentRead document) {
    public Analysis(String kind, int rows, List<Item> items, List<String> issues) { this(kind,rows,items,issues,null); }
  }

  static String extension(String filename) {
    if (filename == null || !filename.contains(".")) return "";
    return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
  }

  static int sizeLimit(String extension) {
    return switch (extension) {
      case "html", "htm", "pdf" -> DOCUMENT_LIMIT;
      case "csv", "txt" -> INSTRUCTION_LIMIT;
      default -> throw new IllegalArgumentException("HTML·PDF 문서, 학생 명단·거래 CSV 또는 업무 지시 TXT를 올려주세요.");
    };
  }

  static String sizeMessage(int limit) {
    return limit == DOCUMENT_LIMIT ? "HTML·PDF는 5MB 이하로 올려주세요." : "CSV·TXT는 256KB 이하로 올려주세요.";
  }

  private Analysis document(String ext, byte[] bytes) {
    var result = MaterialTextExtractor.extract(ext, bytes);
    String text = result.blocks().stream().map(MaterialTextExtractor.Block::text)
        .filter(Objects::nonNull).collect(Collectors.joining("\n\n"));
    var guide = "READ".equals(result.status()) ? CardMerchantNameGuide.create(text).orElse(null) : null;
    String preview = "";
    boolean truncated = result.truncated();
    if (guide == null && !text.isEmpty()) {
      int end = text.offsetByCodePoints(0, Math.min(PREVIEW_CHARS, text.codePointCount(0,text.length())));
      preview = text.substring(0,end);
      truncated |= end < text.length();
    }
    String format = ext.equals("pdf") ? "PDF" : "HTML";
    String message = result.reason();
    if (guide != null) message = (result.truncated() ? "읽을 수 있는 문서 앞부분에서 " : "문서를 읽고 ")
        + "카드사별 가맹점명 변경 문의 초안과 진행표를 정리했습니다.";
    else if ("READ".equals(result.status())) message = format + " 문서에서 읽은 본문을 확인해 주세요.";
    var details = new DocumentRead(new DocumentPreview(format, result.status(), preview, truncated, result.pageCount()), guide, message);
    return new Analysis("READ_ONLY_DOCUMENT", 0, List.of(), List.of(), details);
  }
  private static final Map<String,String> STUDENT_HEADERS=Map.ofEntries(
      Map.entry("이름","name"),Map.entry("학생명","name"),Map.entry("학생이름","name"),Map.entry("name","name"),
      Map.entry("학년","grade"),Map.entry("grade","grade"),Map.entry("학교","schoolName"),Map.entry("schoolname","schoolName"),
      Map.entry("학부모이름","parentName"),Map.entry("parentname","parentName"),
      Map.entry("학부모연락처","parentPhone"),Map.entry("parentphone","parentPhone"));

  public Analysis analyze(Long userId, String filename, byte[] bytes) {
    String ext = extension(filename);
    int limit = sizeLimit(ext);
    if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("파일이 비어 있습니다.");
    if (bytes.length > limit) throw new IllegalArgumentException(sizeMessage(limit));
    if (Set.of("html", "htm", "pdf").contains(ext)) return document(ext, bytes);
    String text=EasyCsvFileAdapter.decode(bytes);
    String start = text.stripLeading().toLowerCase(Locale.ROOT);
    if (start.startsWith("%pdf-") || start.startsWith("<!doctype html") || start.startsWith("<html")) {
      throw new IllegalArgumentException("HTML·PDF 문서는 원래 확장자로 올려주세요.");
    }
    if(text.indexOf('\0')>=0) throw new IllegalArgumentException("텍스트 자료를 확인하세요.");
    var items=new ArrayList<Item>(); var issues=new ArrayList<String>();
    if(ext.equals("txt")) {
      var allowance=intents.newAllowance();
      var lines=text.lines().filter(line->!line.isBlank()).toList();
      limit(lines.size());
      for(int i=0;i<lines.size();i++) {
        if(lines.get(i).length()>2000) {issues.add((i+1)+"행: 지시는 2000자 이하로 작성하세요.");continue;}
        items.add(new Item(i+1,intents.analyze(userId,lines.get(i),allowance).suggestion()));
      }
      return new Analysis("업무 지시 텍스트",lines.size(),items,issues);
    }
    var lines=EasyCsvFileAdapter.parseCsv(text);
    if(lines.isEmpty()) throw new IllegalArgumentException("CSV에 내용이 없습니다.");
    var headers=lines.getFirst().stream().map(h->h.strip().toLowerCase(Locale.ROOT).replace(" ","").replace("_","")).toList();
    // Financial parsing, original evidence and classification belong to the existing importer.
    boolean date=headers.stream().anyMatch(Set.of("date","거래일","거래일자","거래일시","일자")::contains);
    boolean amount=headers.stream().anyMatch(Set.of("amount","금액","거래금액","지출금액","입금금액")::contains);
    if(date&&amount) return new Analysis("LEDGER_IMPORT",Math.max(0,lines.size()-1),items,issues);
    if(headers.stream().noneMatch(h->"name".equals(STUDENT_HEADERS.get(h)))) throw new IllegalArgumentException("학생 CSV에는 이름 열, 거래 CSV에는 거래일·금액 열이 필요합니다.");
    var fields=new HashSet<String>();
    for(var header:headers) {
      var field=STUDENT_HEADERS.get(header);
      if(field==null) issues.add("사용하지 않는 열: "+header);
      else if(!fields.add(field)) throw new IllegalArgumentException("같은 학생 항목의 열이 중복되었습니다: "+header);
    }
    var rows=lines.subList(1,lines.size()).stream().filter(row->row.stream().anyMatch(cell->!cell.isBlank())).toList();
    limit(rows.size());
    for(int i=0;i<rows.size();i++) {
      var row=rows.get(i);var data=new LinkedHashMap<String,Object>();
      if(row.size()!=headers.size()) {issues.add((i+2)+"행: 열 개수가 맞지 않아 초안을 만들지 않았습니다.");continue;}
      for(int j=0;j<headers.size();j++) if(STUDENT_HEADERS.containsKey(headers.get(j))&&!row.get(j).isBlank()) data.put(STUDENT_HEADERS.get(headers.get(j)),row.get(j).strip());
      if(!data.containsKey("name")) {issues.add((i+2)+"행: 학생 이름이 없어 초안을 만들지 않았습니다.");continue;}
      items.add(new Item(i+2,new AssistantCommandParser.Suggestion("STUDENT_CREATE",data,"학생 명단에서 등록 내용을 추출했습니다.")));
    }
    return new Analysis("학생 명단",rows.size(),items,issues);
  }
  private void limit(int count) {if(count==0||count>20) throw new IllegalArgumentException("학원 자료는 한 번에 1~20행으로 올려주세요.");}
}
