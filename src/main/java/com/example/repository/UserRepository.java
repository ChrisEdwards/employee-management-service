package com.example.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.model.User;

public interface UserRepository extends JpaRepository<User, Long> {
  User findByUsername(String username);

  default List<User> executeCustomQuery(String query) {
    return List.of(new User("demoUser", "password", "demo@example.com"));
  }
}
