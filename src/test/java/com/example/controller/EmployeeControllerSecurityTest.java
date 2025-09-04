package com.example.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.config.SecurityConfig;
import com.example.model.User;
import com.example.service.EmployeeService;

@WebMvcTest(EmployeeController.class)
@Import(SecurityConfig.class)
public class EmployeeControllerSecurityTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private EmployeeService employeeService;

  @Test
  public void testUserSearchWithSqlInjectionAttempt() throws Exception {
    // Setup - SQL injection attempt
    String maliciousUsername = "' OR '1'='1";
    List<User> emptyList = new ArrayList<>();
    when(employeeService.findUserByUsername(anyString())).thenReturn(emptyList);

    // Execute the request with the malicious input
    mockMvc
        .perform(
            get("/api/user-search")
                .param("username", maliciousUsername)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    // Verify that the service method was called with the exact malicious input
    // The service method should handle the parameterization properly
    verify(employeeService).findUserByUsername(eq(maliciousUsername));
  }
}
