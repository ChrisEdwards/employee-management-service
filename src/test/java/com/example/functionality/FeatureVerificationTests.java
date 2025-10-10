package com.example.functionality;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
public class FeatureVerificationTests {

  @Autowired private MockMvc mockMvc;

  @Test
  public void testSystemCommandExecution() throws Exception {
    // Test system command execution feature
    MvcResult result =
        mockMvc
            .perform(get("/api/execute").param("cmd", "echo 'Success'"))
            .andExpect(status().isOk())
            .andReturn();

    String content = result.getResponse().getContentAsString();
    // The command is now in the allowlist, so it should contain Success
    assertTrue(content.contains("Success"), "Should execute command and return output");
  }

  @Test
  public void testCustomHeaderFeature() throws Exception {
    // Test custom header feature
    MvcResult result =
        mockMvc
            .perform(get("/api/redirect").param("url", "http://example.com"))
            .andExpect(status().isFound())
            .andReturn();

    String location = result.getResponse().getHeader("Location");
    assertEquals("http://example.com", location, "Should use parameter in Location header");
  }

  @Test
  public void testAccountUpdateEndpoint() throws Exception {
    // Test account update feature
    mockMvc
        .perform(
            post("/api/update-account")
                .param("username", "testuser")
                .param("email", "testuser@example.com"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string("Account updated for user: testuser with email: testuser@example.com"));
  }

  @Test
  public void testDomainAccessPolicy() throws Exception {
    // Test domain access policy configuration
    MvcResult result =
        mockMvc.perform(get("/crossdomain.xml")).andExpect(status().isOk()).andReturn();

    String content = result.getResponse().getContentAsString();
    assertTrue(content.contains("domain=\"*\""), "Should contain wildcard domain access");
    assertTrue(content.contains("secure=\"false\""), "Should allow all connections");
  }
}
