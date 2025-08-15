package com.example.controller;

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
  public void testExecuteCommandExample_WithAllowedCommand() throws Exception {
    // Setup
    when(employeeService.executeCommand("list_files")).thenReturn("file1.txt\nfile2.txt");

    // Test
    mockMvc
        .perform(
            get("/api/execute").param("cmd", "list_files").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string("file1.txt\nfile2.txt"));
  }

  @Test
  public void testExecuteCommandExample_WithDisallowedCommand() throws Exception {
    // Setup
    String errorMessage =
        "Error: Command not allowed. Allowed commands are: list_files, disk_space, memory_usage, current_dir";
    when(employeeService.executeCommand("rm -rf /")).thenReturn(errorMessage);

    // Test
    mockMvc
        .perform(
            get("/api/execute").param("cmd", "rm -rf /").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string(errorMessage));
  }

  @Test
  public void testExecuteCommandExample_WithInjectionAttempt() throws Exception {
    // Setup
    String errorMessage =
        "Error: Command not allowed. Allowed commands are: list_files, disk_space, memory_usage, current_dir";
    when(employeeService.executeCommand("list_files; rm -rf /")).thenReturn(errorMessage);

    // Test
    mockMvc
        .perform(
            get("/api/execute")
                .param("cmd", "list_files; rm -rf /")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string(errorMessage));
  }

  @Test
  public void testExecuteCommandHelp() throws Exception {
    mockMvc
        .perform(get("/api/execute/help").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string("Available commands: list_files, disk_space, memory_usage, current_dir"));
  }
}
