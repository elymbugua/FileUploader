package com.fileuploader;

public class FileUploaderConfig {
    private String GcpStorageProjectId,GcpStorageBucket,CredentialsPath,UploadsPath;

    public String getGcpStorageProjectId() {
        return GcpStorageProjectId;
    }

    public void setGcpStorageProjectId(String gcpStorageProjectId) {
        GcpStorageProjectId = gcpStorageProjectId;
    }

    public String getGcpStorageBucket() {
        return GcpStorageBucket;
    }

    public void setGcpStorageBucket(String gcpStorageBucket) {
        GcpStorageBucket = gcpStorageBucket;
    }

    public String getCredentialsPath() {
        return CredentialsPath;
    }

    public void setCredentialsPath(String credentialsPath) {
        CredentialsPath = credentialsPath;
    }

    public String getUploadsPath() {
        return UploadsPath;
    }

    public void setUploadsPath(String uploadsPath) {
        UploadsPath = uploadsPath;
    }
}
