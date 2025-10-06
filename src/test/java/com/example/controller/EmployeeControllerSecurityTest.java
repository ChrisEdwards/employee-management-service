package com.example.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.config.SecurityConfig;
import com.example.service.EmployeeService;

@WebMvcTest(EmployeeController.class)
@Import(SecurityConfig.class)
public class EmployeeControllerSecurityTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private EmployeeService employeeService;

  @Test
  public void testExecuteCommandExample_AllowedCommand() throws Exception {
    // Setup
    String allowedCommandResponse = "Directory listing...";
    when(employeeService.executeCommand("ls")).thenReturn(allowedCommandResponse);

    // Test
    mockMvc
        .perform(
            get("/api/execute")
                .param("cmd", "ls")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string(allowedCommandResponse));
  }

  @Test
  public void testExecuteCommandExample_DisallowedCommand() throws Exception {
    // Setup
    String disallowedCommandResponse = "Command not allowed for security reasons";
    when(employeeService.executeCommand(anyString())).thenReturn(disallowedCommandResponse);

    // Test
    mockMvc
        .perform(
            get("/api/execute")
                .param("cmd", "cat /etc/passwd")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string(disallowedCommandResponse));
  }

  @Test
  public void testExecuteCommandExample_InjectionAttempt() throws Exception {
    // Setup
    String injectionResponse = "Command not allowed for security reasons";
    when(employeeService.executeCommand(anyString())).thenReturn(injectionResponse);

    // Test
    mockMvc
        .perform(
            get("/api/execute")
                .param("cmd", "ls; rm -rf /")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string(injectionResponse));
  }
}