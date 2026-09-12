package com.wikiplays.repository;

import com.wikiplays.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByDummyTrue();

    java.util.List<User> findByDummyTrue();
}
