package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
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
import org.mockito.ArgumentCaptor;
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

    when(resultSet.next()).thenReturn(false);

    List<User> result = employeeService.findUserByUsername(maliciousInput);

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(queryCaptor.capture());

    String capturedQuery = queryCaptor.getValue();
    assertThat(capturedQuery).isEqualTo("SELECT * FROM users WHERE username = ?");

    ArgumentCaptor<String> paramCaptor = ArgumentCaptor.forClass(String.class);
    verify(preparedStatement).setString(anyInt(), paramCaptor.capture());

    String capturedParam = paramCaptor.getValue();
    assertThat(capturedParam).isEqualTo(maliciousInput);

    assertThat(result).isEmpty();
  }

  @Test
  public void testFindUserByUsername_WithUnionInjectionAttempt() throws SQLException {
    String maliciousInput = "admin' UNION SELECT * FROM passwords--";

    when(resultSet.next()).thenReturn(false);

    List<User> result = employeeService.findUserByUsername(maliciousInput);

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(queryCaptor.capture());

    String capturedQuery = queryCaptor.getValue();
    assertThat(capturedQuery).isEqualTo("SELECT * FROM users WHERE username = ?");

    ArgumentCaptor<String> paramCaptor = ArgumentCaptor.forClass(String.class);
    verify(preparedStatement).setString(anyInt(), paramCaptor.capture());

    String capturedParam = paramCaptor.getValue();
    assertThat(capturedParam).isEqualTo(maliciousInput);

    assertThat(result).isEmpty();
  }

  @Test
  public void testFindUserByUsername_WithNormalInput() throws SQLException {
    String normalInput = "testuser";

    when(resultSet.next()).thenReturn(true, false);
    when(resultSet.getLong("id")).thenReturn(1L);
    when(resultSet.getString("username")).thenReturn("testuser");
    when(resultSet.getString("password")).thenReturn("password123");
    when(resultSet.getString("email")).thenReturn("test@example.com");

    List<User> result = employeeService.findUserByUsername(normalInput);

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(queryCaptor.capture());

    String capturedQuery = queryCaptor.getValue();
    assertThat(capturedQuery).isEqualTo("SELECT * FROM users WHERE username = ?");

    ArgumentCaptor<String> paramCaptor = ArgumentCaptor.forClass(String.class);
    verify(preparedStatement).setString(anyInt(), paramCaptor.capture());

    String capturedParam = paramCaptor.getValue();
    assertThat(capturedParam).isEqualTo(normalInput);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getUsername()).isEqualTo("testuser");
    assertThat(result.get(0).getEmail()).isEqualTo("test@example.com");
  }

  @Test
  public void testFindUserByUsername_WithSpecialCharacters() throws SQLException {
    String inputWithSpecialChars = "user'name\"with;special--chars";

    when(resultSet.next()).thenReturn(false);

    List<User> result = employeeService.findUserByUsername(inputWithSpecialChars);

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(queryCaptor.capture());

    String capturedQuery = queryCaptor.getValue();
    assertThat(capturedQuery).isEqualTo("SELECT * FROM users WHERE username = ?");

    ArgumentCaptor<String> paramCaptor = ArgumentCaptor.forClass(String.class);
    verify(preparedStatement).setString(anyInt(), paramCaptor.capture());

    String capturedParam = paramCaptor.getValue();
    assertThat(capturedParam).isEqualTo(inputWithSpecialChars);

    assertThat(result).isEmpty();
  }
}
