package com.example.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
public class SecurityConfigSecurityTest {

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Test
  public void testPasswordEncoderIsBCrypt() {
    assertNotNull(passwordEncoder, "Password encoder should not be null");
    assertTrue(passwordEncoder instanceof BCryptPasswordEncoder, 
        "Password encoder should be BCryptPasswordEncoder");
  }
  
  @Test
  public void testPasswordEncodingDoesNotUseWeakAlgorithm() {
    String rawPassword = "testPassword123";
    String encodedPassword = passwordEncoder.encode(rawPassword);
    
    // BCrypt passwords start with $2a$, $2b$ or $2y$ indicating the BCrypt algorithm
    assertTrue(
        encodedPassword.startsWith("$2a$") || 
        encodedPassword.startsWith("$2b$") || 
        encodedPassword.startsWith("$2y$"),
        "Password should be encoded with BCrypt, not SHA-1 or other weak algorithms"
    );
  }
}