package com.example.file_server.service;

import com.example.file_server.dto.UserResponseDto;
import com.example.file_server.models.User;
import com.example.file_server.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Path serverRoot;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, Path serverRoot) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.serverRoot = serverRoot;
    }
    @Transactional //указывает что все в методе - единая операция, если что то упало все откатывается. Предотваращает:
    //два вызова save два - отдельных insert/update если что то произойдет между ними - в базе останется мусор, пользователь без папки
    // или с пустым rootfolder
    public UserResponseDto register(String username, String password) {
        String passwordHash = passwordEncoder.encode(password);
        User user = userRepository.save(new User(username,passwordHash));
        user.setRootFolder("cloud-storage/users/" + user.getId());

        try {
            Files.createDirectories(serverRoot.resolve("cloud-storage/users/" + user.getId()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create user directory", e);
        }
        return new UserResponseDto(user.getId(), user.getUsername());
    }

    public User login(String username, String passwordHash) {
        // Найти пользователя
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public boolean userExists(String username) {
        return userRepository.existsByUsername(username);
    }


    public User getCurrentUser() {
        //Получаем объект аутентификации из контекста Spring Security
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        String username;
        if (principal instanceof UserDetails) {
            username = ((UserDetails) principal).getUsername();
        } else {
            username = principal.toString();
        }

        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Current user not found: " + username));
    }

    public Path getCurrentUserRootFolder() {
        User user = getCurrentUser();
        return serverRoot.resolve(user.getRootFolder());
    }
}
