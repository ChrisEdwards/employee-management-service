package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
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
  public void testSqlInjectionAttemptIsHandledSafely() throws SQLException {
    // Setup
    String sqlInjectionPayload = "' OR '1'='1";

    // Configure ResultSet to return a single user (this shouldn't happen with SQL injection)
    when(resultSet.next()).thenReturn(false);

    // Test
    List<User> results = employeeService.findUserByUsername(sqlInjectionPayload);

    // Verify
    // The PreparedStatement should be used with setString for the parameter
    verify(preparedStatement).setString(1, sqlInjectionPayload);

    // The attack should not return any users
    assertThat(results).isEmpty();
  }

  @Test
  public void testNormalUsernameSearchStillWorks() throws SQLException {
    // Setup
    String username = "normaluser";

    // Configure ResultSet to return a single user
    when(resultSet.next())
        .thenReturn(true, false); // Return true first time, then false to end loop
    when(resultSet.getLong("id")).thenReturn(1L);
    when(resultSet.getString("username")).thenReturn(username);
    when(resultSet.getString("password")).thenReturn("password123");
    when(resultSet.getString("email")).thenReturn("normal@example.com");

    // Test
    List<User> users = employeeService.findUserByUsername(username);

    // Verify
    verify(preparedStatement).setString(1, username);
    assertThat(users).isNotEmpty();
    assertThat(users).hasSize(1);
    assertThat(users.get(0).getUsername()).isEqualTo(username);
  }
}
