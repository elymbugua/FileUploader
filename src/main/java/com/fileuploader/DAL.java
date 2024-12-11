package com.fileuploader;

import javax.persistence.*;
import java.util.List;

public class DAL {
	
	static EntityManagerFactory entityManagerFactory=null;
	
	private static EntityManagerFactory getEntityManagerFactory(){
		if(entityManagerFactory!=null) return entityManagerFactory;

		var config= Utility.getFileUploaderConfig();
		entityManagerFactory= Persistence.
				createEntityManagerFactory(config.getUploaderDbPath());
		
		return entityManagerFactory;
	}
	
	public static void addFile(String file){
		EntityManager entityManager= getEntityManagerFactory().createEntityManager();
		try{
			entityManager.getTransaction().begin();
			UploadedFile uploadedFile= new UploadedFile(file);
			entityManager.persist(uploadedFile);
			entityManager.getTransaction().commit();
		}
		finally {
			entityManager.close();
		}
	}
	
	public static void addFolder(String name, String folderId){
		EntityManager em= getEntityManagerFactory().createEntityManager();
		
		try{
			em.getTransaction().begin();
			Folder folder= new Folder(name,folderId);
			em.persist(folder);
			em.getTransaction().commit();
		}
		finally {
			em.close();
		}
	}
	
	public static Folder getFolder(String name){
		EntityManager em= getEntityManagerFactory().createEntityManager();
		
		try{
			Query query= em.createQuery("Select f from Folder f where f.name= :name", Folder.class);
			query.setParameter("name",name);
			List<Folder> folders= query.getResultList();
			return folders.isEmpty()?null:folders.get(0);
		}
		finally {
			em.close();
		}
	}
	
	public static boolean fileExists(String file){
		EntityManager entityManager= getEntityManagerFactory().createEntityManager();
		try{
			Query q= entityManager.createQuery(
					"Select f from UploadedFile f where f.filename= :filename", UploadedFile.class);
			q.setParameter("filename",file);
			List<UploadedFile> files= q.getResultList();
			return !files.isEmpty();
		}
		finally {
			entityManager.close();
		}
		
	}
	
	public static void removeFile(String filename){
		EntityManager entityManager= getEntityManagerFactory().createEntityManager();
		
		try{
			entityManager.getTransaction().begin();
			Query query= entityManager.createQuery("Delete f from UploadedFile where f.filename= :file");
			query.setParameter("file", filename);
			query.executeUpdate();
			entityManager.getTransaction().commit();
		}
		finally {
			entityManager.close();
		}
		
		
	}
	
	public static void disposeEntityManagerfactory(){
		if(entityManagerFactory!=null)
			entityManagerFactory.close();
	}
	
}
