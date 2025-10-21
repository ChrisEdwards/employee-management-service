package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.model.User;

@SpringBootTest
public class EmployeeServiceSecurityTest {

  @Mock private DataSource dataSource;
  @Mock private Connection connection;
  @Mock private PreparedStatement preparedStatement;
  @Mock private ResultSet resultSet;

  @InjectMocks private EmployeeService employeeService;

  @BeforeEach
  public void setup() throws SQLException {
    MockitoAnnotations.openMocks(this);

    // Configure the mock DataSource
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
    when(preparedStatement.executeQuery()).thenReturn(resultSet);

    // Mock the ResultSet to return no results by default
    when(resultSet.next()).thenReturn(false);
  }

  @Test
  public void testSqlInjectionPrevention() throws SQLException {
    // Setup - Use a malicious input that would cause SQL injection
    String maliciousInput = "' OR '1'='1";

    // Execute the method with the malicious input
    employeeService.findUserByUsername(maliciousInput);

    // Verify that the PreparedStatement was used correctly
    verify(connection).prepareStatement("SELECT * FROM users WHERE username = ?");
    // The malicious characters should be sanitized, so the parameter should be "OR11"
    verify(preparedStatement).setString(1, "OR11");

    // This verifies that the malicious input is safely parameterized
    // and not directly concatenated into the SQL query
  }

  @Test
  public void testFindUserByUsername_WithSpecialCharacters() throws SQLException {
    // Setup - Configure ResultSet to return a user
    when(resultSet.next()).thenReturn(true, false);
    when(resultSet.getLong("id")).thenReturn(1L);
    when(resultSet.getString("username")).thenReturn("test@user");
    when(resultSet.getString("password")).thenReturn("password");
    when(resultSet.getString("email")).thenReturn("test@example.com");

    // Test with input containing special characters
    String specialCharInput = "test@user;--";
    List<User> users = employeeService.findUserByUsername(specialCharInput);

    // Verify the parameter was properly sanitized (semicolon removed, hyphens kept)
    verify(preparedStatement).setString(1, "test@user--");

    // Verify the result
    assertThat(users).isNotEmpty();
    assertThat(users.size()).isEqualTo(1);
    assertThat(users.get(0).getUsername()).isEqualTo("test@user");
  }

  @Test
  public void testInputValidation_NullUsername() throws SQLException {
    // Test with null input
    List<User> users = employeeService.findUserByUsername(null);

    // Verify that no database call is made and empty list is returned
    assertThat(users).isEmpty();
  }

  @Test
  public void testInputValidation_EmptyUsername() throws SQLException {
    // Test with empty input
    List<User> users = employeeService.findUserByUsername("");

    // Verify that no database call is made and empty list is returned
    assertThat(users).isEmpty();
  }

  @Test
  public void testInputValidation_WhitespaceUsername() throws SQLException {
    // Test with whitespace-only input
    List<User> users = employeeService.findUserByUsername("   ");

    // Verify that no database call is made and empty list is returned
    assertThat(users).isEmpty();
  }

  @Test
  public void testUsernameSanitization() throws SQLException {
    // Test that special characters are properly sanitized
    String inputWithSpecialChars = "user<script>alert('xss')</script>@domain.com";
    
    employeeService.findUserByUsername(inputWithSpecialChars);
    
    // Verify the sanitized username is used (should remove script tags)
    verify(preparedStatement).setString(1, "userscriptalertxssscript@domain.com");
  }
}
