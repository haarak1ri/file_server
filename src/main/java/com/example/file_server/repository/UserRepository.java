package com.example.file_server.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import com.example.file_server.models.User;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    //Optional - результат может быть: пользователь найден или не найден
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
}
