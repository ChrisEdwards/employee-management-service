package com.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
  public void testRedirectWithHeaderInjectionAttempt() throws Exception {
    String maliciousUrl = "http://example.com\r\nX-Injected-Header: malicious";

    MvcResult result =
        mockMvc
            .perform(get("/api/redirect").param("url", maliciousUrl))
            .andExpect(status().isFound())
            .andReturn();

    String locationHeader = result.getResponse().getHeader("Location");
    assertThat(locationHeader).isNotNull();
    assertThat(locationHeader).doesNotContain("\r");
    assertThat(locationHeader).doesNotContain("\n");
    assertThat(locationHeader).isEqualTo("http://example.comX-Injected-Header: malicious");

    String injectedHeader = result.getResponse().getHeader("X-Injected-Header");
    assertThat(injectedHeader).isNull();
  }

  @Test
  public void testRedirectWithCarriageReturnInjection() throws Exception {
    String maliciousUrl = "http://example.com\rSet-Cookie: sessionid=malicious";

    MvcResult result =
        mockMvc
            .perform(get("/api/redirect").param("url", maliciousUrl))
            .andExpect(status().isFound())
            .andReturn();

    String locationHeader = result.getResponse().getHeader("Location");
    assertThat(locationHeader).isNotNull();
    assertThat(locationHeader).doesNotContain("\r");
    assertThat(locationHeader).doesNotContain("\n");

    String setCookieHeader = result.getResponse().getHeader("Set-Cookie");
    assertThat(setCookieHeader).isNull();
  }

  @Test
  public void testRedirectWithNewlineInjection() throws Exception {
    String maliciousUrl = "http://example.com\nX-Malicious: value";

    MvcResult result =
        mockMvc
            .perform(get("/api/redirect").param("url", maliciousUrl))
            .andExpect(status().isFound())
            .andReturn();

    String locationHeader = result.getResponse().getHeader("Location");
    assertThat(locationHeader).isNotNull();
    assertThat(locationHeader).doesNotContain("\r");
    assertThat(locationHeader).doesNotContain("\n");

    String maliciousHeader = result.getResponse().getHeader("X-Malicious");
    assertThat(maliciousHeader).isNull();
  }

  @Test
  public void testRedirectWithLegitimateUrl() throws Exception {
    String legitimateUrl = "http://example.com/path?param=value";

    MvcResult result =
        mockMvc
            .perform(get("/api/redirect").param("url", legitimateUrl))
            .andExpect(status().isFound())
            .andReturn();

    String locationHeader = result.getResponse().getHeader("Location");
    assertThat(locationHeader).isNotNull();
    assertThat(locationHeader).isEqualTo(legitimateUrl);

    String customHeader = result.getResponse().getHeader("X-Custom-Header");
    assertThat(customHeader).isEqualTo("Referrer: " + legitimateUrl);
  }

  @Test
  public void testRedirectWithNullUrl() throws Exception {
    MvcResult result =
        mockMvc.perform(get("/api/redirect")).andExpect(status().isBadRequest()).andReturn();
  }
}
