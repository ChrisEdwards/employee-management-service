package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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
  public void testFindUserByUsername_WithSanitizedInput() throws SQLException {
    // Setup
    when(resultSet.next()).thenReturn(true, false);
    when(resultSet.getLong("id")).thenReturn(1L);
    when(resultSet.getString("username")).thenReturn("testuser");
    when(resultSet.getString("password")).thenReturn("password");
    when(resultSet.getString("email")).thenReturn("test@example.com");

    // Execute
    List<User> actualUsers = employeeService.findUserByUsername("testuser");

    // Verify
    assertThat(actualUsers).isNotEmpty();
    assertThat(actualUsers.size()).isEqualTo(1);
    assertThat(actualUsers.get(0).getUsername()).isEqualTo("testuser");

    // Verify PreparedStatement is used correctly
    verify(connection).prepareStatement("SELECT * FROM users WHERE username = ?");
    verify(preparedStatement).setString(1, "testuser");
  }

  @Test
  public void testFindUserByUsername_WithSQLInjectionAttempt() throws SQLException {
    // Setup
    when(resultSet.next()).thenReturn(false);

    // SQL injection attempt in the username parameter
    String maliciousInput = "' OR '1'='1"; // Classic SQL injection payload

    // Execute
    List<User> actualUsers = employeeService.findUserByUsername(maliciousInput);

    // Verify
    assertThat(actualUsers).isEmpty(); // The query returns no results

    // Verify that the malicious input was properly parameterized
    verify(connection).prepareStatement("SELECT * FROM users WHERE username = ?");
    verify(preparedStatement).setString(1, maliciousInput);

    // The important thing is that the malicious string was sent as a parameter,
    // not embedded in the SQL query itself
  }
}
