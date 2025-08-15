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

  @GetMapping("/execute/help")
  public String executeCommandHelp() {
    return "Available commands: list_files, disk_space, memory_usage, current_dir";
  }

  @GetMapping("/redirect")
  public ResponseEntity<String> redirectExample(@RequestParam String url) {
    HttpHeaders headers = new HttpHeaders();

    headers.add("Location", url);

    headers.add("X-Custom-Header", "Referrer: " + url);

    return new ResponseEntity<>("Redirecting to: " + url, headers, HttpStatus.FOUND);
  }

  @PostMapping("/update-account")
  public String updateAccount(@RequestParam String username, @RequestParam String email) {
    return "Account updated for user: " + username + " with email: " + email;
  }
}
