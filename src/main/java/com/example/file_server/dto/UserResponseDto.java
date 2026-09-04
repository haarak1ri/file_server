package com.example.file_server.dto;

public class UserResponseDto {
    private final Long id;
    private final String username;

    public UserResponseDto(Long id, String username) {
        this.id = id;
        this.username = username;
    }

    public Long getId() {return id; }
    public String getUsername() {return username;}

}
