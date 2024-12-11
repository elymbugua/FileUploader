package com.fileuploader;

import com.google.api.gax.paging.Page;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.*;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.blob.ListBlobItem;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import java.util.logging.Level;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

public class FileUploader {
	
	private boolean isMultiPart;
	static String basePath, tempFolder;
	String uploadLocation;
	static int maxFileSize, maxUploadMemSize;
	private File file;
	private List fileItems;
	List<String> files = new ArrayList<>();
	
	private Map<String, String> parameters =
			new HashMap<>();
	
	private boolean uploadToInstantUploads = false;
	
	public String fileError = null;
	
	static CloudBlobContainer blobContainer = null, databaseBackupBlobContainer = null;
	private final static Object uploadObject = new Object();
	private boolean renameFiles;
	static FileUploaderConfig config= Utility.getFileUploaderConfig();
	
	public FileUploader() {
		this(false);
	}
	
	public FileUploader(boolean renameFiles) {
		this.renameFiles = renameFiles;
		uploadLocation = config.getUploadsPath();
		basePath = uploadLocation;
	}
	
	public List<String> getFiles() {
		return files;
	}
	
	public void setUploadLocation(String uploadLocation) {
		this.uploadLocation = uploadLocation;
		File uploadPath = new File(uploadLocation);
		if (!uploadPath.exists()) {
			try {
				uploadPath.mkdirs();
			} catch (SecurityException e) {
				Utility.logStackTrace(e);
			}
		}
		basePath = uploadLocation;
	}
	
	public static void uploadFilesToAzure() {
		
		synchronized (uploadObject) {
			String[] filesToUpload = getFilesInDirectory(config.getUploadsPath());
			
			if (filesToUpload != null) {
				for (String file : filesToUpload) {
					uploadFileToAzureHelper(config.getUploadsPath() + file, getBlobContainer());
				}
			}
			
			//Upload database backups
			String[] dbBackups = getFilesInDirectory(config.getDatabaseBackupsPath());
			
			if (dbBackups != null) {
				for (String backup : dbBackups) {
					Utility.log("Uploading backup "+ backup, Level.INFO);
					uploadFileToAzureHelper(config.getDatabaseBackupsPath() + backup,
							getDatabaseBackupBlobContainer());
				}
			} else {
				Utility.log("No database backups found", Level.INFO);
			}
		}
	}
	
	public static void uploadFileToAzureHelper(String filePath, CloudBlobContainer container) {
		boolean uploaded = false;
		
		String pathSeparator = config.getRuntime().equals("linux") ? "/" : "\\";
		
		String fileName = filePath.substring(filePath.lastIndexOf(pathSeparator) + 1);
		
		if (!fileExistsOnAzure(fileName)) {
			Utility.log("uploading file to azure storage: " + fileName, Level.INFO);
			uploaded = uploadFileToAzure(filePath, container);
		} else {
			Utility.log("file exists on azure storage: " + fileName, Level.INFO);
			uploaded = true;
		}
		
	}
	
	public static boolean deleteFile(String filePath){
		try {
			Utility.log("Deleting: " + filePath, Level.INFO);
			Files.delete(Paths.get(filePath));
			return true;
		} catch (IOException ex) {
			Utility.log("Could not delete file ->"+ filePath,Level.INFO);
			Utility.logStackTrace(ex);
			return false;
		}
	}
	
	public static CloudBlobContainer getBlobContainer() {
		
		if (blobContainer != null)
			return blobContainer;
		
		var config = Utility.getFileUploaderConfig();
		
		try {
			// Retrieve storage account from connection-string.
			CloudStorageAccount cloudStorageAccount =
					CloudStorageAccount.parse(config.getAzureConnectionString());
			
			// Create the blob client.
			CloudBlobClient cloudBlobClient = cloudStorageAccount.createCloudBlobClient();
			
			// Retrieve reference to a previously created container.
			blobContainer = cloudBlobClient.
					getContainerReference(config.getBlobStorageContainer());
		} catch (Exception ex) {
			Utility.logStackTrace(ex);
		}
		
		return blobContainer;
	}
	
	public static void deleteOldDbBackups(){
		if(config.isDeleteOldDbBackups()){
			CloudBlobContainer dbbackupsContainer= getDatabaseBackupBlobContainer();
			
			for(ListBlobItem backupFileName: dbbackupsContainer.listBlobs()){
				String fileName=backupFileName.getUri().getPath();
				Utility.log(fileName, Level.INFO);
				String currentBackupDate=fileName.substring(20,28);
				Utility.log(currentBackupDate, Level.INFO);
				
				Date currentate=Formatter.getDateFromIsoString(Formatter.
						formatIsoDateWithSeparators(currentBackupDate));
				
				Date nextDate=Utility.addDaysToDate(currentate, 1);
			}
		}
	}
	
