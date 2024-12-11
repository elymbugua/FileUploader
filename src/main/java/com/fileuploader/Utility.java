package com.fileuploader;

import com.google.gson.Gson;
import java.io.*;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.MathContext;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.sql.*;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.naming.InitialContext;
import javax.sql.DataSource;

import com.google.gson.reflect.TypeToken;
import org.joda.time.*;
import org.apache.commons.lang.exception.ExceptionUtils;

public class Utility {
    static final Logger logger = Logger.getLogger(Utility.class.getCanonicalName());
    static FileUploaderConfig fileUploaderConfig=null;

    public static int getDaysBetweenDates(Date smallDate, Date largeDate) {

        if (smallDate == null || largeDate == null) {
            return 0;
        }

        DateTime d1 = new DateTime(smallDate);
        DateTime d2 = new DateTime(largeDate);
        return Days.daysBetween(d1, d2).getDays() + 1; //+1 to cater for the current Date
    }

    public static Date addDaysToDate(Date d, int days) {

        DateTime dateTime = new DateTime(d);

        DateTime futureDate = dateTime.plusDays(days);

        return futureDate.toDate();
    }

    public synchronized static FileUploaderConfig getFileUploaderConfig(){
        if(fileUploaderConfig!=null) return fileUploaderConfig;

        String homeFolder= GoogleDriveUtils.USER_HOME_FOLDER;
        String resourcesFileName="file-uploader-config.json";
        String resourceFile="";

        Utility.log("Path Separator: "+ File.pathSeparator, Level.INFO);
        Utility.log("Path SeparatorChar: "+ File.pathSeparatorChar, Level.INFO);

        if(File.pathSeparator.equals("/") || File.pathSeparator.equals(":")){
            resourceFile= homeFolder.endsWith("/")?homeFolder+resourcesFileName
                    :homeFolder+"/"+resourcesFileName;
        }
        else{
            resourceFile= homeFolder.endsWith("\\")?homeFolder+resourcesFileName
                    :homeFolder+"\\"+resourcesFileName;
        }


        if (resourceFile == null) {
            log("Config path environment variable not resolved", Level.SEVERE);
        } else {
            log(resourceFile, Level.INFO);
        }

        BufferedReader bufferedReader = null;

        try {
            bufferedReader = new BufferedReader(new FileReader(resourceFile));
            Type type = new TypeToken<FileUploaderConfig>() {}.getType();
            fileUploaderConfig = new Gson().fromJson(bufferedReader, type);
            return fileUploaderConfig;
        } catch (Exception e) {
            log(e.toString(), Level.SEVERE);

            if (bufferedReader == null) {
                log("Config file not resolved. Probably permission issues", Level.SEVERE);
            }
        }
        return null;
    }

    public static void log(String message, Level logLevel) {
        logger.log(logLevel, message);
    }

    public static void logStackTrace(Exception ex) {
        String stackTrace = ExceptionUtils.getFullStackTrace(ex);
        log(stackTrace, Level.SEVERE);
    }

}
