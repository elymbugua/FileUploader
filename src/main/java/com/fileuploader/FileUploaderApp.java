package com.fileuploader;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerFactory;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

public class FileUploaderApp {
	public static void main(String[] args) {
		System.out.println("Starting Mbugua File Uploader....");
		
		try{
			SchedulerFactory schedulerFactory= new StdSchedulerFactory();
			Scheduler scheduler= schedulerFactory.getScheduler();
			scheduler.start();
			
			JobDetail job = newJob(FileUploaderJob.class)
					.withIdentity("myJob", "group1")
					.build();
			
			Trigger trigger = newTrigger()
					.withIdentity("myTrigger", "group1")
					.startNow()
					.withSchedule(simpleSchedule()
							.withIntervalInSeconds(20)
							.repeatForever())
					.build();

			scheduler.scheduleJob(job,trigger);

			JobDetail downloadsJob = newJob(AzureDownloads.class)
					.withIdentity("myJob2", "group1")
					.build();

			Trigger downloadsTrigger = newTrigger()
					.withIdentity("myTrigger2", "group1")
					.startNow()
					.withSchedule(simpleSchedule()
							.withIntervalInSeconds(30)
							.repeatForever())
					.build();
			
			scheduler.scheduleJob(downloadsJob,downloadsTrigger);

			JobDetail googleCloudStorageUploadsJob = newJob(UploadFilesToGoogleStorage.class)
					.withIdentity("myJob3", "group1")
					.build();

			Trigger googleCloudStorageUploadsTrigger = newTrigger()
					.withIdentity("myTrigger3", "group1")
					.startNow()
					.withSchedule(simpleSchedule()
							.withIntervalInSeconds(30)
							.repeatForever())
					.build();

			scheduler.scheduleJob(googleCloudStorageUploadsJob,
					googleCloudStorageUploadsTrigger);

			//FileUploader.uploadFilesToGoogleCloudStorage();
		}
		catch (Exception ex){
			Utility.logStackTrace(ex);
		}
	}
}
