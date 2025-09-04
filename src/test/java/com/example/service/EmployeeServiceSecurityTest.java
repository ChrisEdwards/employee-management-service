package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class EmployeeServiceSecurityTest {

  @Autowired private EmployeeService employeeService;

  @Test
  public void testExecuteCommand_AllowedCommand() {
    // Test with an allowed command
    String result = employeeService.executeCommand("ls");
    
    // Verify that the command executed successfully
    assertThat(result).doesNotContain("Command not allowed");
    // The output should contain some directory listing
    assertThat(result).contains("\n");
  }

  @Test
  public void testExecuteCommand_DisallowedCommand() {
    // Test with a command that is not in the allowlist
    String result = employeeService.executeCommand("cat /etc/passwd");
    
    // Verify that the command was rejected
    assertThat(result).contains("Command not allowed");
    assertThat(result).contains("Allowed commands are:");
  }

  @Test
  public void testExecuteCommand_InjectionAttempt() {
    // Test with a command injection attempt
    String result = employeeService.executeCommand("ls; rm -rf /");
    
    // Verify that the command was rejected
    assertThat(result).contains("Command not allowed");
  }

  @Test
  public void testExecuteCommand_NullInput() {
    // Test with null input
    String result = employeeService.executeCommand(null);
    
    // Verify that null input is handled safely
    assertThat(result).contains("Command not allowed");
  }
}