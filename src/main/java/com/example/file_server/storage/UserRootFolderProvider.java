package com.example.file_server.storage;

import java.nio.file.Path;

public interface UserRootFolderProvider {
    Path getCurrentUserRootFolder();
}
