package com.example.file_server.dto;

public class MoveRequest {
    private String sourcePath;
    private String targetPath;

    public MoveRequest() {}

    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }
    public String getTargetPath() { return targetPath; }
    public void setTargetPath(String targetPath) { this.targetPath = targetPath; }
}
