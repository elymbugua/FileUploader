package com.fileuploader;

import com.google.gson.Gson;
import java.io.*;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.MathContext;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
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

    static ExecutorService threadPool = null;
    
    static EfisalesResource efisalesResource=null;

    static FileUploaderConfig fileUploaderConfig=null;

    private static final Object reloadResourcesLock = new Object();

    public static String getSiteVersion() {
        return getResources().SiteVersion;
    }

//    public static String cachingBaseUrl = getResources().RedisClusterUrl;
    //  public static String coreSiteBaseUrl = getResources().getCoreSiteBaseUrl();


    public static void cleanUp(Connection connection,
                               CallableStatement statement, ResultSet resultSet) {

        try {
            if (connection != null)
                connection.close();

            if (statement != null)
                statement.close();

            if (resultSet != null) {
                resultSet.close();
            }
        } catch (Exception e) {
            Utility.logStackTrace(e);
        }
    }

    public static boolean startDateIsLessThanEndDate(java.util.Date startDate,
                                                     java.util.Date endDate) {

        return startDate.before(endDate) && !startDate.equals(endDate);
    }

    public static String formatTitleStyle(String sentence) {

        if (sentence == null || sentence.trim().isEmpty())
            return "";

        //removes trailing spaces. crazy bug

        sentence = sentence.trim();

        String[] words = sentence.split(" ");

        StringBuilder output = new StringBuilder();

        for (String word : words) {
            if (word.length() > 1) {
                output.append(word.substring(0, 1).toUpperCase()).append(word.substring(1).toLowerCase()).append(" ");
            } else {
                output.append(word.toUpperCase()).append(" ");
            }
        }

        return output.toString().trim();
    }

    public static String formatSentenceStyle(String sentence) {
        if (sentence == null || sentence.trim().isEmpty())
            return "";

        //removes trailing spaces. crazy bug
        sentence = sentence.trim();

        if (sentence.length() > 1) {
            return sentence.substring(0, 1).toUpperCase() +
                    sentence.substring(1).toLowerCase();
        } else {
            return sentence.toUpperCase();
        }
    }

    public static String stripComasInValues(String value) {

        if (value == null)
            return "";

        value = value.trim();
        value = value.replace(" ", "");
        if (value.startsWith("."))
            value = "0";

        return value.replace(",", "");

    }

    public static String getFileExtension(String path) {

        if (path == null || path.trim().isEmpty()) {
            return null;
        }

        if (!path.contains(".")) {
            return null;
        }

        return path.substring(path.lastIndexOf(".") + 1).toLowerCase();
    }

    public String getNumericMonth(String monthText) {
        if (monthText == null)
            return "";

        monthText = monthText.toLowerCase();

        if (monthText.startsWith("jan"))
            return "01";
        else if (monthText.startsWith("feb"))
            return "02";
        else if (monthText.startsWith("mar"))
            return "03";
        else if (monthText.startsWith("apr"))
            return "04";

        return "";

    }

    public static int getDaysBetweenDates(Date smallDate, Date largeDate) {

        if (smallDate == null || largeDate == null) {
            return 0;
        }

        DateTime d1 = new DateTime(smallDate);
        DateTime d2 = new DateTime(largeDate);
        return Days.daysBetween(d1, d2).getDays() + 1; //+1 to cater for the current Date
    }

    public static List<Date> getDatesBetweenDates(Date smallDate, Date largeDate) {

        int daysBetween = getDaysBetweenDates(smallDate, largeDate);

        LocalDate startDate = LocalDate.fromDateFields(smallDate);

        return IntStream.iterate(0, i -> i + 1)
                .limit(daysBetween)
                .mapToObj(startDate::plusDays)
                .map(LocalDate::toDate)
                .collect(Collectors.toList());

    }

    public static int getWorkingDatesBetweenDates(Date smallDate, Date largeDate) {

        int daysBetween = getDaysBetweenDates(smallDate, largeDate);

        LocalDate startDate = LocalDate.fromDateFields(smallDate);

        return IntStream.iterate(0, i -> i + 1)
                .limit(daysBetween)
                .mapToObj(startDate::plusDays)
                .filter(localDate -> localDate.getDayOfWeek() != DateTimeConstants.SUNDAY)
                .collect(Collectors.toList())
                .size();

    }

    public static int getDifferenceBetweenDatesInMilliSeconds
            (Date smallerDate, Date largerDate) {

        DateTime smallDtime = new DateTime(smallerDate);
        DateTime largerDtime = new DateTime(largerDate);

        Seconds secondsBetween = Seconds.secondsBetween(smallDtime, largerDtime);

        return secondsBetween.getSeconds() * 1000;
    }

    public static int getDifferenceBetweenDatesInMinutes(Date smallerDate, Date largerDate) {

        DateTime smallDatetime = new DateTime(smallerDate);
        DateTime largerDatetime = new DateTime(largerDate);

        try {
            Minutes minutesBetween = Minutes.minutesBetween(smallDatetime, largerDatetime);
            return minutesBetween.getMinutes();
        } catch (Exception ex) {
            Utility.logStackTrace(ex);
            Utility.log("StartDate-> " + Formatter.formatDateTime(smallerDate) + ";Ending Date-> " +
                    Formatter.formatDateTime(largerDate), Level.SEVERE);

            return 0;
        }

    }

    public static Date addDaysToDate(Date d, int days) {

        DateTime dateTime = new DateTime(d);

        DateTime futureDate = dateTime.plusDays(days);

        return futureDate.toDate();
    }

    public static Date subtractDaysFromDate(Date d, int days) {
        DateTime dTime = new DateTime(d);

        DateTime earlierDate = dTime.minusDays(days);

        return earlierDate.toDate();
    }

    public static String getTimeFromMilliseconds(long milliseconds) {

        if (milliseconds <= 0) {
            return "";
        }

        int oneMinute = 60000; //1 minute is 60000 milliseconds

        if (milliseconds < oneMinute) {
            return milliseconds / 1000 + " Seconds";
        }

        long totalSeconds = milliseconds / 1000;

        long fullMinutes = (totalSeconds - totalSeconds % 60) / 60;

        long overflowMinutes = fullMinutes % 60;

        long totalHours = fullMinutes - overflowMinutes;

        long hours = 0;

        if (totalHours > 0) {
            hours = totalHours / 60;
        }

        if (hours > 0 && overflowMinutes > 0) {
            return hours + " Hrs " + overflowMinutes + " Mins";
        } else if (hours > 0) {
            return hours + " Hrs";
        } else if (overflowMinutes > 0) {
            return overflowMinutes + " Mins";
        } else {
            return "";
        }
    }

    public static Date getStartOfMonth(Date d) {
        if (d == null) {
            d = new Date();
        }

        DateTime dateTime = new DateTime(d).dayOfMonth().withMinimumValue();
        return dateTime.toDate();
    }

    public static Date getEndofMonth(Date d) {
        if (d == null) {
            d = new Date();
        }

        DateTime dateTime = new DateTime(d).dayOfMonth().withMaximumValue();
        return dateTime.toDate();
    }

    public static EfisalesResource getResources() {

        //Singleton
        if (efisalesResource != null) {
            return efisalesResource;
        }

        efisalesResource = deserializeResources();
        return efisalesResource;
    }

    public static void setEfisalesResources(EfisalesResource resources) {
        if (resources == null)
            throw new IllegalArgumentException("Resources cannot be null");

        efisalesResource = resources;
    }

    public static EfisalesResource deserializeResources() {
        String homeFolder= GoogleDriveUtils.USER_HOME_FOLDER;
        String resourcesFileName="appresources.json";
        String resourceFile="";
        
        Utility.log("Path Separator: "+ File.pathSeparator, Level.INFO);
        Utility.log("Path SeparatorChar: "+ File.pathSeparatorChar, Level.INFO);
        
        if(File.pathSeparator.equals("/") ||
                File.pathSeparator.equals(":")){
            resourceFile= homeFolder.endsWith("/")?homeFolder+resourcesFileName
                    :homeFolder+"/"+resourcesFileName;
        }
        else{
            resourceFile= homeFolder.endsWith("\\")?homeFolder+resourcesFileName
                    :homeFolder+"\\"+resourcesFileName;
        }
        

        if (resourceFile == null) {
            log("Resource path environment variable not resolved", Level.SEVERE);
        } else {
            log(resourceFile, Level.INFO);
        }

        BufferedReader bufferedReader = null;

        try {
            bufferedReader = new BufferedReader(new FileReader(resourceFile));
            Type type = new TypeToken<EfisalesResource>() {}.getType();
            efisalesResource = new Gson().fromJson(bufferedReader, type);
            efisalesResource.DbPassword = Encryptor.Decrypt(efisalesResource.DbPassword);
            efisalesResource.SmtpPassword = Encryptor.Decrypt(efisalesResource.SmtpPassword);
            return efisalesResource;
        } catch (Exception e) {
            log(e.toString(), Level.SEVERE);

            if (bufferedReader == null) {
                log("Resources file not resolved. Probably permission issues", Level.SEVERE);
            }
        }
        return null;
    }

    public static void refreshResources() {
        synchronized (reloadResourcesLock) {
            Utility.log("Reloading Config...", Level.INFO);
            long startTime = System.currentTimeMillis();
            efisalesResource = deserializeResources();
            long timeTaken = System.currentTimeMillis() - startTime;
            Utility.log("Done Reloading Config took " + timeTaken, Level.SEVERE);
        }
    }

    public synchronized static FileUploaderConfig getFileUploaderConfig(){
        if(fileUploaderConfig!=null) return fileUploaderConfig;

        String homeFolder= GoogleDriveUtils.USER_HOME_FOLDER;
        String resourcesFileName="file-uploader-config.json";
        String resourceFile="";

        Utility.log("Path Separator: "+ File.pathSeparator, Level.INFO);
        Utility.log("Path SeparatorChar: "+ File.pathSeparatorChar, Level.INFO);

        if(File.pathSeparator.equals("/") ||
                File.pathSeparator.equals(":")){
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
        /*if (efisalesResource != null) {
            LogPayload logPayLoad = new LogPayload();
            logPayLoad.ApplicationId = getResources().getApplicationId();
            logPayLoad.OS = efisalesResource.getRuntime();
            logPayLoad.LogMessage = message;

            if (logLevel == Level.INFO) {
                logPayLoad.LogCategorization = LogConstants.Info;
            } else if (logLevel == Level.SEVERE) {
                logPayLoad.LogCategorization = LogConstants.Error;
            } else if (logLevel == Level.WARNING) {
                logPayLoad.LogCategorization = LogConstants.Warning;
            } else {
                logPayLoad.LogCategorization = LogConstants.Error;
            }

            logPayLoad.LoggingDate = new Date();
            logPayLoad.LoggingSource = LogSource.AppLogger;
            logPayLoad.IpAddress = Utility.getHostIP();
            HunterConnector.log(logPayLoad);
        }*/

    }

    public static void logStackTrace(Exception ex) {
        String stackTrace = ExceptionUtils.getFullStackTrace(ex);
        log(stackTrace, Level.SEVERE);
        /*String appId = efisalesResource == null ? "Efisales" :
                efisalesResource.getApplicationId();
        HunterConnector.logError(stackTrace, appId);*/
    }

    public static Double roundNumber(Number n, int decimalPlaces) {

        return new BigDecimal((Double) n).
                round(new MathContext(decimalPlaces)).doubleValue();
    }


    public static String getHostMacAddress() {

        try {

            if (Utility.getResources().getAppEnvironment() != null && Utility.getResources()
                    .getAppEnvironment()
                    .equalsIgnoreCase(EfisalesResource.AppEnvironments.STAGING.toString())) {
                return Utility.getResources().getStagingDefaultMac();
            }

            NetworkInterface networkInterface =
                    NetworkInterface.getByInetAddress(InetAddress.getLocalHost());

            byte[] mac = networkInterface.getHardwareAddress();
            StringBuilder macBuilder = new StringBuilder();

            for (int i = 0; i < mac.length; i++) {
                macBuilder.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));
            }

            return macBuilder.toString();
        } catch (Exception ex) {
            Utility.logStackTrace(ex);
            return null;
        }
    }

    public static String getHostIP() {

        try {
            InetAddress ipAddress = InetAddress.getLocalHost();
            return ipAddress.getHostAddress();
        } catch (Exception ex) {
            Utility.logStackTrace(ex);
            return null;
        }
    }

    public static String getIdsListAsString(List<String> idsList) {

        if (idsList == null)
            throw new IllegalArgumentException("Ids List cannot be null");

        StringBuilder builder = new StringBuilder();

        if (idsList.isEmpty()) {
            return "";
        }

        for (int i = 0; i < idsList.size() - 1; i++) {
            builder.append(idsList.get(i)).append(",");
        }

        builder.append(idsList.get(idsList.size() - 1));
        return builder.toString();
    }

    public static String getDayOfTheWeekByIndex(int index) {
        //no locale, default English.
        String days[] = {"", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        try {
            return days[index];
        } catch (Exception e) {
            Utility.logStackTrace(e);
            return "";
        }
    }

    public static ExecutorService getFixedThreadPool() {
        if (threadPool != null) {
            return threadPool;
        }

        //threadPool= Executors.newFixedThreadPool(100);

        ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newCachedThreadPool();

        threadPool = pool;

        /*Utility.log("Core threads: " + pool.getCorePoolSize(), Level.INFO);
        Utility.log("Largest executions: "
                + pool.getLargestPoolSize(), Level.INFO);
        Utility.log("Maximum allowed threads: "
                + pool.getMaximumPoolSize(), Level.INFO);
        Utility.log("Current threads in pool: "
                + pool.getPoolSize(), Level.INFO);
        Utility.log("Currently executing threads: "
                + pool.getActiveCount(), Level.INFO);
        Utility.log("Total number of threads(ever scheduled): "
                + pool.getTaskCount(), Level.INFO);
        Utility.log("Keep Alive Time: "
                + pool.getKeepAliveTime(TimeUnit.SECONDS), Level.INFO);*/

        return threadPool;
    }

    public static <T extends Serializable> float getObjectSizeInKB(T object) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream objectOutPutStream = new ObjectOutputStream(bos);
            objectOutPutStream.writeObject(object);
            objectOutPutStream.flush();

            Utility.log("object size " + bos.toByteArray().length, Level.INFO);

            return (float) bos.toByteArray().length / 13266; //in KB
        } catch (Exception ex) {
            Utility.logStackTrace(ex);
            return 0;
        }
    }

    public static DataSource getDefaultDatasource() {

        String datasourceName = getResources().JdbcDatasource;
        try {
            InitialContext context = new InitialContext();
            DataSource ds = (DataSource) context.lookup(datasourceName);
            return ds;
        } catch (Exception ex) {
            Utility.logStackTrace(ex);
            return null;
        }

    }

    public static double distanceBetweenCoordinates(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.sin(dLon / 2) * Math.sin(dLon / 2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return earthRadiusKm * c;
    }
}
