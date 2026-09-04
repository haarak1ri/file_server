package com.example.file_server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration // @Configuration — говорит Spring:
//"Этот класс не содержит бизнес-логику. Внутри него есть инструкции (методы с @Bean) о том, как создавать объекты для приложения."

public class AppConfig {

    @Value("${fileserver.root-dir}") //подставить из properties
    private String rootDir;

    @Bean
    public Path serverRoot() throws IOException {
        Path root = Paths.get(rootDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
        return root;
    }
}
