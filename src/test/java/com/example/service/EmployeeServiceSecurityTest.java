package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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
  @Mock private Connection connection;
  @Mock private Statement statement;
  @Mock private ResultSet resultSet;

  @InjectMocks private EmployeeService employeeService;

  @BeforeEach
  public void setup() throws SQLException {
    MockitoAnnotations.openMocks(this);

    // Configure the mock DataSource
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery(anyString())).thenReturn(resultSet);
  }

  @Test
  public void testExecuteCommand_AllowedCommand() {
    // Test with an allowed command
    String result = employeeService.executeCommand("ls");
    
    // The result should not contain an error message about command not being allowed
    assertThat(result).doesNotContain("Error: Command not allowed");
  }

  @Test
  public void testExecuteCommand_DisallowedCommand() {
    // Test with a command that is not in the allowed list
    String result = employeeService.executeCommand("rm");
    
    // The result should contain an error message about command not being allowed
    assertThat(result).contains("Error: Command not allowed");
  }

  @Test
  public void testExecuteCommand_InjectionAttempt() {
    // Test with a command injection attempt
    String result = employeeService.executeCommand("ls; rm -rf /");
    
    // The result should contain an error message about command not being allowed
    assertThat(result).contains("Error: Command not allowed");
  }

  @Test
  public void testExecuteCommand_AllowedCommandsList() {
    // Test that the error message lists all allowed commands
    String result = employeeService.executeCommand("invalid");
    
    // The result should list all allowed commands
    assertThat(result).contains("ls");
    assertThat(result).contains("pwd");
    assertThat(result).contains("whoami");
    assertThat(result).contains("date");
    assertThat(result).contains("echo");
  }
}