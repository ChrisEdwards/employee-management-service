package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class EmployeeServiceSecurityTest {

  @Autowired private EmployeeService employeeService;

  @Test
  public void testExecuteCommand_WithAllowedCommand() {
    // Test with a valid command from the whitelist
    String result = employeeService.executeCommand("list_files");
    
    // The command should execute and return output
    assertThat(result).isNotEmpty();
    // Should not contain error message
    assertThat(result).doesNotContain("Error: Command not allowed");
  }

  @Test
  public void testExecuteCommand_WithDisallowedCommand() {
    // Test with a command that's not in the whitelist
    String result = employeeService.executeCommand("invalid_command");
    
    // Should return error message
    assertThat(result).contains("Error: Command not allowed");
    assertThat(result).contains("Allowed commands are:");
  }

  @Test
  public void testExecuteCommand_WithInjectionAttempt() {
    // Test with a command injection attempt
    String result = employeeService.executeCommand("list_files; rm -rf /");
    
    // Should return error message since this is not in the whitelist
    assertThat(result).contains("Error: Command not allowed");
  }
}