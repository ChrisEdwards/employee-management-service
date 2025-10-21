package com.example.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.model.User;

@Service
public class EmployeeService {

  @Autowired private javax.sql.DataSource dataSource;

  /** Safely finds users by username using a prepared statement to prevent SQL injection. */
  public List<User> findUserByUsername(String username) {
    List<User> users = new java.util.ArrayList<>();

    // Input validation to prevent null/empty values
    if (username == null || username.trim().isEmpty()) {
      return users;
    }

    // Sanitize username to prevent potential issues
    String sanitizedUsername = sanitizeUsername(username);

    String query = "SELECT * FROM users WHERE username = ?";

    try {
      java.sql.Connection connection = dataSource.getConnection();
      java.sql.PreparedStatement preparedStatement = connection.prepareStatement(query);
      preparedStatement.setString(1, sanitizedUsername);

      try (java.sql.ResultSet resultSet = preparedStatement.executeQuery()) {

        while (resultSet.next()) {
          User user = new User();
          user.setId(resultSet.getLong("id"));
          user.setUsername(resultSet.getString("username"));
          user.setPassword(resultSet.getString("password"));
          user.setEmail(resultSet.getString("email"));
          users.add(user);
        }
      } catch (java.sql.SQLException e) {
        System.err.println("SQL Error: " + e.getMessage());
        System.err.println("SQL State: " + e.getSQLState());
        System.err.println("Error Code: " + e.getErrorCode());
        e.printStackTrace();
      }
    } catch (java.sql.SQLException e) {
      System.err.println("SQL Error: " + e.getMessage());
      System.err.println("SQL State: " + e.getSQLState());
      System.err.println("Error Code: " + e.getErrorCode());
      e.printStackTrace();
    }

    return users;
  }

  /**
   * Sanitizes username input to prevent potential security issues.
   * This method provides an additional layer of security beyond prepared statements.
   */
  private String sanitizeUsername(String username) {
    if (username == null) {
      return null;
    }
    
    // Remove potentially dangerous characters while preserving valid username characters
    // Allow alphanumeric, underscore, hyphen, dot, and @ symbol for email-style usernames
    return username.replaceAll("[^a-zA-Z0-9._@-]", "").trim();
  }

  public String fetchDataFromUrl(String url) {
    try {
      URL targetUrl = new URL(url);
      HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
      connection.setRequestMethod("GET");

      BufferedReader reader =
          new BufferedReader(new InputStreamReader(connection.getInputStream()));
      String line;
      StringBuilder response = new StringBuilder();

      while ((line = reader.readLine()) != null) {
        response.append(line);
      }
      reader.close();

      return response.toString();
    } catch (Exception e) {
      return "Error fetching URL: " + e.getMessage();
    }
  }

  public String executeCommand(String command) {
    try {
      Process process = Runtime.getRuntime().exec(command);

      BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      StringBuilder output = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        output.append(line).append("\n");
      }

      BufferedReader errorReader =
          new BufferedReader(new InputStreamReader(process.getErrorStream()));
      while ((line = errorReader.readLine()) != null) {
        output.append("ERROR: ").append(line).append("\n");
      }

      process.waitFor();
      return output.toString();
    } catch (Exception e) {
      return "Error executing command: " + e.getMessage();
    }
  }
}
