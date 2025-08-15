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
  public void testFindUserByUsername_PreventsSqlInjection() throws SQLException {
    // Setup - SQL injection attempt
    String maliciousInput = "admin' OR '1'='1";

    // Execute the method with malicious input
    employeeService.findUserByUsername(maliciousInput);

    // Verify that the input was properly parameterized
    verify(connection).prepareStatement("SELECT * FROM users WHERE username = ?");
    verify(preparedStatement).setString(1, maliciousInput);

    // Verify that executeQuery was called on the PreparedStatement (not with the raw SQL)
    verify(preparedStatement).executeQuery();
  }

  @Test
  public void testFindUserByUsername_ReturnsUserData() throws SQLException {
    // Setup
    when(resultSet.next()).thenReturn(true, false); // Return true first time, then false
    when(resultSet.getLong("id")).thenReturn(1L);
    when(resultSet.getString("username")).thenReturn("testuser");
    when(resultSet.getString("password")).thenReturn("password");
    when(resultSet.getString("email")).thenReturn("test@example.com");

    // Execute
    List<User> users = employeeService.findUserByUsername("testuser");

    // Verify
    assertThat(users).hasSize(1);
    assertThat(users.get(0).getUsername()).isEqualTo("testuser");
    assertThat(users.get(0).getEmail()).isEqualTo("test@example.com");

    // Verify proper parameterization
    verify(preparedStatement).setString(1, "testuser");
  }
}
