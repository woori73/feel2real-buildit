package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Component;

/** Small, explicit Korean command vocabulary. Unknown/missing fields require user correction. */
@Component
public class AssistantCommandParser {
  public record Suggestion(String operation,Map<String,Object> input,String message) {}
  /** 정정 API의 요청 규격이 이 저장소에 없어 추측 호출하지 않는다. 새 출결 이벤트를 만드는 오분류도 막는다. */
  static final String CORRECTION_BLOCKED="출결 정정은 사유 입력이 필요한 기존 출결 화면(출결 메뉴)에서 처리하세요. 비서는 새 출결 기록을 만들어 정정을 대신하지 않습니다.";
  static final Set<String> READ_ONLY=Set.of("OVERVIEW","ATTENDANCE_QUERY","NOTICE_DRAFT");
  private static final List<String> QUERY_WORDS=List.of("조회","확인","보여","알려","현황","요약","내역");
  private static final List<String> WRITE_WORDS=List.of("등록","기록해","기록하","기록으로","추가","만들","생성","수정","배정","처리","남겨");
  public Suggestion parse(String text) {
    if(text==null || text.isBlank() || text.length()>2000) throw new IllegalArgumentException("업무 지시는 1~2000자로 입력하세요.");
    var data=new LinkedHashMap<String,Object>(); String op;
    if(text.contains("삭제")||text.contains("승인")||text.contains("실행해")) return new Suggestion("",data,"실행은 저장된 초안의 승인 버튼에서 진행하세요. 삭제는 기존 업무 화면에서 처리합니다.");
    boolean attendance=text.contains("출결")||text.contains("출석")||text.contains("등원")||text.contains("하원")||text.contains("결석")||text.contains("지각");
    if(attendance&&text.contains("정정")) return new Suggestion("",data,CORRECTION_BLOCKED);
    if(text.contains("공지")) {
      for(var entry:Map.of("제목","title","내용","content").entrySet()) {
        Matcher m=Pattern.compile("(?:^|[,;\\s])"+entry.getKey()+"[:=]\\s*(\"[^\"]*\"|[^,;\\n]+)").matcher(text);
        if(m.find())data.put(entry.getValue(),m.group(1).replaceAll("^\"|\"$","").strip());
      }
      return new Suggestion("NOTICE_DRAFT",data,"공지 문안 초안만 제안합니다. 저장하거나 발송하지 않습니다.");
    }
    if(attendance&&QUERY_WORDS.stream().anyMatch(text::contains)&&WRITE_WORDS.stream().noneMatch(text::contains)) {
      putId(data,text,"studentId","학생");
      return new Suggestion("ATTENDANCE_QUERY",data,"출결 내역을 조회만 합니다. 기록을 만들지 않습니다.");
    }
    if((text.contains("현황")||text.contains("요약")||text.contains("보여")) && !text.contains("등록") && !text.contains("기록")) return new Suggestion("OVERVIEW",data,"허용된 학원과 선택한 사업체의 현황을 새로 조회합니다.");
    if(text.contains("상담")) {op=text.contains("메시지")?"CONSULT_MESSAGE":"CONSULT_CREATE"; putId(data,text,"studentId","학생");putId(data,text,"consultId","상담");}
    else if(text.contains("배정")) {op="CLASS_ASSIGN"; putId(data,text,"studentId","학생");putId(data,text,"classId","반");}
    else if(text.contains("등원")||text.contains("하원")||text.contains("결석")||text.contains("지각")||text.contains("출결")) {
      op="ATTENDANCE_RECORD"; putId(data,text,"studentId","학생");
      data.put("eventType",text.contains("결석")?"ABSENT":text.contains("지각")?"LATE":text.contains("하원")?"CHECK_OUT":"CHECK_IN");
    } else if(text.contains("반")&&(text.contains("만들")||text.contains("생성")||text.contains("수정"))) {
      op=text.contains("수정")?"CLASS_UPDATE":"CLASS_CREATE"; putId(data,text,"classId","반");putId(data,text,"teacherId","강사");
    } else if(text.contains("학생")&&(text.contains("등록")||text.contains("추가")||text.contains("수정"))) {
      op=text.contains("수정")?"STUDENT_UPDATE":"STUDENT_CREATE"; putId(data,text,"studentId","학생");
      Matcher name=Pattern.compile("학생\\s+([가-힣A-Za-z]{2,20})(?:을|를)?\\s*(?:등록|추가)").matcher(text);
      if(name.find()) data.put("name",name.group(1));
    } else if(text.contains("거래")||text.contains("지출")||text.contains("수입")||text.contains("증빙")) {
      op="LEDGER_ENTRY";
      if(text.contains("CSV")||text.contains("파일")) return new Suggestion("LEDGER_IMPORT",data,"거래·증빙 CSV를 올리면 원본 행을 보관하고 장부 반영 초안을 만듭니다.");
      Matcher date=Pattern.compile("\\d{4}-\\d{2}-\\d{2}").matcher(text); if(date.find())data.put("occurredOn",date.group());
      Matcher amount=Pattern.compile("([0-9][0-9,]*)\\s*원").matcher(text);if(amount.find())data.put("amount",Long.parseLong(amount.group(1).replace(",","")));
      if(text.contains("지출"))data.put("direction","EXPENSE");else if(text.contains("수입"))data.put("direction","INCOME");
    } else return new Suggestion("",data,"학생 등록, 반 생성·배정, 출결 기록, 상담 작성, 거래·증빙 CSV를 도와드립니다. 조회는 현황 새로고침 또는 업무 목록 버튼을 이용하세요.");
    for(var entry:Map.ofEntries(Map.entry("이름","name"),Map.entry("반이름","name"),Map.entry("학교","schoolName"),Map.entry("학년","grade"),Map.entry("제목","title"),Map.entry("내용","content"),Map.entry("메모","note"),Map.entry("거래처","counterparty"),Map.entry("적요","description"),Map.entry("증빙","evidenceType"),Map.entry("학부모이름","parentName"),Map.entry("학부모연락처","parentPhone")).entrySet()) {
      Matcher m=Pattern.compile("(?:^|[,;\\s])"+entry.getKey()+"[:=]\\s*(\"[^\"]*\"|[^,;\\n]+)").matcher(text);
      if(m.find())data.put(entry.getValue(),m.group(1).replaceAll("^\"|\"$","").strip());
    }
    data.entrySet().removeIf(e->!allowed(op,e.getKey()));
    return new Suggestion(op,data,"업무 항목을 채웠습니다. 대상과 내용을 확인·보완한 뒤 초안을 만드세요. 아직 실행하지 않았습니다.");
  }
  private boolean allowed(String op,String key) {
    return switch(op) {
      case "NOTICE_DRAFT" -> Set.of("title","content").contains(key);
      case "CONSULT_MESSAGE" -> Set.of("consultId","content").contains(key);
      case "CONSULT_CREATE" -> Set.of("studentId","title","content").contains(key);
      case "CLASS_CREATE" -> Set.of("name","teacherId").contains(key);
      case "CLASS_UPDATE" -> Set.of("classId","name","teacherId").contains(key);
      case "LEDGER_ENTRY" -> Set.of("occurredOn","direction","amount","counterparty","description","evidenceType").contains(key);
      default -> true;
    };
  }
  private void putId(Map<String,Object> data,String text,String key,String label) {
    Matcher m=Pattern.compile(label+"\\s*(?:ID|아이디|#)\\s*[:=]?\\s*(\\d+)",Pattern.CASE_INSENSITIVE).matcher(text);
    if(m.find())data.put(key,Long.parseLong(m.group(1)));
  }
}
