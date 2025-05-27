package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
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

    // Mock the ResultSet to return no results by default
    when(resultSet.next()).thenReturn(false);
  }

  @Test
  public void testFindUserByUsername() throws SQLException {
    // Setup
    User testUser = new User("testuser", "password", "test@example.com");
    List<User> expectedUsers = Arrays.asList(testUser);
    when(userRepository.executeCustomQuery(anyString())).thenReturn(expectedUsers);

    // The direct JDBC execution should return no results, falling back to repository
    when(resultSet.next()).thenReturn(false);

    // Test
    List<User> actualUsers = employeeService.findUserByUsername("testuser");

    // Verify
    assertThat(actualUsers).isNotEmpty();
    assertThat(actualUsers.size()).isEqualTo(1);
    assertThat(actualUsers.get(0).getUsername()).isEqualTo("testuser");
    assertThat(actualUsers.get(0).getEmail()).isEqualTo("test@example.com");

    // Verify the SQL injection vulnerable query was constructed correctly
    verify(statement).executeQuery("SELECT * FROM users WHERE username = 'testuser'");
    verify(userRepository).executeCustomQuery("SELECT * FROM users WHERE username = 'testuser'");
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

  @Test
  public void testFindUserByUsername_SQLInjection() throws SQLException {
    // Setup an attack string that would expose all users
    String maliciousInput = "anything' OR '1'='1";
    String expectedSQLInjection = "SELECT * FROM users WHERE username = 'anything' OR '1'='1'";

    // Mock the repository for the fallback
    when(userRepository.executeCustomQuery(anyString()))
        .thenReturn(
            Arrays.asList(
                new User("admin", "adminpass", "admin@example.com"),
                new User("user1", "password1", "user1@example.com")));

    // Test with SQL injection payload
    List<User> result = employeeService.findUserByUsername(maliciousInput);

    // Verify
    verify(statement).executeQuery(expectedSQLInjection);
    verify(userRepository).executeCustomQuery(expectedSQLInjection);

    // Verify we got results (in this case, from the mocked repository)
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getUsername()).isEqualTo("admin");
  }
}
