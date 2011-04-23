package com.radicaldynamic.groupinform.couchdb;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.radicaldynamic.groupinform.application.Collect;
import com.radicaldynamic.groupinform.utilities.FileUtils;

public class CouchInitializer
{
    private final static String t = "CouchDbUtils: ";
    
    private static final String COUCHDIR = "couchdb";
    private static final String ERLANGDIR = "erlang";
    
    private static final String FILE_INDEX = "installedfiles.index";
    
    private static final String DIRECTORY = "d";
    private static final String LINK = "l";
    private static final String FILE = "f";
    
    // This cannot be dynamically determined from FileUtils.EXTERNAL_PATH because it changes between 2.1/2.2
    private static final String SOURCE_PATH = "sdcard/Android/data/com.radicaldynamic.groupinform";
    
    // The private data directory to initialize into
    private static final String DESTINATION_PATH = Environment.getDataDirectory() + File.separator + "data" + File.separator + "com.radicaldynamic.groupinform";
    
    public static boolean isEnvironmentInitialized()
    {
        return new File(DESTINATION_PATH, COUCHDIR).isDirectory();
    }
    
    public static void initializeEnvironment(Handler handler)
    {
        List<String> index = new ArrayList<String>();
        
        // This method must have the index of installed files as prepared by CouchInstaller
        if (new File(FileUtils.EXTERNAL_FILES, FILE_INDEX).exists() == false) {
            Log.e(Collect.LOGTAG, t + "unable to initialize data directory: index of installed files is missing");
            return;   
        }
        
        // Remove pre-existing files (from upgrades, broken installs, etc) 
        if (new File(DESTINATION_PATH, COUCHDIR).isDirectory()) {
            Log.v(Collect.LOGTAG, t + "purging " + COUCHDIR + " data directory...");
            CouchInstaller.deleteDirectory(new File(DESTINATION_PATH, COUCHDIR));
        }

        // Remove pre-existing files (from upgrades, broken installs, etc)
        if (new File(DESTINATION_PATH, ERLANGDIR).isDirectory()) {
            Log.v(Collect.LOGTAG, t + "purging " + ERLANGDIR + " data directory...");
            CouchInstaller.deleteDirectory(new File(DESTINATION_PATH, ERLANGDIR));
        }
        
        Log.i(Collect.LOGTAG, t + "initializing data directory for CouchDB and Erlang/OTP");
        Log.d(Collect.LOGTAG, t + "source path is " + SOURCE_PATH);
        Log.d(Collect.LOGTAG, t + "destination path is " + DESTINATION_PATH);

        /*
         * Go through each of the files listed in the index of installed files and take appropriate
         * action depending on the type of file listed.  The purpose of this loop is to copy or link 
         * files into the /data/data hierarchy where executable files can be used (and where the 
         * compiled version of Couch/Erlang expects to find them). 
         */
        try {
            index = org.apache.commons.io.FileUtils.readLines(new File(FileUtils.EXTERNAL_FILES, FILE_INDEX));            
            Iterator<String> entries = index.iterator();
            
            float entriesProcessed = 0;
            
            while (entries.hasNext()) {
                String entry = entries.next();
                
                if (Pattern.matches(".*" + SOURCE_PATH + File.separator + ".*", entry))
                    initializeEntry(entry);
                
                if (handler != null) {
                    Message progress = new Message();
                    progress.arg1 = (int) Math.round(++entriesProcessed / index.size() * 100);
                    progress.arg2 = 0;
                    progress.what = CouchInstaller.PROGRESS;
                    handler.sendMessage(progress);
                }
            }
            
            // Close progress bar
            Message progress = new Message();
            progress.arg1 = 1;                  // Our way of telling the handler not to restart the activity
            progress.arg2 = 0;
            progress.what = CouchInstaller.COMPLETE;
            handler.sendMessage(progress);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            Log.e(Collect.LOGTAG, t + "unable to read index file: " + e.toString());
            e.printStackTrace();
            return;
        }
    }
    
    /*
     * Figure out what to do with a file listed in the index of installed files
     * 
     * The format of an entry in the index is 
     * FILE_TYPE FILE_MODE FILE_PATH LINK_TO
     * 0         1         2         3
     */
    private static void initializeEntry(String entry)
    {
        Log.v(Collect.LOGTAG, t + "initializing " + entry);
        
        String [] info = entry.split("\\s");
        String neutralPath = info[2].replace(SOURCE_PATH, "");
        
        if (info[0].equals(DIRECTORY)) {
            new File(DESTINATION_PATH, neutralPath).mkdirs();
            
            try {
                Runtime.getRuntime().exec("chmod 755 " + DESTINATION_PATH + neutralPath);
            } catch (IOException e) {
                Log.e(Collect.LOGTAG, t + "failed to chmod 755 " + e.toString());
                e.printStackTrace();
            } 
        }
        
        if (info[0].equals(LINK)) {            
            try {
                Runtime.getRuntime().exec("/system/bin/ln -s " + info[3] + " " + DESTINATION_PATH + neutralPath);
            } catch (IOException e) {
                Log.e(Collect.LOGTAG, t + "failed to link " + e.toString());
                e.printStackTrace();
            }
        }
        
        if (info[0].equals(FILE)) {
            if (info[1].equals("420")) {
                try {
                    Runtime.getRuntime().exec("/system/bin/ln -s " + FileUtils.EXTERNAL_PATH + neutralPath + " " + DESTINATION_PATH + neutralPath);
                } catch (IOException e) {
                    Log.e(Collect.LOGTAG, t + "failed to link " + e.toString());
                    e.printStackTrace();
                }
            } else if (info[1].equals("493")) {
                try {
                    org.apache.commons.io.FileUtils.copyFile(new File(FileUtils.EXTERNAL_PATH, neutralPath), new File(DESTINATION_PATH, neutralPath));
                    Runtime.getRuntime().exec("/system/bin/chmod 755 " + DESTINATION_PATH + neutralPath);
                } catch (IOException e) {
                    Log.e(Collect.LOGTAG, t + "unable to duplicate " + e.toString());
                    e.printStackTrace();
                }
            } else {
                Log.e(Collect.LOGTAG, t + "unhandled file mode " + info[1]);
            }
        }
    }
}