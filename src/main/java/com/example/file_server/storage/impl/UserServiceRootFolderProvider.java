package com.example.file_server.storage.impl;

import com.example.file_server.service.UserService;
import com.example.file_server.storage.UserRootFolderProvider;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class UserServiceRootFolderProvider implements UserRootFolderProvider {
    private final UserService userService;

    public UserServiceRootFolderProvider(UserService userService) {
        this.userService = userService;
    }

    @Override
    public Path getCurrentUserRootFolder() {
        return userService.getCurrentUserRootFolder();
    }
}