	public static CloudBlobContainer getDatabaseBackupBlobContainer() {
		if (databaseBackupBlobContainer != null)
			return databaseBackupBlobContainer;

		try {
			// Retrieve storage account from connection-string.
			CloudStorageAccount cloudStorageAccount =
					CloudStorageAccount.parse(config.getAzureConnectionString());
			
			// Create the blob client.
			CloudBlobClient cloudBlobClient = cloudStorageAccount.createCloudBlobClient();
			
			// Retrieve reference to a previously created container.
			databaseBackupBlobContainer = cloudBlobClient.
					getContainerReference(config.getDatabaseBackupContainer());
		} catch (Exception ex) {
			Utility.logStackTrace(ex);
		}
		
		return databaseBackupBlobContainer;
	}
	
	public static void uploadFileToAzure(File file, CloudBlobContainer container) {
		
		try {
			// Create or overwrite the filename blob with contents from a local file.
			String fileName = file.getName();
			
			if (!fileName.isEmpty()) {
				CloudBlockBlob cloudBlob = container.getBlockBlobReference(fileName);
				cloudBlob.uploadFromFile(file.getPath());
			}
		} catch (Exception ex) {
			Utility.logStackTrace(ex);
		}
	}
	
	public static boolean uploadFileToAzure(String filePath, CloudBlobContainer container) {
		
		try {
			if (filePath != null && !filePath.isEmpty()) {
				
				String pathSeparator = config.getRuntime().equals("linux") ? "/" : "\\";
				
				String fileName = filePath.substring(filePath.lastIndexOf(pathSeparator) + 1);
				CloudBlockBlob cloudBlob = container.getBlockBlobReference(fileName);
				File file = new File(filePath);
				if (file.exists()) {
					if (file.isDirectory()) {
						if (file.list() != null) {
							try {
								for (String fileInDir : file.list()) {
									return uploadFileToAzure(fileInDir, container);
								}
							} catch (Exception e) {
								Utility.logStackTrace(e);
								return false;
							}
						}
					} else {
						cloudBlob.uploadFromFile(filePath);
					}
				}
				Utility.log("Uploaded " + fileName + " to azure storage", Level.INFO);
			}
			
			return true;
		} catch (Exception ex) {
			Utility.logStackTrace(ex);
			return false;
		}
	}
	
	public static String[] getFilesInDirectory(String directoryPath) {
		
		try {
			File directory = new File(directoryPath);
			String[] fileNames = directory.list();
			return fileNames;
		} catch (Exception ex) {
			Utility.logStackTrace(ex);
			return new String[0];
		}
	}
	
	public static boolean fileExistsOnAzure(String fileName) {
		
		try {
			return getBlobContainer().getBlockBlobReference(fileName).exists();
		} catch (Exception ex) {
			Utility.logStackTrace(ex);
			return false;
		}
	}
	
	public static void downloadFile(String url, String filePath) {
		
		if (url == null || url.trim().isEmpty()) {
			throw new IllegalArgumentException("Url cannot be null or empty");
		}
		
		if (filePath == null || filePath.trim().isEmpty()) {
			throw new IllegalArgumentException("fileName cannot be null or empty");
		}
		
		String destinationUrl = filePath;
		File destination = new File(destinationUrl);
		
		try {
			FileUtils.copyURLToFile(new URL(url), destination);
		} catch (Exception ex) {
			Utility.logStackTrace(ex);
		}
	}
	
	public static boolean fileExists(String path) {
		
		try {
			return Files.exists(Paths.get(path), LinkOption.NOFOLLOW_LINKS);
		} catch (Exception ex) {
			Utility.logStackTrace(ex);
			return false;
		}
	}
	
	//Remove older database backups from azure storage
	public void purgeOlderBackups(){
		CloudBlobContainer dbBackupsblobContainer= getDatabaseBackupBlobContainer();
		int blobCount=0;
		
		for(ListBlobItem blobItem: dbBackupsblobContainer.listBlobs()){
			URI uri= blobItem.getUri();
			String url= uri.toString();
			String blobName= url.substring(url.lastIndexOf("/")+1);
			String blobDate= blobName.substring(8,16);
			
			DateFormat dateFormat= new SimpleDateFormat("yyyyMMdd");
			
			try{
				Date backUpDate= dateFormat.parse(blobDate);
				int daysBetweeenDates= Utility.getDaysBetweenDates(backUpDate, new Date());
				
				Utility.log("Backup is "+ daysBetweeenDates+ " days old", Level.INFO);
				
				//Delete backups older than 10 days
				if(daysBetweeenDates>10){
					CloudBlockBlob blockBlob= dbBackupsblobContainer.getBlockBlobReference(blobName);
					if(blockBlob!=null){
						blockBlob.delete();
						Utility.log("Deleted old backup -> "+ blobName, Level.SEVERE);
						blobCount++;
						if(blobCount % 100==0)
							return;
					}
				}
			}
			catch(Exception ex){
				Utility.logStackTrace(ex);
			}
		}
	}

