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
  public void testFindUserByUsername_ValidInput() throws SQLException {
    // Setup
    // Configure ResultSet to return a single user
    when(resultSet.next())
        .thenReturn(true, false); // Return true first time, then false to end loop
    when(resultSet.getLong("id")).thenReturn(1L);
    when(resultSet.getString("username")).thenReturn("bob.jones");
    when(resultSet.getString("password")).thenReturn("password");
    when(resultSet.getString("email")).thenReturn("bob.jones@example.com");

    // Test
    List<User> actualUsers = employeeService.findUserByUsername("bob.jones");

    // Verify PreparedStatement is used with parameter binding
    verify(connection).prepareStatement("SELECT * FROM users WHERE username = ?");
    verify(preparedStatement).setString(1, "bob.jones");
    verify(preparedStatement).executeQuery();

    // Verify result
    assertThat(actualUsers).isNotEmpty();
    assertThat(actualUsers.size()).isEqualTo(1);
    assertThat(actualUsers.get(0).getUsername()).isEqualTo("bob.jones");
  }

  @Test
  public void testFindUserByUsername_SqlInjectionAttempt() throws SQLException {
    // Setup for SQL injection attempt
    String sqlInjection = "' OR '1'='1";

    // Test with SQL injection attempt
    employeeService.findUserByUsername(sqlInjection);

    // Verify that the SQL injection string is properly parameterized
    verify(connection).prepareStatement("SELECT * FROM users WHERE username = ?");
    verify(preparedStatement).setString(1, sqlInjection);
    verify(preparedStatement).executeQuery();

    // No need to verify results as we're just checking that the query was properly prepared
  }
}
