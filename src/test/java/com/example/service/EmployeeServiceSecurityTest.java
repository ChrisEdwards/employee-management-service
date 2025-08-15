package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class EmployeeServiceSecurityTest {

  @InjectMocks private EmployeeService employeeService;

  @BeforeEach
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  public void testExecuteCommand_AllowedCommand() {
    // Test with an allowed command
    String result = employeeService.executeCommand("ls");
    
    // Verify that the command was executed (not rejected)
    assertThat(result).doesNotContain("Error: Command not allowed for security reasons");
  }

  @Test
  public void testExecuteCommand_DisallowedCommand() {
    // Test with a command that could be used for command injection
    String result = employeeService.executeCommand("ls; rm -rf /");
    
    // Verify that the command was rejected
    assertThat(result).contains("Error: Command not allowed for security reasons");
  }

  @Test
  public void testExecuteCommand_CommandInjectionAttempt() {
    // Test with a command injection attempt
    String result = employeeService.executeCommand("ls && echo 'Injected command'");
    
    // Verify that the command was rejected
    assertThat(result).contains("Error: Command not allowed for security reasons");
  }

  @Test
  public void testExecuteCommand_PipeOperatorInjection() {
    // Test with a pipe operator injection attempt
    String result = employeeService.executeCommand("ls | grep etc");
    
    // Verify that the command was rejected
    assertThat(result).contains("Error: Command not allowed for security reasons");
  }
}