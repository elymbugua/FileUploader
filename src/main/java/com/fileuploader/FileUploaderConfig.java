package com.fileuploader;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FileUploaderConfig {
    private String GcpStorageProjectId,GcpStorageBucket,CredentialsPath,UploadsPath,
    AzureConnectionString,TempDir,BlobStorageContainer,DatabaseBackupContainer,Runtime,
            UploaderDbPath,DatabaseBackupsPath;
    private boolean DeleteOldDbBackups;
}
