package com.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.config.SecurityConfig;
import com.example.service.EmployeeService;

@WebMvcTest(EmployeeController.class)
@Import(SecurityConfig.class)
public class EmployeeControllerSecurityTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private EmployeeService employeeService;

  @Test
  public void testRedirectWithValidUrl() throws Exception {
    String validUrl = "http://example.com";

    MvcResult result =
        mockMvc
            .perform(get("/api/redirect").param("url", validUrl))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", validUrl))
            .andReturn();

    String locationHeader = result.getResponse().getHeader("Location");
    assertThat(locationHeader).isEqualTo(validUrl);
    assertThat(locationHeader).doesNotContain("\r");
    assertThat(locationHeader).doesNotContain("\n");
  }

  @Test
  public void testRedirectWithHeaderInjectionAttempt() throws Exception {
    String maliciousUrl = "http://example.com\r\nX-Injected-Header: malicious";

    MvcResult result =
        mockMvc
            .perform(get("/api/redirect").param("url", maliciousUrl))
            .andExpect(status().isFound())
            .andReturn();

    String locationHeader = result.getResponse().getHeader("Location");
    assertThat(locationHeader).doesNotContain("\r");
    assertThat(locationHeader).doesNotContain("\n");
    assertThat(locationHeader).isEqualTo("http://example.comX-Injected-Header: malicious");

    assertThat(result.getResponse().getHeader("X-Injected-Header")).isNull();
  }

  @Test
  public void testRedirectWithCarriageReturnOnly() throws Exception {
    String maliciousUrl = "http://example.com\rX-Injected: value";

    MvcResult result =
        mockMvc
            .perform(get("/api/redirect").param("url", maliciousUrl))
            .andExpect(status().isFound())
            .andReturn();

    String locationHeader = result.getResponse().getHeader("Location");
    assertThat(locationHeader).doesNotContain("\r");
    assertThat(locationHeader).isEqualTo("http://example.comX-Injected: value");
  }

  @Test
  public void testRedirectWithLineFeedOnly() throws Exception {
    String maliciousUrl = "http://example.com\nX-Injected: value";

    MvcResult result =
        mockMvc
            .perform(get("/api/redirect").param("url", maliciousUrl))
            .andExpect(status().isFound())
            .andReturn();

    String locationHeader = result.getResponse().getHeader("Location");
    assertThat(locationHeader).doesNotContain("\n");
    assertThat(locationHeader).isEqualTo("http://example.comX-Injected: value");
  }

  @Test
  public void testRedirectWithMultipleNewlines() throws Exception {
    String maliciousUrl = "http://example.com\r\n\r\n<html>injected content</html>";

    MvcResult result =
        mockMvc
            .perform(get("/api/redirect").param("url", maliciousUrl))
            .andExpect(status().isFound())
            .andReturn();

    String locationHeader = result.getResponse().getHeader("Location");
    assertThat(locationHeader).doesNotContain("\r");
    assertThat(locationHeader).doesNotContain("\n");
    assertThat(locationHeader).isEqualTo("http://example.com<html>injected content</html>");
  }

  @Test
  public void testCustomHeaderAlsoSanitized() throws Exception {
    String maliciousUrl = "http://example.com\r\nX-Evil: header";

    MvcResult result =
        mockMvc
            .perform(get("/api/redirect").param("url", maliciousUrl))
            .andExpect(status().isFound())
            .andReturn();

    String customHeader = result.getResponse().getHeader("X-Custom-Header");
    assertThat(customHeader).doesNotContain("\r");
    assertThat(customHeader).doesNotContain("\n");
    assertThat(customHeader).startsWith("Referrer: ");
  }
}