	public static void uploadFilesToGoogleCloudStorage(){
		try{
			FileUploaderConfig uploaderConfig= Utility.getFileUploaderConfig();
			String[] files= getFilesInDirectory(uploaderConfig.getUploadsPath());

			if(files.length>0){
				//delete older backups
				deleteOlderBackupsInGCPCloudStorage(uploaderConfig.getGcpStorageBucket());
			}

			for(String file: files){
				uploadFileToGoogleStorage(uploaderConfig.getUploadsPath()+file);
			}
		}
		catch (Exception ex){
			Utility.logStackTrace(ex);
		}
	}

	public synchronized static GoogleCredentials getCredentials()throws IOException{
		FileUploaderConfig fileUploaderConfig= Utility.getFileUploaderConfig();
		return GoogleCredentials.fromStream(new FileInputStream(fileUploaderConfig.getCredentialsPath()));
	}

	public synchronized static void uploadFileToGoogleStorage(String filePath)
		throws IOException{
		FileUploaderConfig fileUploaderConfig= Utility.getFileUploaderConfig();

		String filename=filePath.substring(filePath.lastIndexOf("\\")+1);

		if(DAL.fileExists(filename)) return;

		Utility.log("Uploading file: "+ filePath+ ", to google storage", Level.INFO);

		Storage googleStorage= StorageOptions.newBuilder().setCredentials(getCredentials()).
				setProjectId(fileUploaderConfig.getGcpStorageProjectId()).build().getService();

		BlobId blobId= BlobId.of(fileUploaderConfig.getGcpStorageBucket(),filename);
		BlobInfo blobInfo= BlobInfo.newBuilder(blobId).build();

		try{
			googleStorage.create(blobInfo, Files.readAllBytes(Paths.get(filePath)));
			DAL.addFile(filename);
			deleteFile(filePath);
			Utility.log("Uploaded file: "+ filePath+ ", to google storage", Level.INFO);
		}
		catch (Exception ex){
			Utility.logStackTrace(ex);
		}
	}

	public static void downloadAzureBlobs(){

		CloudBlobContainer blobsContainer= getBlobContainer();

		try{

			for(ListBlobItem blobItem: blobsContainer.listBlobs()){
				String path=blobItem.getUri().getPath();
				String filename=path.substring(path.lastIndexOf("/")+1);

				if(!DAL.fileExists(filename) && !Files.exists(Paths.get(config.getUploadsPath()+filename))){
					Utility.log("URI: "+ blobItem.getUri().toString(), Level.INFO);
					downloadFile(blobItem.getUri().toString(),config.getUploadsPath() + filename);
				}
				else{
					Utility.log(filename+ " exists, skipping", Level.INFO);
				}
			}
		}
		catch (Exception ex){

		}
	}

	public static void deleteOlderBackupsInGCPCloudStorage(String bucket)throws IOException{
		Storage googleStorage= StorageOptions.newBuilder().setCredentials(getCredentials()).
				setProjectId(Utility.getFileUploaderConfig().getGcpStorageProjectId()).build().getService();
		Page<Blob> blobs=googleStorage.list(bucket);
		List<Blob> backupBlobs= new ArrayList<>(1000);
		for(Blob blob:blobs.iterateAll()){
			String name= blob.getName();
			if(name.endsWith(".bak")){
				backupBlobs.add(blob);
			}
		}

		Map<String,List<Blob>> blobGroupings= backupBlobs.stream().collect(Collectors.groupingBy(b->b.getName().
				substring(0,b.getName().indexOf("_"))));

		blobGroupings.forEach((key,blobGroup)->{
			blobGroup.sort(Comparator.comparing(b->b.getCreateTime()));
			for(int i=0;i<blobGroup.size()-3;i++){
				Blob blob=blobGroup.get(i);
				Utility.log("Deleting older backup created on: "+
						Formatter.formatDateTime(new Date(blob.getCreateTime())),Level.INFO);
				blob.delete();
			}

		});
	}

}
