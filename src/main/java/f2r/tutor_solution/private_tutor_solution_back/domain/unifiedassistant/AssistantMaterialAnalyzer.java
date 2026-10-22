package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyCsvFileAdapter;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Parses material only. All authority, staging and approval remain in the existing services. */
@Component @RequiredArgsConstructor
public class AssistantMaterialAnalyzer {
  private final AssistantIntentAnalyzer intents;
  public record Item(int row, AssistantCommandParser.Suggestion suggestion) {}
  public record Analysis(String kind, int rows, List<Item> items, List<String> issues) {}
  private static final Map<String,String> STUDENT_HEADERS=Map.ofEntries(
      Map.entry("이름","name"),Map.entry("학생명","name"),Map.entry("학생이름","name"),Map.entry("name","name"),
      Map.entry("학년","grade"),Map.entry("grade","grade"),Map.entry("학교","schoolName"),Map.entry("schoolname","schoolName"),
      Map.entry("학부모이름","parentName"),Map.entry("parentname","parentName"),
      Map.entry("학부모연락처","parentPhone"),Map.entry("parentphone","parentPhone"));

  public Analysis analyze(Long userId, String filename, byte[] bytes) {
    if(bytes.length==0 || bytes.length>262144) throw new IllegalArgumentException("자료는 1~256KB로 올려주세요.");
    String file=filename==null?"":filename.toLowerCase(Locale.ROOT);
    if(!file.endsWith(".csv")&&!file.endsWith(".txt")) throw new IllegalArgumentException("학생 명단·거래 CSV 또는 업무 지시 TXT를 올려주세요. 이미지·PDF·엑셀 자동 인식은 지원하지 않습니다.");
    String text=EasyCsvFileAdapter.decode(bytes);
    if(text.indexOf('\0')>=0) throw new IllegalArgumentException("텍스트 자료를 확인하세요.");
    var items=new ArrayList<Item>(); var issues=new ArrayList<String>();
    if(file.endsWith(".txt")) {
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
