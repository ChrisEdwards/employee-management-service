package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

import com.example.model.User;

public class EmployeeServiceSecurityTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet resultSet;

    @InjectMocks
    private EmployeeService employeeService;

    @BeforeEach
    public void setup() throws SQLException {
        MockitoAnnotations.openMocks(this);

        // Configure mocks
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false); // No results by default
    }

    @Test
    public void testFindUserByUsername_SQLInjectionAttempt() throws SQLException {
        // Setup - SQL injection payload
        String sqlInjectionPayload = "' OR '1'='1";
        
        // Execute
        List<User> users = employeeService.findUserByUsername(sqlInjectionPayload);
        
        // Verify that the payload was properly parameterized
        verify(preparedStatement).setString(1, sqlInjectionPayload);
        
        // Verify the payload is treated as a literal parameter value
        // and not part of the SQL syntax
        assertThat(users).isEmpty();
    }

    @Test
    public void testFindUserByUsername_ValidInput() throws SQLException {
        // Setup
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong("id")).thenReturn(1L);
        when(resultSet.getString("username")).thenReturn("testUser");
        when(resultSet.getString("password")).thenReturn("password");
        when(resultSet.getString("email")).thenReturn("test@example.com");
        
        // Execute
        List<User> users = employeeService.findUserByUsername("testUser");
        
        // Verify proper parameterization
        verify(preparedStatement).setString(1, "testUser");
        
        // Verify results
        assertThat(users).hasSize(1);
        assertThat(users.get(0).getUsername()).isEqualTo("testUser");
    }
}