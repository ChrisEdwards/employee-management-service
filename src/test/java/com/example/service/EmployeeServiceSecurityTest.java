package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
  @Mock private Statement statement;
  @Mock private ResultSet resultSet;

  @InjectMocks private EmployeeService employeeService;

  @BeforeEach
  public void setup() throws SQLException {
    MockitoAnnotations.openMocks(this);

    // Configure mocks
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
    when(preparedStatement.executeQuery()).thenReturn(resultSet);

    // Statement would be used in vulnerable code
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery(anyString())).thenReturn(resultSet);

    // Mock empty result by default
    when(resultSet.next()).thenReturn(false);
  }

  @Test
  public void testFindUserByUsername_PreventsSqlInjection() throws SQLException {
    // Arrange - Use a malicious username input with SQL injection attempt
    String maliciousUsername = "' OR '1'='1"; // Would return all users in vulnerable code

    // Act
    employeeService.findUserByUsername(maliciousUsername);

    // Assert
    // Verify that we use PreparedStatement instead of Statement
    verify(connection).prepareStatement(anyString());
    verify(preparedStatement).setString(1, maliciousUsername);
    verify(preparedStatement).executeQuery();

    // Verify that the vulnerable path is not used
    verify(connection, never()).createStatement();
    verify(statement, never()).executeQuery(anyString());

    // This test verifies that:
    // 1. PreparedStatement is used (not Statement)
    // 2. The malicious input is safely parameterized
    // 3. The raw executeQuery with string concatenation is never called
  }

  @Test
  public void testFindUserByUsername_NormalInputWorks() throws SQLException {
    // Arrange - Configure ResultSet for a normal user
    when(resultSet.next()).thenReturn(true, false);
    when(resultSet.getLong("id")).thenReturn(1L);
    when(resultSet.getString("username")).thenReturn("normaluser");
    when(resultSet.getString("password")).thenReturn("password");
    when(resultSet.getString("email")).thenReturn("normal@example.com");

    // Act
    List<User> result = employeeService.findUserByUsername("normaluser");

    // Assert - Ensure normal functionality still works
    assertThat(result).isNotEmpty();
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.get(0).getUsername()).isEqualTo("normaluser");
    assertThat(result.get(0).getEmail()).isEqualTo("normal@example.com");

    // And verify the secure approach was used
    verify(preparedStatement).setString(1, "normaluser");
    verify(preparedStatement).executeQuery();
  }
}
