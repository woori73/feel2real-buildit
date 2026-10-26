package f2r.tutor_solution.private_tutor_solution_back.domain.naesin;

import static org.junit.jupiter.api.Assertions.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service.ExamInsightTextExtractor;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class NaesinTextExtractorTest {
  final NaesinTextExtractor extractor = new NaesinTextExtractor(new ExamInsightTextExtractor(new DocumentUploadValidator(new UploadSizeGuard(20971520))));
  static byte[] fixture(boolean compressed, boolean encrypted, boolean broken) throws Exception {
    return fixture("중2",compressed,encrypted,broken);
  }
  static byte[] fixture(String grade,boolean compressed, boolean encrypted, boolean broken) throws Exception {
    try (var fs=new POIFSFileSystem();var out=new ByteArrayOutputStream()) {
      byte[] header=new byte[256];System.arraycopy("HWP Document File".getBytes(StandardCharsets.US_ASCII),0,header,0,17);
      header[36]=(byte)((compressed?1:0)|(encrypted?2:0));
      fs.getRoot().createDocument("FileHeader",new ByteArrayInputStream(header));
      byte[] text=(grade+" 영어 1. Choose the correct answer. 정답 ③\r").getBytes(StandardCharsets.UTF_16LE);
      int h=67|((broken?10000:text.length)<<20);byte[] record=new byte[4+text.length];
      for(int i=0;i<4;i++)record[i]=(byte)(h>>>(8*i));System.arraycopy(text,0,record,4,text.length);
      if(compressed){var deflater=new Deflater(6,true);try(var bytes=new ByteArrayOutputStream()){try(var zip=new DeflaterOutputStream(bytes,deflater)){zip.write(record);}record=bytes.toByteArray();}finally{deflater.end();}}
      fs.getRoot().createDirectory("BodyText").createDocument("Section0",new ByteArrayInputStream(record));
      fs.writeFilesystem(out);return out.toByteArray();
    }
  }
  @Test void givenHwp5CompressedAndPlain_whenExtracted_thenTextAndOriginalBytesPreserved() throws Exception {
    for(boolean compressed:new boolean[]{false,true}){
      byte[] bytes=fixture(compressed,false,false),original=bytes.clone();
      String text=extractor.extract(new MockMultipartFile("file","중2.hwp","application/x-hwp",bytes));
      assertTrue(text.contains("중2 영어 1."));assertTrue(text.contains("정답 ③"));assertArrayEquals(original,bytes);
    }
  }
  @Test void givenEncryptedOrTruncatedHwp_whenExtracted_thenFailsExplicitly() throws Exception {
    assertThrows(IOException.class,()->extractor.extract(new MockMultipartFile("file","중2.hwp","application/x-hwp",fixture(true,true,false))));
    assertThrows(IOException.class,()->extractor.extract(new MockMultipartFile("file","중2.hwp","application/x-hwp",fixture(false,false,true))));
  }
  @Test void givenPdf_whenExtracted_thenExistingExtractorReused() throws Exception {
    try(var doc=new PDDocument();var bytes=new ByteArrayOutputStream()){
      var page=new PDPage();doc.addPage(page);try(var stream=new PDPageContentStream(doc,page)){
        stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);stream.newLineAtOffset(50,700);
        stream.showText("Middle school English. Choose the correct answer from the following options.");stream.endText();
      }
      doc.save(bytes);assertTrue(extractor.extract(new MockMultipartFile("file","middle.pdf","application/pdf",bytes.toByteArray())).contains("Middle school English"));
    }
  }
  @Test void givenPreviouslyVerifiedKordocAndRealHwp_whenRead_thenTextReturnedWithoutChangingOriginal()throws Exception{
    String cli=System.getenv("NAESIN_KORDOC_CLI"),source=System.getenv("NAESIN_KORDOC_REAL_HWP");
    org.junit.jupiter.api.Assumptions.assumeTrue(cli!=null && source!=null,"Pinned Kordoc and private HWP are optional local evidence");
    var path=java.nio.file.Path.of(source);byte[] original=java.nio.file.Files.readAllBytes(path);
    org.springframework.test.util.ReflectionTestUtils.setField(extractor,"kordocCli",cli);
    String text=extractor.extract(new MockMultipartFile("file",path.getFileName().toString(),"application/x-hwp",original));
    assertTrue(text.length()>100);assertArrayEquals(original,java.nio.file.Files.readAllBytes(path));
    assertTrue(extractor.toolName().startsWith("Kordoc"));
  }
}
