package com.example.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
public class SecurityConfigSecurityTest {

  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  public void testPasswordEncoderIsBCrypt() {
    // Verify that the configured PasswordEncoder is BCryptPasswordEncoder
    assertTrue(
        passwordEncoder instanceof BCryptPasswordEncoder,
        "PasswordEncoder should be an instance of BCryptPasswordEncoder");
  }

  @Test
  public void testPasswordEncoderEncryption() {
    // Test that the password encoder works correctly
    String rawPassword = "testPassword123!";
    String encodedPassword = passwordEncoder.encode(rawPassword);

    // BCrypt encoded passwords should start with $2a$ or similar ($2b$, $2y$)
    assertTrue(
        encodedPassword.startsWith("$2"),
        "Encoded password should use BCrypt format starting with $2");

    // Verify that the password matches when checked
    assertTrue(
        passwordEncoder.matches(rawPassword, encodedPassword),
        "Password encoder should correctly match raw password with encoded version");
  }
}
