package com.example.file_server.storage.impl;

import com.example.file_server.dto.FileInfoDto;
import com.example.file_server.storage.FileStorage;
import com.example.file_server.storage.UserRootFolderProvider;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


@Component
@Profile("local")
public class LocalFileStorage implements FileStorage {
    private final UserRootFolderProvider rootFolderProvider;


    public LocalFileStorage(UserRootFolderProvider rootFolderProvider) {
        this.rootFolderProvider = rootFolderProvider;
    }

    private Path resolve(String relativePath) {
        Path userRoot = rootFolderProvider.getCurrentUserRootFolder();
        Path resolved = userRoot.resolve(relativePath).normalize();
        if(!resolved.startsWith(userRoot)) {
            throw new SecurityException("Path traversal detected: " + relativePath);
        }
        return resolved;
    }


    @Override
    public List<FileInfoDto> list(String relativePath) throws IOException {
        Path dir = resolve(relativePath);

        if (!Files.exists(dir)) {
            throw new IOException("Ditectory doesn't exist " + relativePath);
        }
        if(!Files.isDirectory(dir)) {
            throw new IOException("It's not a directory: " + relativePath);
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .map(path -> {
                        boolean isDir = Files.isDirectory(path);
                        long size = 0;
                        String lastModified = null;
                        FileTime fileTime = null;


                        try {
                            size = Files.size(path);
                            fileTime = Files.getLastModifiedTime(path);

                        } catch (IOException e) {

                        }

                        return new FileInfoDto(
                                path.getFileName().toString(),
                                isDir,
                                size,
                                fileTime
                        );
                    })

                    .toList();
        }
    }
    @Override
    public List<FileInfoDto> searchRecursive(String relativePath, String query) throws IOException {
        Path startDir = resolve(relativePath);

        if (!Files.exists(startDir)) {
            throw new IOException("Directory not found: " + relativePath);
        }

        List<FileInfoDto> results = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(startDir)) {
            walk.filter(path -> {

                        if (path.equals(startDir)) return false;

                        String name = path.getFileName().toString().toLowerCase();
                        return name.contains(query.toLowerCase());
                    })
                    .forEach(path -> {
                        try {
                            String relPath = startDir.relativize(path).toString();
                            boolean isDirectory = Files.isDirectory(path);

                            results.add(new FileInfoDto(
                                    relPath,
                                    isDirectory,
                                    isDirectory ? 0 : Files.size(path),
                                    Files.getLastModifiedTime(path)
                            ));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }

        return results;
    }



    @Override
    public void save(String relPath, String fileName, MultipartFile file) throws IOException  {


        if(fileName == null  || fileName.isEmpty()) {
            throw new IllegalArgumentException("Empty file name");
        }

        String safeFileName = Paths.get(fileName).getFileName().toString(); //очищаем от пути
        Path target = resolve(relPath).resolve(safeFileName); //формируем полный путь: корень + папка + имя файла
        Files.createDirectories(target.getParent()); //создаем директории если таких нет
        try(InputStream in = file.getInputStream()) { //сохраняем файл потоково (не загружая весь в RAM)
            Files.copy(in,target, StandardCopyOption.REPLACE_EXISTING);
            Files.setLastModifiedTime(target, FileTime.fromMillis(System.currentTimeMillis()));
        }
    }

    public void saveFolder(String relPath, MultipartFile zipFile) throws IOException {
        if (zipFile == null || zipFile.isEmpty()) {
            throw new IllegalArgumentException("Empty zip file");
        }


        try (InputStream is = zipFile.getInputStream()) {
            extractZipToPath(is, resolve(relPath));
        }

    }



    private void extractZipToPath(InputStream zipInputStream, Path targetDir) throws IOException {
        try (ZipArchiveInputStream zis = new ZipArchiveInputStream(zipInputStream, "UTF-8")) {
            ZipArchiveEntry entry;

            while ((entry = zis.getNextZipEntry()) != null) {
                String entryName = entry.getName();
                Path entryPath = targetDir.resolve(entryName);

                if (entry.isDirectory()) {

                    Files.createDirectories(entryPath);
                } else {

                    Files.createDirectories(entryPath.getParent());


                    try (OutputStream out = Files.newOutputStream(entryPath)) {
                        IOUtils.copy(zis, out);
                    }


//                    if (entry.getLastModifiedDate() != null) {
//                        entryPath.toFile().setLastModified(entry.getLastModifiedDate().getTime());
//                    }
                    Files.setLastModifiedTime(entryPath, FileTime.fromMillis(System.currentTimeMillis()));
                }
            }
        }
    }

    @Override
    public void delete(String path) throws IOException {
        Path dir = resolve(path);
        if (!Files.exists(dir)) {
            throw new IOException("dir doesnt exist");
        }
        if(Files.isRegularFile(dir)) {
            Files.delete(dir);
        }
        else if(Files.isDirectory(dir)) {
            try(Stream<Path> walk = Files.walk(dir)) { //walk проходит по всему дереву директорий и файлов
                walk.sorted(Comparator.reverseOrder()).forEach(path1 -> { //сначала самые глубокие файлы потом сами папки
                    try {
                        Files.delete(path1);
                    } catch (IOException e) {throw new RuntimeException("Failed to delete: " + path1, e);}
                });
            }
        }
    }

    @Override
    public void move(String sourcePath, String targetPath) throws IOException {
        Path source = resolve(sourcePath);
        Path target = resolve(targetPath);

        if (!Files.exists(source)) {
            throw new IOException("source not found: " + source);
        }

        if (Files.exists(target) && !source.equals(target)) {
            throw new IOException("already exists: " + targetPath);
        }

        Files.createDirectories(target.getParent());
        Files.move(source, target);
        Files.setLastModifiedTime(target, FileTime.fromMillis(System.currentTimeMillis()));
    }

    @Override
    public void copy(String sourceRel, String targetRel) throws IOException {
        Path source = resolve(sourceRel);
        Path target = resolve(targetRel);

        if (!Files.exists(source)) {
            throw new IOException("Source not found: " + sourceRel);
        }

        int copyNumber = 1;
        Path finalTarget = target;

        while (Files.exists(finalTarget)) {
            String parent = target.getParent().toString();
            String name = target.getFileName().toString();


            int dotIndex = name.lastIndexOf('.');
            if (dotIndex > 0) {
                name = name.substring(0, dotIndex) + "_copy" + copyNumber + name.substring(dotIndex);
            } else {
                name = name + "_copy" + copyNumber;
            }

            finalTarget = Paths.get(parent).resolve(name);
            copyNumber++;
        }

        Files.createDirectories(finalTarget.getParent());

        if (Files.isDirectory(source)) {
            copyDirectory(source, finalTarget);
        } else {
            Files.copy(source, finalTarget, StandardCopyOption.REPLACE_EXISTING);
            Files.setLastModifiedTime(finalTarget, FileTime.fromMillis(System.currentTimeMillis()));
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(sourcePath -> {
            try {
                Path targetPath = target.resolve(source.relativize(sourcePath));
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    public void createFolder(String relativePath) throws IOException {
        Path target = resolve(relativePath);
        if (Files.exists(target)) {
            throw new IOException("Path already exists: " + relativePath);
        }
        Files.createDirectories(target);
    }

//    @Override
//    public Path createTempZip(String relativePath) throws IOException {
//        Path folder = getFolder(relativePath);
//
//        //2.Создаём временный файл (в системной temp-папке)
//        Path tempZip = Files.createTempFile("folder_", ".zip");
//
//        // 3. Заполняем ZIP
//        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempZip))) {
//            Files.walk(folder)
//                    .filter(file -> !Files.isDirectory(file))
//                    .forEach(file -> {
//                        try {
//                            Path rel = folder.relativize(file);
//                            zos.putNextEntry(new ZipEntry(rel.toString().replace("\\", "/")));
//                            Files.copy(file, zos);
//                            zos.closeEntry();
//                        } catch (IOException e) {
//                            throw new UncheckedIOException(e);
//                        }
//                    });
//        }
//
//
//        return tempZip;
//    }

    @Override
    public Path getFile(String relativePath) throws IOException {
        Path resolved = resolve(relativePath);
        if (!Files.exists(resolved)) {
            throw new IOException("File not found: " + relativePath);
        }

        if (Files.isDirectory(resolved)) {
            throw new IOException("Cannot download a directory, use download-folder endpoint: " + relativePath);
        }

        return resolved;
    }

    @Override
    public Path getFolder(String relativePath) throws IOException {
        Path resolved = resolve(relativePath);
        if (!Files.exists(resolved)) {
            throw new IOException("Folder not found: " + relativePath);
        }

        if (!Files.isDirectory(resolved)) {
            throw new IOException("Path is not a directory: " + relativePath);
        }

        return resolved;
    }

    @Override
    public void zipFolderToStream(String relativePath, OutputStream outputStream) throws IOException {
        Path folder = getFolder(relativePath);


        try (BufferedOutputStream bufferedOut = new BufferedOutputStream(outputStream);
             ZipArchiveOutputStream zipOut = new ZipArchiveOutputStream(bufferedOut)) {


            zipOut.setLevel(Deflater.DEFAULT_COMPRESSION); //Уровень сжатия
            zipOut.setMethod(ZipArchiveOutputStream.DEFLATED); //Метод сжатия
            zipOut.setEncoding("UTF-8"); //Поддержка русских имен файлов

            Files.walk(folder)
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        try {
                            // Получаем относительный путь от корня папки
                            Path relativePathFromFolder = folder.relativize(file);


                            String entryName = relativePathFromFolder.toString().replace("\\", "/");
                            ZipArchiveEntry zipEntry = new ZipArchiveEntry(entryName);


                            zipEntry.setSize(Files.size(file));
                            zipEntry.setLastModifiedTime(Files.getLastModifiedTime(file));


                            zipOut.putArchiveEntry(zipEntry);


                            Files.copy(file, zipOut);

                            // Завершаем запись
                            zipOut.closeArchiveEntry();

                        } catch (IOException e) {
                            throw new UncheckedIOException("Failed to add file to zip: " + file, e);
                        }
                    });

            // Завершаем архив
            zipOut.finish();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}


