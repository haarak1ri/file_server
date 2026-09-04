package com.example.file_server.service;


import com.example.file_server.dto.FileInfoDto;
import com.example.file_server.storage.FileStorage;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


@Service

public class FileService {
    private final FileStorage fileStorage;


    public FileService(FileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    public List<FileInfoDto> listDirectory(String relativePath) throws IOException {
        return fileStorage.list(relativePath);
    }

    public void save(String relPath, MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        fileStorage.save(relPath, fileName, file);
    }

    public void delete(String path) throws IOException {
        fileStorage.delete(path);
    }

    public void move(String sourcePath, String targetPath) throws IOException {
        fileStorage.move(sourcePath, targetPath);
    }

    public void copy(String sourceRel, String targetRel) throws IOException {
        fileStorage.copy(sourceRel, targetRel);
    }

    public void createFolder(String relativePath) throws IOException {
        fileStorage.createFolder(relativePath);
    }

    public void streamFolderAsZip(String relativePath, OutputStream outputStream) throws IOException {
        fileStorage.zipFolderToStream(relativePath, outputStream);
    }

    public Path getFileForDownload(String relativePath) throws IOException {
        return fileStorage.getFile(relativePath);
    }

    public Path getFolderForDownload(String relativePath) throws IOException {
        return fileStorage.getFolder(relativePath);
    }

    public void saveFolder(String targetPath, MultipartFile file) throws IOException {
        fileStorage.saveFolder(targetPath, file);
    }

    public List<FileInfoDto> searchRecursive(String path, String filter) throws IOException {
        return fileStorage.searchRecursive(path, filter);
    }
}
