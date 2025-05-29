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
  public void testUserSearchExample_WithSqlInjectionAttempt() throws Exception {
    // Setup
    List<User> emptyUsers = new ArrayList<>();
    when(employeeService.findUserByUsername(anyString())).thenReturn(emptyUsers);

    // SQL injection payload from the vulnerability report
    String sqlInjectionPayload = "' OR '1'='1";

    // Test
    mockMvc
        .perform(
            get("/api/user-search")
                .param("username", sqlInjectionPayload)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    // Verify that the service was called with the exact SQL injection string
    verify(employeeService).findUserByUsername(eq(sqlInjectionPayload));
  }

  @Test
  public void testUserSearchExample_WithValidInput() throws Exception {
    // Setup
    List<User> emptyUsers = new ArrayList<>();
    when(employeeService.findUserByUsername("Bob Jones")).thenReturn(emptyUsers);

    // Test with the input from the original HTTP request
    mockMvc
        .perform(
            get("/api/user-search")
                .param("username", "Bob Jones")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    // Verify that the service was called with the exact username
    verify(employeeService).findUserByUsername(eq("Bob Jones"));
  }
}
