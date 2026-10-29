package f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import f2r.tutor_solution.private_tutor_solution_back.domain.landing.service.HomeAgentTrailerService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HomeAgentTrailerControllerTest {
  final HomeAgentTrailerService service = mock(HomeAgentTrailerService.class);
  final HomeAgentTrailerService.Media media = new HomeAgentTrailerService.Media("secret-storage-key", 16, "a".repeat(64));
  final byte[] bytes = "0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
  MockMvc mvc;
  static final String URL = HomeAgentTrailerService.PUBLIC_PATH;

  @BeforeEach void setup() throws Exception {
    when(service.approvedMedia()).thenReturn(Optional.of(media));
    when(service.openRange(eq(media), anyLong(), anyLong())).thenAnswer(call ->
        new ByteArrayInputStream(Arrays.copyOfRange(bytes,
            ((Long) call.getArgument(1)).intValue(), ((Long) call.getArgument(2)).intValue() + 1)));
    mvc = MockMvcBuilders.standaloneSetup(new HomeAgentTrailerController(service)).build();
  }

  @Test void givenApprovedFile_whenFullOrRangeRead_thenExactBytesAndNoStore() throws Exception {
    mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(content().bytes(bytes))
        .andExpect(content().contentType("video/mp4")).andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    mvc.perform(get(URL).header("Range", "bytes=2-7")).andExpect(status().isPartialContent())
        .andExpect(header().string("Content-Range", "bytes 2-7/16")).andExpect(content().string("234567"));
    mvc.perform(get(URL).header("Range", "bytes=-4")).andExpect(status().isPartialContent())
        .andExpect(content().string("cdef"));
  }

  @Test void givenInvalidOrMultiRange_whenRead_then416WithoutOpeningStorage() throws Exception {
    for (String range : new String[]{"bytes=16-", "bytes=8-2", "bytes=0-1,3-4", "bytes=-0", "invalid"}) {
      mvc.perform(get(URL).header("Range", range)).andExpect(status().isRequestedRangeNotSatisfiable())
          .andExpect(header().string("Content-Range", "bytes */16"));
    }
    verify(service, never()).openRange(any(), anyLong(), anyLong());
  }

  @Test void givenCallerFileSelection_whenRead_thenRejectedBeforeLookup() throws Exception {
    mvc.perform(get(URL + "?projectId=other&fileId=other")).andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test void givenRevokedOrUnapprovedFile_whenRead_then404WithoutStorage() throws Exception {
    when(service.approvedMedia()).thenReturn(Optional.empty());
    mvc.perform(get(URL)).andExpect(status().isNotFound());
    verify(service, never()).openRange(any(), anyLong(), anyLong());
  }

  @Test void givenStorageFailure_whenRetried_thenSanitized503AndAllPermitsReleased() throws Exception {
    when(service.openRange(any(), anyLong(), anyLong())).thenThrow(new IOException("secret-storage-key token=secret"));
    for (int i = 0; i < 4; i++) {
      var response = mvc.perform(get(URL)).andExpect(status().isServiceUnavailable()).andReturn().getResponse();
      assertThat(response.getErrorMessage()).doesNotContain("secret", "storage-key");
      assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }
  }

  @Test void givenTruncatedStorage_whenRead_thenClosesStreamAndDoesNotReturnSuccess() throws Exception {
    var closed = new java.util.concurrent.atomic.AtomicBoolean();
    when(service.openRange(any(), anyLong(), anyLong())).thenAnswer(call -> new ByteArrayInputStream(new byte[0]) {
      @Override public void close() { closed.set(true); }
    });
    mvc.perform(get(URL)).andExpect(status().isServiceUnavailable());
    assertThat(closed).isTrue();
  }
}
