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
    // Test with a valid URL
    mockMvc
        .perform(get("/api/redirect").param("url", "https://example.com"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://example.com"));
  }

  @Test
  public void testRedirectWithInvalidUrl() throws Exception {
    // Test with an invalid URL (no protocol)
    mockMvc
        .perform(get("/api/redirect").param("url", "example.com"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testRedirectWithNonHttpProtocol() throws Exception {
    // Test with a non-HTTP protocol
    mockMvc
        .perform(get("/api/redirect").param("url", "file:///etc/passwd"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void testRedirectWithHeaderInjection() throws Exception {
    // Test with a URL containing CR/LF characters for header injection
    mockMvc
        .perform(get("/api/redirect").param("url", "https://example.com\r\nX-Injected: value"))
        .andExpect(status().isBadRequest());
  }
}