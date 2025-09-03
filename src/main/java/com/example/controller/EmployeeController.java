package com.example.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.model.User;
import com.example.service.EmployeeService;

@RestController
@RequestMapping("/api")
public class EmployeeController {

  @Autowired private EmployeeService employeeService;

  @GetMapping("/user-search")
  public List<User> userSearchExample(@RequestParam String username) {
    return employeeService.findUserByUsername(username);
  }

  @GetMapping("/render-content")
  public String renderContentExample(@RequestParam String userInput) {
    return "<html><body><h1>User Input:</h1><p>" + userInput + "</p></body></html>";
  }

  @GetMapping("/fetch-url")
  public String fetchUrlExample(@RequestParam String url) {
    return employeeService.fetchDataFromUrl(url);
  }

  @GetMapping("/execute")
  public String executeCommandExample(@RequestParam String cmd) {
    return employeeService.executeCommand(cmd);
  }

  /**
   * Validates a URL to prevent header injection attacks.
   *
   * @param url The URL to validate
   * @return The validated URL if safe, or null if unsafe
   */
  private String validateUrlForHeaderSafety(String url) {
    if (url == null) {
      return null;
    }

    // Extract the base URL without any potential injection characters
    // This will stop at the first CR, LF or other control character
    int endOfUrl = url.length();
    for (int i = 0; i < url.length(); i++) {
      char c = url.charAt(i);
      if (c == '\r' || c == '\n' || c == '\t' || c == '\f') {
        endOfUrl = i;
        break;
      }
    }
    
    String sanitizedUrl = url.substring(0, endOfUrl);

    // Basic URL validation - must start with http:// or https://
    if (!sanitizedUrl.matches("^https?://.*")) {
      return null;
    }

    return sanitizedUrl;
  }

  @GetMapping("/redirect")
  public ResponseEntity<String> redirectExample(@RequestParam String url) {
    String validatedUrl = validateUrlForHeaderSafety(url);

    if (validatedUrl == null) {
      return new ResponseEntity<>("Invalid URL provided", HttpStatus.BAD_REQUEST);
    }

    HttpHeaders headers = new HttpHeaders();
    headers.add("Location", validatedUrl);
    headers.add("X-Custom-Header", "Referrer: " + validatedUrl);

    return new ResponseEntity<>("Redirecting to: " + validatedUrl, headers, HttpStatus.FOUND);
  }

  @PostMapping("/update-account")
  public String updateAccount(@RequestParam String username, @RequestParam String email) {
    return "Account updated for user: " + username + " with email: " + email;
  }
}
