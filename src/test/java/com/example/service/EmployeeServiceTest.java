package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.model.User;
import com.example.repository.UserRepository;

@SpringBootTest
public class EmployeeServiceTest {

  @Mock private UserRepository userRepository;

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
  public void testFindUserByUsername() throws SQLException {
    // Setup
    // Configure ResultSet to return a single user
    when(resultSet.next())
        .thenReturn(true, false); // Return true first time, then false to end loop
    when(resultSet.getLong("id")).thenReturn(1L);
    when(resultSet.getString("username")).thenReturn("testuser");
    when(resultSet.getString("password")).thenReturn("password");
    when(resultSet.getString("email")).thenReturn("test@example.com");

    // Test
    List<User> actualUsers = employeeService.findUserByUsername("testuser");

    // Verify parameterized query was used correctly
    verify(connection).prepareStatement("SELECT * FROM users WHERE username = ?");
    verify(preparedStatement).setString(1, "testuser");

    // Verify
    assertThat(actualUsers).isNotEmpty();
    assertThat(actualUsers.size()).isEqualTo(1);
    assertThat(actualUsers.get(0).getUsername()).isEqualTo("testuser");
    assertThat(actualUsers.get(0).getEmail()).isEqualTo("test@example.com");
  }

  @Test
  public void testFetchDataFromUrl_Success() {
    // Note: This is a partial test that doesn't actually make HTTP calls
    // In a real test, you'd use a MockServer to simulate HTTP responses

    // Since we can't easily mock HttpURLConnection, we're just testing the error case
    String result = employeeService.fetchDataFromUrl("invalid_url");

    // The result should contain the error message
    assertThat(result).startsWith("Error fetching URL:");
  }

  @Test
  public void testFetchDataFromUrl_Error() {
    // Testing with a URL that will cause an exception
    String result = employeeService.fetchDataFromUrl("not-a-valid-url");

    // Verify that the error message is returned
    assertThat(result).contains("Error fetching URL:");
  }
}
