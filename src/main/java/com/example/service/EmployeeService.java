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

  /**
   * List of allowed commands that can be executed.
   */
  private static final java.util.Map<String, String[]> ALLOWED_COMMANDS = new java.util.HashMap<>();
  
  static {
    // Define allowed commands with their arguments
    ALLOWED_COMMANDS.put("ls", new String[]{"ls", "-l"});
    ALLOWED_COMMANDS.put("pwd", new String[]{"pwd"});
    ALLOWED_COMMANDS.put("whoami", new String[]{"whoami"});
    ALLOWED_COMMANDS.put("date", new String[]{"date"});
    ALLOWED_COMMANDS.put("echo", new String[]{"echo", "Hello World"});
  }
  
  /**
   * Validates if a command is in the allowed list and executes it safely.
   */
  private String validateAndExecuteCommand(String commandKey) {
    if (!ALLOWED_COMMANDS.containsKey(commandKey)) {
      return "Error: Command not allowed. Allowed commands are: " + String.join(", ", ALLOWED_COMMANDS.keySet());
    }
    
    try {
      String[] commandArray = ALLOWED_COMMANDS.get(commandKey);
      ProcessBuilder processBuilder = new ProcessBuilder(commandArray);
      Process process = processBuilder.start();
      
      BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      StringBuilder output = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        output.append(line).append("\n");
      }
      
      BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
      while ((line = errorReader.readLine()) != null) {
        output.append("ERROR: ").append(line).append("\n");
      }
      
      process.waitFor();
      return output.toString();
    } catch (Exception e) {
      return "Error executing command: " + e.getMessage();
    }
  }
  
  public String executeCommand(String command) {
    return validateAndExecuteCommand(command);
  }
}
