/*
 * Copyright (C) 2009 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.radicaldynamic.groupinform.tasks;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.protocol.HttpContext;
import org.ektorp.Attachment;
import org.ektorp.AttachmentInputStream;
import org.ektorp.DbAccessException;
import org.ektorp.DocumentNotFoundException;

import android.os.AsyncTask;
import android.util.Log;

import com.radicaldynamic.groupinform.application.Collect;
import com.radicaldynamic.groupinform.documents.FormInstanceDocument;
import com.radicaldynamic.groupinform.documents.GenericDocument;
import com.radicaldynamic.groupinform.listeners.InstanceUploaderListener;
import com.radicaldynamic.groupinform.utilities.FileUtils;
import com.radicaldynamic.groupinform.utilities.WebUtils;

/**
 * Background task for uploading completed forms.
 * 
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class InstanceUploaderTask extends AsyncTask<String, Integer, ArrayList<String>> {

    private static String t = "InstanceUploaderTask: ";
    //private static long MAX_BYTES = 1048576 - 1024; // 1MB less 1KB overhead
    InstanceUploaderListener mStateListener;
    String mUrl;
    private static final int CONNECTION_TIMEOUT = 30000;


    public void setUploadServer(String newServer) {
        mUrl = newServer;
    }
    
    /**
     * The values are the names of the instances to upload -- i.e., the directory names.
     * 
     */
    @Override
    protected ArrayList<String> doInBackground(String... values) {
        ArrayList<String> uploadedInstances = new ArrayList<String>();
        int instanceCount = values.length;

        // get shared HttpContext so that authentication and cookies are retained.
        HttpContext localContext = Collect.getInstance().getHttpContext();
        
        URI u = null;
        try {
            URL url = new URL(mUrl);
            u = url.toURI();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

    	HttpClient httpclient = WebUtils.createHttpClient(CONNECTION_TIMEOUT);
        
        HttpHead httpHead = WebUtils.createOpenRosaHttpHead(u);

        // prepare response and return uploaded
        HttpResponse response = null;
        try {
            response = httpclient.execute(httpHead,localContext);
            int statusCode = response.getStatusLine().getStatusCode();
            if ( statusCode != 204 ) {
            	Log.w(t, "Status code on Head request: " + statusCode );
            }
	    } catch (ClientProtocolException e) {
	        e.printStackTrace();
	    } catch (IOException e) {
	        e.printStackTrace();
	    } catch (IllegalStateException e) {
	        e.printStackTrace();
	    }

        for (int i = 0; i < instanceCount; i++) {
            publishProgress(i + 1, instanceCount);
            
            FormInstanceDocument instance = null;
            
            // Download instance files from database so ODK code can upload them
            try {
                instance = Collect.getInstance().getDbService().getDb().get(FormInstanceDocument.class, values[i]);
                
                if (instance.getDateAggregated() != null && instance.getDateUpdatedAsCalendar().after(instance.getDateAggregatedAsCalendar())) {
                    Log.w(Collect.LOGTAG, t + values[i] + " cannot be uploaded to ODK Aggregate: dateUpdated is newer than dateAggregated");
                    cancel(true);
                }
                
                HashMap<String, Attachment> attachments = (HashMap<String, Attachment>) instance.getAttachments();
                
                for (Entry<String, Attachment> entry : attachments.entrySet()) {                    
                    String key = entry.getKey();

                    AttachmentInputStream ais = Collect.getInstance().getDbService().getDb().getAttachment(values[i], key);
                    
                    // ODK code below expects the XML instance to have a .xml extension
                    if (key.equals("xml")) 
                        key = values[i] + ".xml";
                    
                    FileOutputStream file = new FileOutputStream(new File(FileUtils.EXTERNAL_CACHE, key));
                    byte[] buffer = new byte[8192];
                    int bytesRead = 0;                    

                    while ((bytesRead = ais.read(buffer)) != -1) {
                        file.write(buffer, 0, bytesRead);
                    }

                    ais.close();
                    file.close();
                }
            } catch (DocumentNotFoundException e) {
                Log.w(Collect.LOGTAG, t + "DocumentNotFoundException: " + e.toString());
                cancel(true);
            } catch (DbAccessException e) {
                Log.w(Collect.LOGTAG, t + "DbAccessException: " + e.toString());
                cancel(true);
            } catch (Exception e) {
                Log.e(Collect.LOGTAG, t + "unhandled exception: " + e.toString());
                e.printStackTrace();
                cancel(true);
            }

            HttpPost httppost = WebUtils.createOpenRosaHttpPost(u);
            
            // get instance file
            File file = new File(FileUtils.EXTERNAL_CACHE, values[i] + ".xml");

            // find all files in parent directory
            File[] files = file.getParentFile().listFiles();
            if (files == null) {
                Log.e(t, "no files to upload");
                cancel(true);
            }

            // mime post
            MultipartEntity entity = new MultipartEntity();
            for (int j = 0; j < files.length; j++) {
                File f = files[j];
                FileBody fb;
                if (f.getName().endsWith(".xml")) {
                	if ( f.getName().equals(values[i] + ".xml")) {
	                    fb = new FileBody(f, "text/xml");
	                    entity.addPart("xml_submission_file", fb);
	                    Log.i(t, "added xml_submission_file: " + f.getName());
                	} else {
	                    fb = new FileBody(f, "text/xml");
	                    entity.addPart(f.getName(), fb);
	                    Log.i(t, "added xml file " + f.getName());
                	}
                } else if (f.getName().endsWith(".jpg")) {
                    fb = new FileBody(f, "image/jpeg");
                    entity.addPart(f.getName(), fb);
                    Log.i(t, "added image file " + f.getName());
                } else if (f.getName().endsWith(".3gpp")) {
                    fb = new FileBody(f, "audio/3gpp");
                    entity.addPart(f.getName(), fb);
                    Log.i(t, "added audio file " + f.getName());
                } else if (f.getName().endsWith(".3gp")) {
                    fb = new FileBody(f, "video/3gpp");
                    entity.addPart(f.getName(), fb);
                    Log.i(t, "added video file " + f.getName());
                } else if (f.getName().endsWith(".mp4")) {
                    fb = new FileBody(f, "video/mp4");
                    entity.addPart(f.getName(), fb);
                    Log.i(t, "added video file " + f.getName());
                } else {
                    Log.w(t, "unsupported file type, not adding file: " + f.getName());
                }
            }
            httppost.setEntity(entity);

            // prepare response and return uploaded
            response = null;
            try {
                response = httpclient.execute(httppost,localContext);
            } catch (ClientProtocolException e) {
                e.printStackTrace();
                return uploadedInstances;
            } catch (IOException e) {
                e.printStackTrace();
                return uploadedInstances;
            } catch (IllegalStateException e) {
                e.printStackTrace();
                return uploadedInstances;
            }

            int responseCode = response.getStatusLine().getStatusCode();
            Log.e(t, "Response code:" + responseCode);
            // check response.
			try {
				BufferedReader r;
				r = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
				String line;
				while ( (line = r.readLine()) != null ) {
					if ( responseCode == 201 || responseCode == 202) {
						Log.i(t, line);
					} else {
						Log.e(t, line);
					}
				}
			} catch (IllegalStateException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {
					response.getEntity().getContent().close();
				} catch (IllegalStateException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

            // verify that the response was a 201 or 202.  
			// If it wasn't, the submission has failed.
            if (responseCode == 201 || responseCode == 202) {
                uploadedInstances.add(values[i]);
                
                instance.setDateAggregated(GenericDocument.generateTimestamp());
                Collect.getInstance().getDbService().getDb().update(instance);
            }

            // Remove cache files pertaining to this upload
            Log.d(Collect.LOGTAG, t + "purging uploaded files");
            FileUtils.deleteExternalInstanceCacheFiles(values[i]);
        }

        return uploadedInstances;
    }


    @Override
    protected void onPostExecute(ArrayList<String> value) {
        synchronized (this) {
            if (mStateListener != null) {
                mStateListener.uploadingComplete(value);
            }
        }
    }


    @Override
    protected void onProgressUpdate(Integer... values) {
        synchronized (this) {
            if (mStateListener != null) {
                // update progress and total
                mStateListener.progressUpdate(values[0].intValue(), values[1].intValue());
            }
        }
    }


    public void setUploaderListener(InstanceUploaderListener sl) {
        synchronized (this) {
            mStateListener = sl;
        }
    }
}
