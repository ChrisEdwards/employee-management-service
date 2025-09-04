package com.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.config.SecurityConfig;
import com.example.model.User;
import com.example.service.EmployeeService;

@WebMvcTest(EmployeeController.class)
@Import(SecurityConfig.class) // Import the security configuration to disable CSRF protection
public class EmployeeControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private EmployeeService employeeService;

  @Test
  public void testUserSearchExample() throws Exception {
    // Setup
    User testUser = new User("testuser", "password", "test@example.com");
    List<User> users = Arrays.asList(testUser);
    when(employeeService.findUserByUsername(anyString())).thenReturn(users);

    // Test
    MvcResult result =
        mockMvc
            .perform(
                get("/api/user-search")
                    .param("username", "testuser")
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();

    // Verify
    String content = result.getResponse().getContentAsString();
    assertThat(content).contains("testuser");
    assertThat(content).contains("test@example.com");
  }

  @Test
  public void testRenderContentExample() throws Exception {
    // Test
    mockMvc
        .perform(
            get("/api/render-content")
                .param("userInput", "Test Content")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<h1>User Input:</h1>")))
        .andExpect(content().string(containsString("<p>Test Content</p>")));
  }

  @Test
  public void testFetchUrlExample() throws Exception {
    // Setup
    String mockResponse = "Response from external service";
    when(employeeService.fetchDataFromUrl(anyString())).thenReturn(mockResponse);

    // Test
    mockMvc
        .perform(
            get("/api/fetch-url")
                .param("url", "http://example.com")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(mockResponse)));
  }

  @Test
  public void testExecuteCommandExample() throws Exception {
    // Setup
    String mockResponse = "Command output";
    when(employeeService.executeCommand(anyString())).thenReturn(mockResponse);

    // Test
    mockMvc
        .perform(get("/api/execute").param("cmd", "ls").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(mockResponse)));
  }
}
