package f2r.tutor_solution.private_tutor_solution_back.domain.naesin;

import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service.ExamInsightTextExtractor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import org.apache.poi.poifs.filesystem.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** HWP5 text records only; original bytes and layout are never rewritten. */
@Component
public class NaesinTextExtractor {
  @org.springframework.beans.factory.annotation.Value("${app.naesin.kordoc-cli:}")
  private String kordocCli = "";
  @org.springframework.beans.factory.annotation.Value("${app.naesin.kordoc-node:node}")
  private String kordocNode = "node";
  private static final int MAX_EXPANDED = 8 * 1024 * 1024;
  private final ExamInsightTextExtractor existing;
  public NaesinTextExtractor(ExamInsightTextExtractor existing) { this.existing = existing; }

  public String extract(MultipartFile file) throws IOException {
    if (!Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT).endsWith(".hwp"))
      return existing.extract(file).text();
    if(!kordocCli.isBlank())return kordoc(file);
    try (var fs = new POIFSFileSystem(file.getInputStream())) {
      var root = fs.getRoot();
      byte[] header = read(root, "FileHeader", 256);
      if (header.length < 40 || !new String(header, 0, 17, StandardCharsets.US_ASCII).equals("HWP Document File"))
        throw new IOException("HWP5 문서가 아닙니다.");
      int flags = le32(header, 36);
      if ((flags & 6) != 0) throw new IOException("암호·배포용 HWP는 추출할 수 없습니다.");
      var body = (DirectoryEntry) root.getEntry("BodyText");
      var sections = new ArrayList<String>();
      for (var entry : body) if (entry.getName().matches("Section[0-9]+")) sections.add(entry.getName());
      sections.sort(Comparator.comparingInt(n -> Integer.parseInt(n.substring(7))));
      StringBuilder text = new StringBuilder();
      int budget = MAX_EXPANDED;
      for (String section : sections) {
        byte[] raw = read(body, section, MAX_EXPANDED);
        if ((flags & 1) != 0) {
          var inflater = new Inflater(true);
          try (var in = new InflaterInputStream(new ByteArrayInputStream(raw), inflater)) {
            raw = in.readNBytes(budget + 1);
          } finally { inflater.end(); }
        }
        budget -= raw.length;
        if (budget < 0) throw new IOException("HWP 압축 해제 상한 초과");
        for (int at = 0; at < raw.length;) {
          if (raw.length - at < 4) throw new IOException("잘린 HWP 레코드");
          int h = le32(raw, at); at += 4;
          int size = h >>> 20;
          if (size == 4095) {
            if (raw.length - at < 4) throw new IOException("잘린 HWP 길이");
            size = le32(raw, at); at += 4;
          }
          if (size < 0 || size > raw.length - at) throw new IOException("HWP 레코드 길이 오류");
          if ((h & 1023) == 67) {
            if (size % 2 != 0) throw new IOException("HWP 텍스트 길이 오류");
            String paragraph = new String(raw, at, size, StandardCharsets.UTF_16LE);
            for (int i = 0; i < paragraph.length(); i++) {
              char c = paragraph.charAt(i);
              if (c == 9) { text.append(' '); i += 7; }
              else if (c == 10 || c == 13) text.append('\n');
              else if ((c >= 1 && c <= 8) || (c >= 11 && c <= 12) || (c >= 14 && c <= 23)) i += 7;
              else if (c >= 32) text.append(c);
            }
            text.append('\n');
          }
          at += size;
        }
      }
      if (text.isEmpty()) throw new IOException("추출 가능한 HWP 본문이 없습니다.");
      if (text.length() > 150000) throw new IOException("본문은 15만 자 이하만 추출할 수 있습니다.");
      return text.toString().strip();
    }
  }
  public String toolName(){return kordocCli.isBlank()?"HWP5 / ExamInsightTextExtractor":"Kordoc CLI / ExamInsightTextExtractor";}
  private String kordoc(MultipartFile file)throws IOException {
    var dir=java.nio.file.Files.createTempDirectory("naesin-kordoc-");
    Process process=null;
    try{
      var input=dir.resolve("original.hwp");var output=dir.resolve("extracted.md");
      try(var in=file.getInputStream()){java.nio.file.Files.copy(in,input);}
      process=new ProcessBuilder(kordocNode,"--max-old-space-size=256",kordocCli,input.toString(),"--keep-layout-tables","-o",output.toString())
          .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
      if(!process.waitFor(30,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("Kordoc 추출 시간 초과");}
      if(process.exitValue()!=0 || !java.nio.file.Files.exists(output))throw new IOException("Kordoc 본문 추출 실패");
      if(java.nio.file.Files.size(output)>600000)throw new IOException("Kordoc 본문 크기 상한 초과");
      String text=java.nio.file.Files.readString(output);
      if(text.isBlank() || text.length()>150000)throw new IOException("Kordoc 본문 길이 오류");
      return text;
    }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Kordoc 추출 중단",e);}
    finally{
      if(process!=null && process.isAlive())process.destroyForcibly();
      try(var paths=java.nio.file.Files.walk(dir)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())java.nio.file.Files.deleteIfExists(p);}
    }
  }
  private static int le32(byte[] b, int i) {
    return (b[i] & 255) | ((b[i+1] & 255) << 8) | ((b[i+2] & 255) << 16) | ((b[i+3] & 255) << 24);
  }
  private static byte[] read(DirectoryEntry dir, String name, int max) throws IOException {
    try (var in = new DocumentInputStream((DocumentEntry) dir.getEntry(name))) {
      byte[] b = in.readNBytes(max + 1);
      if (b.length > max) throw new IOException("HWP 스트림 크기 상한 초과");
      return b;
    }
  }
}
