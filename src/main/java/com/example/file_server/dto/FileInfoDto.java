package com.example.file_server.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.Getter;

import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Getter
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FileInfoDto {
    private String name;
    private boolean isDirectory;
    private long size;
    private long lastModifiedMillis;  //  миллисекунды для сортировки
    private String lastModified;
    private String extension;


    public FileInfoDto(String name, boolean isDirectory, long size, FileTime fileTime) {
        this.name = name;
        this.isDirectory = isDirectory;
        this.size = size;

        if (fileTime != null) {
            this.lastModifiedMillis = fileTime.toMillis();
            this.lastModified = formatFileTime(fileTime);
        }
        this.extension = isDirectory? "" : extractExtension(name);
    }

    private static String extractExtension(String name) {
        if (name == null) return "";
        int dotidx = name.lastIndexOf('.');
        return (dotidx > 0) ? name.substring(dotidx + 1).toLowerCase() : "";
    }


    public String getName() {
        return name;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public long getSize() {
        return size;
    }



    private String formatFileTime(FileTime fileTime) {
        Instant instant = fileTime.toInstant();
        DateTimeFormatter formatter = DateTimeFormatter
                .ofPattern("dd.MM.yyyy HH:mm")
                .withZone(ZoneId.systemDefault());
        return formatter.format(instant);
    }

}
