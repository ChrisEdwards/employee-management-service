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
    String mockResponse = "Directory listing...";
    when(employeeService.executeCommand("ls")).thenReturn(mockResponse);

    // Test
    mockMvc
        .perform(
            get("/api/execute")
                .param("cmd", "ls")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string(mockResponse));
  }

  @Test
  public void testExecuteCommandExample_DisallowedCommand() throws Exception {
    // Setup
    String securityMessage = "Command not allowed for security reasons";
    when(employeeService.executeCommand(anyString())).thenReturn(securityMessage);

    // Test with a potentially malicious command
    mockMvc
        .perform(
            get("/api/execute")
                .param("cmd", "cat /etc/passwd")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string(securityMessage));
  }
}