package com.example.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.example.config.SecurityConfig;
import com.example.service.EmployeeService;

@WebMvcTest(EmployeeController.class)
@Import(SecurityConfig.class)
public class EmployeeControllerSecurityTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private EmployeeService employeeService;

  @Test
  public void testRedirectWithValidUrl() throws Exception {
    // Test with valid URL
    mockMvc
        .perform(get("/api/redirect").param("url", "https://example.com"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://example.com"));
  }

  @Test
  public void testRedirectWithInvalidUrl() throws Exception {
    // Test with invalid URL (no scheme)
    mockMvc
        .perform(get("/api/redirect").param("url", "example.com"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testRedirectWithHeaderInjectionAttempt() throws Exception {
    // Test with header injection attempt - URL should be sanitized
    String maliciousUrl = "https://example.com%0AX-Injected-Header: Injected";
    mockMvc
        .perform(get("/api/redirect").param("url", maliciousUrl))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testRedirectWithNonHttpScheme() throws Exception {
    // Test with non-HTTP scheme
    mockMvc
        .perform(get("/api/redirect").param("url", "file:///etc/passwd"))
        .andExpect(status().isBadRequest());
  }
}
