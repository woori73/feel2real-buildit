package f2r.tutor_solution.private_tutor_solution_back.domain.studio;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StudioMediaRangeTest {
  static final long MB = 1024L * 1024;

  @Test
  void givenNoRange_whenWindowed_thenWholeFile() {
    assertArrayEquals(new long[] {0, 99}, StudioController.rangeWindow(null, 100));
  }

  @Test
  void givenOpenEndedRange_whenLargeFile_thenCappedChunk() {
    long size = 90 * MB;
    assertArrayEquals(
        new long[] {0, StudioController.STREAM_CHUNK - 1},
        StudioController.rangeWindow("bytes=0-", size));
    assertArrayEquals(
        new long[] {10 * MB, 10 * MB + StudioController.STREAM_CHUNK - 1},
        StudioController.rangeWindow("bytes=" + 10 * MB + "-", size));
  }

  @Test
  void givenBoundedAndSuffixRanges_whenWindowed_thenClampedToFile() {
    assertArrayEquals(new long[] {10, 19}, StudioController.rangeWindow("bytes=10-19", 100));
    assertArrayEquals(new long[] {90, 99}, StudioController.rangeWindow("bytes=90-500", 100));
    assertArrayEquals(new long[] {80, 99}, StudioController.rangeWindow("bytes=-20", 100));
    assertArrayEquals(new long[] {0, 99}, StudioController.rangeWindow("bytes=-500", 100));
  }

  @Test
  void givenInvalidRanges_whenWindowed_thenUnsatisfiable() {
    for (String bad :
        new String[] {"bytes=100-", "bytes=20-10", "bytes=-", "bytes=-0", "items=0-1", "bytes=0-1,5-6", "bytes=x-"})
      assertNull(StudioController.rangeWindow(bad, 100), bad);
    assertNull(StudioController.rangeWindow("bytes=0-", 0));
  }
}
