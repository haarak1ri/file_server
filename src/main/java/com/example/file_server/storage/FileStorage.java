package com.example.file_server.storage;

import com.example.file_server.dto.FileInfoDto;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;

public interface FileStorage {
     List<FileInfoDto> list(String relativePath) throws IOException;
     void save(String relPath,String filename, MultipartFile file) throws IOException;
     void delete(String path) throws IOException;
     void move(String sourcePath, String targetPath) throws IOException;
     void copy(String sourceRel, String targetRel) throws IOException;
     void createFolder(String relativePath) throws IOException;
//     Path createTempZip(String relativePath) throws IOException;
     Path getFile(String relativePath) throws IOException;
     Path getFolder(String relativePath) throws IOException;
     void zipFolderToStream(String relativePath, OutputStream outputStream) throws IOException;
     void saveFolder(String targetPath, MultipartFile file) throws IOException;
     public List<FileInfoDto> searchRecursive(String relativePath, String query) throws IOException;
}
