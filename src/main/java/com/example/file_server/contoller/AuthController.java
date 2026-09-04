package com.example.file_server.contoller;

import com.example.file_server.dto.RegisterRequest;
import com.example.file_server.dto.UserResponseDto;
import com.example.file_server.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponseDto> register(@Valid @RequestBody RegisterRequest request) {
        if (userService.userExists(request.getUsername())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(null);
        }
        UserResponseDto response = userService.register(request.getUsername(), request.getPassword());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}