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
    String allowedCommand = "ls";
    String expectedResponse = "file1.txt\nfile2.txt\n";
    when(employeeService.executeCommand(allowedCommand)).thenReturn(expectedResponse);

    // Test
    mockMvc
        .perform(
            get("/api/execute")
                .param("cmd", allowedCommand)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string(expectedResponse));
  }

  @Test
  public void testExecuteCommandExample_DisallowedCommand() throws Exception {
    // Setup
    String disallowedCommand = "rm -rf /";
    String securityMessage = "Command not allowed for security reasons. Only specific commands are permitted.";
    when(employeeService.executeCommand(disallowedCommand)).thenReturn(securityMessage);

    // Test
    mockMvc
        .perform(
            get("/api/execute")
                .param("cmd", disallowedCommand)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string(securityMessage));
  }

  @Test
  public void testExecuteCommandExample_CommandInjectionAttempt() throws Exception {
    // Setup
    String injectionAttempt = "ls; rm -rf /";
    String securityMessage = "Command not allowed for security reasons. Only specific commands are permitted.";
    when(employeeService.executeCommand(injectionAttempt)).thenReturn(securityMessage);

    // Test
    mockMvc
        .perform(
            get("/api/execute")
                .param("cmd", injectionAttempt)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string(securityMessage));
  }
}