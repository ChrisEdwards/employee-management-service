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
    
    // Verify that the command was executed (not rejected)
    assertThat(result).doesNotContain("Command not allowed for security reasons");
  }

  @Test
  public void testExecuteCommand_DisallowedCommand() {
    // Test with a disallowed command that could be used for malicious purposes
    String result = employeeService.executeCommand("cat /etc/passwd");
    
    // Verify that the command was rejected
    assertThat(result).contains("Command not allowed for security reasons");
  }

  @Test
  public void testExecuteCommand_CommandInjection() {
    // Test with command injection attempt
    String result = employeeService.executeCommand("ls; rm -rf /");
    
    // Verify that the command was rejected
    assertThat(result).contains("Command not allowed for security reasons");
  }
}