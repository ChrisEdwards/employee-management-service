package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class EmployeeServiceSecurityTest {

  @Mock private DataSource dataSource;

  @InjectMocks private EmployeeService employeeService;

  @BeforeEach
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  public void testExecuteCommand_AllowedCommand() {
    // Test with an allowed command
    String result = employeeService.executeCommand("ls");
    
    // The result should not contain the "not allowed" message
    assertThat(result).doesNotContain("Command not allowed for security reasons");
  }

  @Test
  public void testExecuteCommand_DisallowedCommand() {
    // Test with a command that is not in the allowlist
    String result = employeeService.executeCommand("rm -rf /");
    
    // The result should contain the "not allowed" message
    assertThat(result).contains("Command not allowed for security reasons");
  }

  @Test
  public void testExecuteCommand_CommandInjectionAttempt() {
    // Test with a command injection attempt
    String result = employeeService.executeCommand("ls; rm -rf /");
    
    // The result should contain the "not allowed" message
    assertThat(result).contains("Command not allowed for security reasons");
  }

  @Test
  public void testExecuteCommand_CommandWithPipes() {
    // Test with a command that uses pipes
    String result = employeeService.executeCommand("ls | grep java");
    
    // The result should contain the "not allowed" message
    assertThat(result).contains("Command not allowed for security reasons");
  }

  @Test
  public void testIsCommandAllowed() throws Exception {
    // Use reflection to access the private method
    Method isCommandAllowedMethod = EmployeeService.class.getDeclaredMethod("isCommandAllowed", String.class);
    isCommandAllowedMethod.setAccessible(true);
    
    // Test allowed commands
    assertThat((Boolean) isCommandAllowedMethod.invoke(employeeService, "ls")).isTrue();
    assertThat((Boolean) isCommandAllowedMethod.invoke(employeeService, "pwd")).isTrue();
    assertThat((Boolean) isCommandAllowedMethod.invoke(employeeService, "date")).isTrue();
    
    // Test disallowed commands
    assertThat((Boolean) isCommandAllowedMethod.invoke(employeeService, "rm -rf /")).isFalse();
    assertThat((Boolean) isCommandAllowedMethod.invoke(employeeService, "cat /etc/passwd")).isFalse();
    assertThat((Boolean) isCommandAllowedMethod.invoke(employeeService, "ls; rm -rf /")).isFalse();
    assertThat((Boolean) isCommandAllowedMethod.invoke(employeeService, "ls | grep java")).isFalse();
  }
}