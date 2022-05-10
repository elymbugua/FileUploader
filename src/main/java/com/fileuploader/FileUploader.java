package com.fileuploader;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
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
	EfisalesResource resources;
	
	
	public String fileError = null;
	
	public static String azureStorageConnectionString = "DefaultEndpointsProtocol=http;AccountName=efisalesdiststorage;" +
			"AccountKey=k6O98+bZ7rhEEQJsyturuemrHe8CnZvHXvnDO2k9pEDfnuHPY3llW+BADq5OWE8JWqSd2sKBeIpRZ4JDd+SdQA==";
	
	static CloudBlobContainer blobContainer = null, databaseBackupBlobContainer = null;
	private final static Object uploadObject = new Object();
	private boolean renameFiles;
	
	public FileUploader() {
		this(false);
	}
	
	public FileUploader(boolean renameFiles) {
		this.renameFiles = renameFiles;
		resources = Utility.getResources();
		uploadLocation = resources.getUploadsPath();
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
	
	
	public String uploadFiles(HttpServletRequest httpRequest) {
		//maximum file size that can be uploaded
		//maxFileSize =
				//Integer.parseInt(httpRequest.getServletContext().getInitParameter("uploadsize"));
		
		//maximum file in memory while processing
		//maxUploadMemSize =
				//Integer.parseInt(httpRequest.getServletContext().getInitParameter("maxuploadmemsize"));
		
		//where file chunks are stored while processing
		tempFolder = resources.getTempDir();//httpRequest.getServletContext().getInitParameter("tempfolder");
		
		//request should be multipart/form-data
		isMultiPart = ServletFileUpload.isMultipartContent(httpRequest);
		
		
		if (!isMultiPart) {
			return "request not multipart";
		}
		
		files.clear();
		
		DiskFileItemFactory factory = new DiskFileItemFactory();
		factory.setSizeThreshold(maxUploadMemSize);
		
		factory.setRepository(new File(tempFolder));
		
		ServletFileUpload fileUploadHandler =
				new ServletFileUpload(factory);
		
		fileUploadHandler.setSizeMax(maxFileSize);
		
		try {
			
			//parse the request to get file items
			List items = fileUploadHandler.parseRequest(httpRequest);
			
			if (items.isEmpty()) {
				items = fileItems;
			}
			
			Iterator i = items.iterator();
			
			getFiles().clear();
			
			while (i.hasNext()) {
				FileItem fi = (FileItem) i.next();
				
				if (!fi.isFormField()) {
					
					//Get the uploaded file parameters
					//String fieldName= fi.getFieldName();
					String fileName = fi.getName();
					//String contentType=fi.getContentType();
					//boolean isInMemory= fi.isInMemory();
					//long sizeInBytes=fi.getSize();
					
					String pathSeparator = resources.getRuntime().equals("linux") ? "/" : "\\";
					
					int separators = fileName.lastIndexOf(pathSeparator);
					
					if (separators >= 0) {
						fileName = fileName.substring(fileName.lastIndexOf(pathSeparator) + 1);
					}
					String fileExtension = FilenameUtils.getExtension(fileName);
					
					if (renameFiles) {
						fileName = new Random().nextInt(5) + "_" + System.currentTimeMillis() + "." + fileExtension;
						
					}
                    /*else{
                        fileName=fileName.substring(fileName.lastIndexOf(pathSeparator)+1);
                    }*/
					try {
						File baseDir = new File(basePath);
						if (!baseDir.exists()) {
							baseDir.mkdir();
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
					file = new File(basePath + fileName);
					
					if (fi.getSize() > 0) {
						fi.write(file);
						files.add(fileName);
					}
				} else {
					parameters.put(fi.getFieldName(),
							fi.getString());
				}
			}
		} catch (Exception ex) {
			Utility.logStackTrace(ex);
			return "Could not upload try again";
		}
		
		return "uploaded";
	}
	
	public synchronized String getParameterValueFromMultipartRequest(
			String parameter) {
		
		for (Map.Entry<String, String> entry : parameters.entrySet()) {
			if (parameter.equals(entry.getKey())) {
				return entry.getValue();
			}
		}
		
		return null;
		
	}
	
	public synchronized void UploadImages(boolean resize, HttpServletRequest request,
	                                      int maxDimension)
			throws FileUploadException, IOException {
		
		//Only logos cannot be resized but must be 170 x 50 px
		
		//directory where files go
		EfisalesResource resources = Utility.getResources();
		basePath = resources.getUploadsPath();
		isMultiPart = ServletFileUpload.isMultipartContent(request);
		
		if (!isMultiPart) {
			fileError = "No images uploaded";
			return;
		}
		
		//maximum file size that can be uploaded
		//maxFileSize =
				//Integer.parseInt(request.getServletContext().getInitParameter("uploadsize"));
		
		//maximum file in memory while processing
		//maxUploadMemSize =
				//Integer.parseInt(request.getServletContext().getInitParameter("maxuploadmemsize"));
		
		//where file chunks are stored while processing
		tempFolder = resources.getTempDir();
		
		DiskFileItemFactory factory = new DiskFileItemFactory();
		factory.setSizeThreshold(maxUploadMemSize);
		
		factory.setRepository(new File(tempFolder));
		
		ServletFileUpload fileUpload =
				new ServletFileUpload(factory);
		
		
		fileUpload.setSizeMax(maxFileSize);
		
		List<FileItem> items = fileUpload.parseRequest(request);
		Iterator<FileItem> itemsIterator = items.iterator();
		
		//clear any earlier files
		files.clear();
		
		while (itemsIterator.hasNext()) {
			
			FileItem item = itemsIterator.next();
			
			String name = item.getFieldName();
			
			InputStream stream = item.getInputStream();
			
			if (!item.isFormField()) {
				
				String extension = item.getName().substring(item.getName().lastIndexOf(".") + 1);
				
				if (extension != null && !extension.trim().isEmpty()) {
					extension = extension.toLowerCase();
					
					String fileName = item.getName().substring(0, item.getName().lastIndexOf(".") - 1).toLowerCase();
					
					fileName = fileName.replace(" ", "-");
					
					fileName += "_" + Calendar.getInstance().getTimeInMillis() + "." + extension;
					
					if (extension.equals("png") || extension.equals("jpg") ||
							extension.equals("gif")) {
						if (!resize) {
							//this is a logo upload. Check the size
							
							BufferedImage img = ImageIO.read(stream);
							
							if (img == null) {
								fileError = "Invalid Image Try again";
								return;
							}
							
							if (img.getWidth() > 170 || img.getHeight() > 50) {
								fileError = "Logos should be 170px by 50px or smaller.";
								return;
							}
							
							ImageIO.write(img, extension, new File(basePath + fileName));
							files.add(fileName);
						}
					} else {
						fileError = "Invalid image type. Only PNG,JPG and GIF allowed.";
					}
				} else {
					fileError = "Invalid image type. Only PNG,JPG and GIF allowed.";
				}
			} else {
				parameters.put(item.getFieldName(), item.getString());
			}
		}
		
	}
	
	public static String getBasePath(HttpServletRequest request) {
		//directory where files go
		EfisalesResource resources = Utility.getResources();
		return resources.getUploadsPath();
	}
	
	public static void uploadFilesToAzure() {
		
		synchronized (uploadObject) {
			EfisalesResource efisalesResources = Utility.getResources();
			
			String[] filesToUpload = getFilesInDirectory(efisalesResources.getUploadsPath());
			
			if (filesToUpload != null) {
				for (String file : filesToUpload) {
					uploadFileToAzureHelper(efisalesResources.getUploadsPath() + file, getBlobContainer());
				}
			}
			
			//Upload database backups
			String[] dbBackups = getFilesInDirectory(efisalesResources.getDatabaseBackupsPath());
			
			if (dbBackups != null) {
				for (String backup : dbBackups) {
					Utility.log("Uploading backup "+ backup, Level.INFO);
					uploadFileToAzureHelper(efisalesResources.getDatabaseBackupsPath() + backup,
							getDatabaseBackupBlobContainer());
				}
			} else {
				Utility.log("No database backups found", Level.INFO);
			}
		}
	}
	
	public static void uploadFileToAzureHelper(String filePath, CloudBlobContainer container) {
		
		EfisalesResource efisalesResources = Utility.getResources();
		boolean uploaded = false;
		
		String pathSeparator = efisalesResources.getRuntime().equals("linux") ? "/" : "\\";
		
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
		
		EfisalesResource eResource = Utility.getResources();
		
		try {
			// Retrieve storage account from connection-string.
			CloudStorageAccount cloudStorageAccount =
					CloudStorageAccount.parse(azureStorageConnectionString);
			
			// Create the blob client.
			CloudBlobClient cloudBlobClient = cloudStorageAccount.createCloudBlobClient();
			
			// Retrieve reference to a previously created container.
			blobContainer = cloudBlobClient.
					getContainerReference(eResource.getBlobStorageContainer());
		} catch (Exception ex) {
			Utility.logStackTrace(ex);
		}
		
		return blobContainer;
	}
	
	public static void deleteOldDbBackups(){
		EfisalesResource efisalesResources= Utility.getResources();
		
		if(efisalesResources.deleteOldDbBackups()){
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
		
		EfisalesResource eResource = Utility.getResources();
		
		try {
			// Retrieve storage account from connection-string.
			CloudStorageAccount cloudStorageAccount =
					CloudStorageAccount.parse(azureStorageConnectionString);
			
			// Create the blob client.
			CloudBlobClient cloudBlobClient = cloudStorageAccount.createCloudBlobClient();
			
			// Retrieve reference to a previously created container.
			databaseBackupBlobContainer = cloudBlobClient.
					getContainerReference(eResource.getDatabaseBackupContainer());
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
				
				String pathSeparator = Utility.getResources().getRuntime().equals("linux") ? "/" : "\\";
				
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

			for(String file: files){
				uploadFileToGoogleStorage(uploaderConfig.getUploadsPath()+file);
			}
		}
		catch (Exception ex){
			Utility.logStackTrace(ex);
		}
	}

	public synchronized static void uploadFileToGoogleStorage(String filePath)
		throws IOException{
		FileUploaderConfig fileUploaderConfig= Utility.getFileUploaderConfig();

		String filename=filePath.substring(filePath.lastIndexOf("\\")+1);

		if(DAL.fileExists(filename)) return;

		Utility.log("Uploading file: "+ filePath+ ", to google storage", Level.INFO);

		GoogleCredentials googleCredentials=GoogleCredentials.fromStream(
				new FileInputStream(fileUploaderConfig.getCredentialsPath()));

		Storage googleStorage= StorageOptions.newBuilder().setCredentials(googleCredentials).
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
			EfisalesResource resources= Utility.getResources();

			for(ListBlobItem blobItem: blobsContainer.listBlobs()){
				String path=blobItem.getUri().getPath();
				String filename=path.substring(path.lastIndexOf("/")+1);

				if(!DAL.fileExists(filename) && !Files.exists(Paths.get(resources.getUploadsPath()+filename))){
					Utility.log("URI: "+ blobItem.getUri().toString(), Level.INFO);
					downloadFile(blobItem.getUri().toString(),resources.getUploadsPath() + filename);
				}
				else{
					Utility.log(filename+ " exists, skipping", Level.INFO);
				}
			}
		}
		catch (Exception ex){

		}
	}

}
