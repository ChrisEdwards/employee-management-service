package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.model.User;
import com.example.repository.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class IntegrationTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private UserRepository userRepository;

  private String baseUrl;

  @BeforeEach
  public void setUp() {
    baseUrl = "http://localhost:" + port + "/api";
  }

  @Test
  public void testUserSearchEndpoint() {
    // Test the user search endpoint
    ResponseEntity<User[]> response =
        restTemplate.getForEntity(baseUrl + "/user-search?username=testUser", User[].class);

    // Check response status
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    // Check we have at least one result
    User[] users = response.getBody();
    assertThat(users).isNotEmpty();

    // Check the result contains expected user details
    assertThat(users[0].getUsername()).isEqualTo("demoUser");
  }

  @Test
  public void testRenderContentEndpoint() {
    // Test the HTML rendering endpoint
    ResponseEntity<String> response =
        restTemplate.getForEntity(
            baseUrl + "/render-content?userInput=IntegrationTest", String.class);

    // Check response status
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    // Check the HTML content contains our input
    assertThat(response.getBody()).contains("IntegrationTest");
    assertThat(response.getBody()).contains("<h1>User Input:</h1>");
  }

  // Note: We don't test the fetch-url endpoint in integration tests
  // because it would require actual HTTP connections to external servers
}
