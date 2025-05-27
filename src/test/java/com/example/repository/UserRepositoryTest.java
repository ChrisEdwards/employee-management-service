package com.example.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.model.User;

@SpringBootTest
public class UserRepositoryTest {

  @Autowired private UserRepository userRepository;

  @Test
  public void testFindByUsername() {
    // Given: A test user in the database
    User user = new User("testRepoUser", "password", "repo@example.com");

    // Check for existing users with the same username
    User existingUser = userRepository.findByUsername("testRepoUser");
    if (existingUser == null) {
      // Save the user to the database
      user = userRepository.save(user);
    }

    // When: We search for that user
    User foundUser = userRepository.findByUsername("testRepoUser");

    // Then: The user should be found
    assertThat(foundUser).isNotNull();
    assertThat(foundUser.getUsername()).isEqualTo("testRepoUser");
  }

  @Test
  public void testExecuteCustomQuery() {
    // Test the custom query execution
    List<User> users = userRepository.executeCustomQuery("SELECT * FROM users");

    // This method has a placeholder implementation that always returns a demo user
    // So we're just testing that we get a non-empty result
    assertThat(users).isNotEmpty();
    assertThat(users.get(0).getUsername()).isEqualTo("demoUser");
  }
}
