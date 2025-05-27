package com.example.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class UserTest {

  @Test
  public void testUserConstructorAndGetters() {
    // Create a new user instance
    User user = new User("testuser", "password123", "test@example.com");

    // Test getters
    assertThat(user.getUsername()).isEqualTo("testuser");
    assertThat(user.getPassword()).isEqualTo("password123");
    assertThat(user.getEmail()).isEqualTo("test@example.com");

    // ID should be null as it hasn't been persisted
    assertThat(user.getId()).isNull();
  }

  @Test
  public void testUserSetters() {
    // Create a new user instance
    User user = new User();

    // Set values using setters
    user.setId(1L);
    user.setUsername("newuser");
    user.setPassword("newpassword");
    user.setEmail("new@example.com");

    // Test that values were set correctly
    assertThat(user.getId()).isEqualTo(1L);
    assertThat(user.getUsername()).isEqualTo("newuser");
    assertThat(user.getPassword()).isEqualTo("newpassword");
    assertThat(user.getEmail()).isEqualTo("new@example.com");
  }

  @Test
  public void testToString() {
    // Create a new user instance
    User user = new User("testuser", "password123", "test@example.com");
    user.setId(1L);

    // Test toString() method
    String expectedToString = "User [id=1, username=testuser, email=test@example.com]";
    assertThat(user.toString()).isEqualTo(expectedToString);
  }
}
