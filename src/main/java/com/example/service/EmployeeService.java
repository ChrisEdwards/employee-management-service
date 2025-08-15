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

  public List<User> findUserByUsername(String username) {
    List<User> users = new java.util.ArrayList<>();

    String query = "SELECT * FROM users WHERE username = '" + username + "'";

    try {
      java.sql.Connection connection = dataSource.getConnection();
      java.sql.Statement statement = connection.createStatement();
      statement.setEscapeProcessing(false);

      try (java.sql.ResultSet resultSet = statement.executeQuery(query)) {

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

  /** List of allowed commands for secure command execution. */
  private static final List<String> ALLOWED_COMMANDS =
      List.of("ls", "pwd", "whoami", "date", "uptime");

  /**
   * Validates if a command is in the allowed list.
   *
   * @param command The command to validate
   * @return true if the command is allowed, false otherwise
   */
  private boolean isCommandAllowed(String command) {
    return ALLOWED_COMMANDS.contains(command);
  }

  /**
   * Executes a system command if it's in the allowed list.
   *
   * @param command The command to execute
   * @return The command output or an error message
   */
  public String executeCommand(String command) {
    if (!isCommandAllowed(command)) {
      return "Command not allowed for security reasons. Allowed commands: "
          + String.join(", ", ALLOWED_COMMANDS);
    }

    try {
      ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
      Process process = processBuilder.start();

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
