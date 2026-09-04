package com.example.file_server.contoller;

import com.example.file_server.dto.FileInfoDto;
import com.example.file_server.dto.MoveRequest;
import com.example.file_server.dto.UserResponseDto;
import com.example.file_server.service.FileService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.imageio.IIOException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/files")
public class FileContoller {
    private final FileService fileService;

    public FileContoller(FileService fileService) {
        this.fileService = fileService;
    }


    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<?> handleSecurityException(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }
    private String extractPath(HttpServletRequest request, String prefix) {
        String uri = request.getRequestURI();
        String rawPath = uri.startsWith(prefix) ? uri.substring(prefix.length()) : "";

        try {
            return URLDecoder.decode(rawPath, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return rawPath;
        }
    }

    @GetMapping() //получить список загруженных файлов
    public ResponseEntity<List<FileInfoDto>> list(@RequestParam(defaultValue = "") String path,
                                                  @RequestParam(required = false) String filter,
                                                  @RequestParam(defaultValue = "false") boolean recursive) throws IOException {
        List<FileInfoDto> files;

        if (recursive && filter != null && !filter.isEmpty()) {

            files = fileService.searchRecursive(path, filter);
        } else {

            files = fileService.listDirectory(path);

            // Применяем фильтр если нужно
            if (filter != null && !filter.isEmpty()) {
                files = files.stream()
                        .filter(file -> !file.isDirectory())
                        .filter(file -> file.getName().toLowerCase().contains(filter.toLowerCase()))
                        .toList();
            }
        }

        return ResponseEntity.ok(files);
    }

    @PostMapping()
    public ResponseEntity<Map<String, String>> upload(@RequestParam String targetPath, @RequestParam(defaultValue = "false") boolean extract,
                                                      @RequestPart("file") MultipartFile file) throws IOException {
        //ReqPart для файлов и сложных не текстовых объектов
        //MultipartFile — это интерфейс Spring, который представляет загруженный файл.
        //spring определяет файл, ссохраняет во временное хранилище на системе
        //после копирования в сервисе удалит, multipart реализует поток передачи байтов

        if (extract) {
            fileService.saveFolder(targetPath,file);
        } else {
            fileService.save(targetPath, file);
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Uploaded: " + file.getOriginalFilename()));

    }
    @GetMapping("/**") //Response entity позволяет контролировать возвращаемый объектов, статус 404,200, заголовки и т.д
    public ResponseEntity<Resource> download(HttpServletRequest request) throws IOException {

        try {
            String fullpath = extractPath(request, "/api/files/");
            Path filePath = fileService.getFileForDownload(fullpath);

            String contentType = Files.probeContentType(filePath); // опрежеляем MIME тип, пример .txt = text/plain
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            Resource resource = new FileSystemResource(filePath.toFile());

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filePath.getFileName().toString() + "\"")
                    .body(resource);
            //contentType(MediaType.parseMediaType(contentType)) - чтобы брайзер, сurl понял что за файл пришел и как его обработать
            //attachment - нужно скачать а не открывать; filename="report.pdf" - предлагаемое имя сохранения
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }
    @DeleteMapping("/**")
    public ResponseEntity<Void> delete(HttpServletRequest request) throws IOException {
        String path = extractPath(request, "/api/files/");
        fileService.delete(path);
        return ResponseEntity.noContent().build();  // 204
    }

    @PostMapping("/move")
    public ResponseEntity<?> move(@RequestBody MoveRequest request) throws IOException {
        try {
            fileService.move(request.getSourcePath(), request.getTargetPath());
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                return ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Name already exists: " + request.getTargetPath()));
            }
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
    @PostMapping("/copy")
    public ResponseEntity<Void> copy(@RequestBody MoveRequest request) throws IOException {
        fileService.copy(request.getSourcePath(), request.getTargetPath());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/folders")
    public ResponseEntity<?> createFolder(@RequestParam String path) throws IOException {
        try {
            fileService.createFolder(path);
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                return ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Folder already exists: " + path));
            }
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/folders/**")
    public ResponseEntity<StreamingResponseBody> downloadFolder(HttpServletRequest request) {
        String path = extractPath(request, "/api/files/folders/");


        try {
            fileService.getFolderForDownload(path);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }

        String folderName = Path.of(path).getFileName().toString();

        StreamingResponseBody body = outputStream -> {
            try {
                fileService.streamFolderAsZip(path, outputStream);
            } catch (IOException e) {
                // Логирование ошибки
                throw new RuntimeException("Failed to stream folder: " + path, e);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + folderName + ".zip\"")
                .body(body);
    }


}