package com.fileuploader;


import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class GoogleDriveWorker {
	
	
	public static final ConcurrentHashMap<String,String>
			FOLDER_IDS_MAP= new ConcurrentHashMap<>();
	
	public static final String BASE_BACKUPS_FOLDER_ID ="11I5OgqN4Y7OiCV0YYrVsaZ6DBPSipyV1";
	
	public static final File createGoogleDriveFolder(String parentFolder,
	                                                 String folderName)
	throws IOException {
	
		File fileMetaData= new File();
		fileMetaData.setName(folderName);
		fileMetaData.setMimeType(GoogleDriveUtils.FOLDER_MIME_TYPE);
		
		if(parentFolder!=null){
			List<String> parentsList= Arrays.asList(parentFolder);
			fileMetaData.setParents(parentsList);
		}
		
		Drive driveService= GoogleDriveUtils.getDriveService();
		
		try{
			File file=driveService.files().create(fileMetaData).setFields("id,name").execute();
			return file;
		}
		catch (Exception ex){
			Utility.logStackTrace(ex);
			return null;
		}
	
	}
	
	public static File createGoogleDriveFile(String parentFolder, String contentType,
                String filename, AbstractInputStreamContent inputStreamContent)
											throws IOException{
		File gDriveFile= new File();
		gDriveFile.setName(filename);
		
		if(parentFolder!=null){
			gDriveFile.setParents(Collections.singletonList(parentFolder));
		}
		
		Drive driveService= GoogleDriveUtils.getDriveService();
		
		Utility.log("File Content Type: "+ contentType, Level.INFO);
		
		try{
			File file= driveService.files().create(gDriveFile, inputStreamContent)
					.setFields("id, webContentLink, webViewLink, parents")
					.execute();
			
			return file;
		}
		catch (Exception ex){
			Utility.logStackTrace(ex);
			return null;
		}
		
	}
	
	public static File uploadFileFromByteStream(String parentFolder, String contentType,
                String filename,byte[] bytes) throws IOException {
		
		AbstractInputStreamContent uploadContent= new ByteArrayContent(contentType,bytes);
		return createGoogleDriveFile(parentFolder,contentType,filename,uploadContent);
	}
	
	public static File createFileFromJavaIOFile(String parentFolder, String contentType,
                String filename,java.io.File file)throws IOException{
		
		AbstractInputStreamContent uploadContent= new FileContent(contentType,file);
		return createGoogleDriveFile(parentFolder,contentType,filename,uploadContent);
	}
	
	public static File createFileFromInputStream(String parentFolder, String contentType,
                 String filename, InputStream inputStream)throws IOException{
		AbstractInputStreamContent uploadContent=
				new InputStreamContent(contentType,inputStream);
		return createGoogleDriveFile(parentFolder,contentType,filename,uploadContent);
	}
	
	//searches for files that match a specific pattern
	public static List<File> searchFiles(String pattern)throws IOException{
		
		List<File> filesList= new ArrayList<>();
		
		try{
			Drive driveService=GoogleDriveUtils.getDriveService();
			
			String query=" name contains '" + pattern + "' " //
					+ " and mimeType != 'application/vnd.google-apps.folder' ";
			
			String pageToken=null;
			
			do{
				FileList result= driveService.files().list().setQ(query)
						.setSpaces("drive")
						.setFields("nextPageToken, files(id, name, createdTime, mimeType)")
						.setPageToken(pageToken)
						.execute();
				
				filesList.addAll(result.getFiles());
				pageToken=result.getNextPageToken();
			}while (pageToken!=null);
		}
		catch (Exception ex){
			Utility.logStackTrace(ex);
		}
		
		return filesList;
	}

	public void deleteFilesOlderThanDays(int days){

	}

}
