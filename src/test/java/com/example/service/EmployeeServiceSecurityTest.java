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

    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
    when(preparedStatement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(false);
  }

  @Test
  public void testFindUserByUsername_WithSqlInjectionAttempt() throws SQLException {
    String maliciousInput = "admin' OR '1'='1";

    when(resultSet.next()).thenReturn(true, false);
    when(resultSet.getLong("id")).thenReturn(1L);
    when(resultSet.getString("username")).thenReturn("admin");
    when(resultSet.getString("password")).thenReturn("password");
    when(resultSet.getString("email")).thenReturn("admin@example.com");

    List<User> users = employeeService.findUserByUsername(maliciousInput);

    verify(preparedStatement).setString(1, maliciousInput);
    verify(connection).prepareStatement("SELECT * FROM users WHERE username = ?");

    assertThat(users).isNotEmpty();
    assertThat(users.size()).isEqualTo(1);
  }

  @Test
  public void testFindUserByUsername_WithSingleQuoteInUsername() throws SQLException {
    String usernameWithQuote = "O'Brien";

    when(resultSet.next()).thenReturn(true, false);
    when(resultSet.getLong("id")).thenReturn(2L);
    when(resultSet.getString("username")).thenReturn("O'Brien");
    when(resultSet.getString("password")).thenReturn("password");
    when(resultSet.getString("email")).thenReturn("obrien@example.com");

    List<User> users = employeeService.findUserByUsername(usernameWithQuote);

    verify(preparedStatement).setString(1, usernameWithQuote);

    assertThat(users).isNotEmpty();
    assertThat(users.get(0).getUsername()).isEqualTo("O'Brien");
  }

  @Test
  public void testFindUserByUsername_WithUnionBasedInjection() throws SQLException {
    String maliciousInput = "admin' UNION SELECT * FROM passwords--";

    when(resultSet.next()).thenReturn(false);

    List<User> users = employeeService.findUserByUsername(maliciousInput);

    verify(preparedStatement).setString(1, maliciousInput);
    verify(connection).prepareStatement("SELECT * FROM users WHERE username = ?");

    assertThat(users).isEmpty();
  }

  @Test
  public void testFindUserByUsername_WithNormalUsername() throws SQLException {
    String normalUsername = "john.doe";

    when(resultSet.next()).thenReturn(true, false);
    when(resultSet.getLong("id")).thenReturn(3L);
    when(resultSet.getString("username")).thenReturn("john.doe");
    when(resultSet.getString("password")).thenReturn("password");
    when(resultSet.getString("email")).thenReturn("john.doe@example.com");

    List<User> users = employeeService.findUserByUsername(normalUsername);

    verify(preparedStatement).setString(1, normalUsername);

    assertThat(users).isNotEmpty();
    assertThat(users.get(0).getUsername()).isEqualTo("john.doe");
  }
}
