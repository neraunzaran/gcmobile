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

package com.radicaldynamic.groupinform.activities;

import java.io.File;
import java.util.HashMap;
import java.util.Set;

import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.model.xform.XFormsModule;
import org.odk.collect.android.logic.FormController;
import org.odk.collect.android.logic.PropertyManager;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.utilities.GestureDetector;
import org.odk.collect.android.views.ODKView;
import org.odk.collect.android.widgets.QuestionWidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore.Images;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Animation.AnimationListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.radicaldynamic.groupinform.R;
import com.radicaldynamic.groupinform.application.Collect;
import com.radicaldynamic.groupinform.documents.FormDefinition;
import com.radicaldynamic.groupinform.documents.FormInstance;
import com.radicaldynamic.groupinform.documents.Generic;
import com.radicaldynamic.groupinform.listeners.FormLoaderListener;
import com.radicaldynamic.groupinform.listeners.FormSavedListener;
import com.radicaldynamic.groupinform.tasks.FormLoaderTask;
import com.radicaldynamic.groupinform.tasks.SaveToDiskTask;
import com.radicaldynamic.groupinform.utilities.FileUtilsExtended;

/**
 * FormEntryActivity is responsible for displaying questions, animating transitions between
 * questions, and allowing the user to enter data.
 * 
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class FormEntryActivity extends Activity implements AnimationListener, FormLoaderListener,
        FormSavedListener {
    private static final String t = "FormEntryActivity";

    // Defines for FormEntryActivity
    private static final boolean EXIT = true;
    private static final boolean DO_NOT_EXIT = false;
    private static final boolean EVALUATE_CONSTRAINTS = true;
    private static final boolean DO_NOT_EVALUATE_CONSTRAINTS = false;

    // Request codes for returning data from specified intent.
    public static final int IMAGE_CAPTURE = 1;
    public static final int BARCODE_CAPTURE = 2;
    public static final int AUDIO_CAPTURE = 3;
    public static final int VIDEO_CAPTURE = 4;
    public static final int LOCATION_CAPTURE = 5;
    public static final int HIERARCHY_ACTIVITY = 6;
    public static final int IMAGE_CHOOSER = 7;
    public static final int AUDIO_CHOOSER = 8;
    public static final int VIDEO_CHOOSER = 9;

    // Extra returned from gp activity
    public static final String LOCATION_RESULT = "LOCATION_RESULT";

    // Identifies the gp of the form used to launch form entry
    public static final String KEY_FORMPATH = "formpath";
    public static final String KEY_INSTANCEPATH = "instancepath";
    public static final String KEY_INSTANCES = "instances";
    public static final String KEY_SUCCESS = "success";
    public static final String KEY_ERROR = "error";

    // Identifies whether this is a new form, or reloading a form after a screen
    // rotation (or similar)
    private static final String NEWFORM = "newform";

    private static final int MENU_DELETE_REPEAT = Menu.FIRST;
    private static final int MENU_LANGUAGES = Menu.FIRST + 1;
    private static final int MENU_HIERARCHY_VIEW = Menu.FIRST + 2;
    private static final int MENU_SAVE = Menu.FIRST + 3;

    private static final int PROGRESS_DIALOG = 1;
    private static final int SAVING_DIALOG = 2;

    private String mFormPath;
    public static String mInstancePath;
    private GestureDetector mGestureDetector;

    public static FormController mFormController;

    private Animation mInAnimation;
    private Animation mOutAnimation;

    private RelativeLayout mRelativeLayout;
    private View mCurrentView;

    private AlertDialog mAlertDialog;
    private ProgressDialog mProgressDialog;
    private String mErrorMessage;

    // used to limit forward/backward swipes to one per question
    private boolean mBeenSwiped;

    private FormLoaderTask mFormLoaderTask;
    private SaveToDiskTask mSaveToDiskTask;

    enum AnimationType {
        LEFT, RIGHT, FADE
    }
    
    // BEGIN custom
    private static final int REMOVE_DIALOG = 3;
    
    private static final int MENU_REMOVE = Menu.FIRST + 4;
    private static final int MENU_INFO = Menu.FIRST + 5;
    
    // See onRetainNonConfigurationInstance()
    private static final String KEY_FORM_DEFINITION = "formdefinition";
    private static final String KEY_FORM_INSTANCE = "forminstance";
    
    public static final String KEY_NEXT_INSTANCE = "skiptonextinstance";
    public static final String KEY_PREVIOUS_INSTANCE = "skiptopreviousinstance";
    
    public static final String KEY_FINISH_ACTIVITY = "finishactivity";

    private FormDefinition mFormDefinition = null;
    private FormInstance mFormInstance = null;
    // END custom
    

    /** Called when the activity is first created. */
    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // must be at the beginning of any activity that can be called from an external intent
        // BEGIN custom
//        try {
//            Collect.createODKDirs();
//        } catch (RuntimeException e) {
//            createErrorDialog(e.getMessage(), EXIT);
//            return;
//        }
        // END custom
        
        setContentView(R.layout.form_entry);
        setTitle(getString(R.string.app_name) + " > " + getString(R.string.loading_form));

        mRelativeLayout = (RelativeLayout) findViewById(R.id.rl);

        mBeenSwiped = false;
        mAlertDialog = null;
        mCurrentView = null;
        mInAnimation = null;
        mOutAnimation = null;
        mGestureDetector = new GestureDetector();

        // Load JavaRosa modules. needed to restore forms.
        new XFormsModule().registerModule();

        // needed to override rms property manager
        org.javarosa.core.services.PropertyManager.setPropertyManager(new PropertyManager(
                getApplicationContext()));

        Boolean newForm = true;
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(KEY_FORMPATH)) {
                mFormPath = savedInstanceState.getString(KEY_FORMPATH);
            }
            if (savedInstanceState.containsKey(NEWFORM)) {
                newForm = savedInstanceState.getBoolean(NEWFORM, true);
            }
            if (savedInstanceState.containsKey(KEY_ERROR)) {
                mErrorMessage = savedInstanceState.getString(KEY_ERROR);
            }
        }

        // If a parse error message is showing then nothing else is loaded
        // Dialogs mid form just disappear on rotation.
        if (mErrorMessage != null) {
            createErrorDialog(mErrorMessage, EXIT);
            return;
        }

        // Check to see if this is a screen flip or a new form load.
        Object data = getLastNonConfigurationInstance();
        if (data instanceof FormLoaderTask) {
            mFormLoaderTask = (FormLoaderTask) data;
        } else if (data instanceof SaveToDiskTask) {
            mSaveToDiskTask = (SaveToDiskTask) data;
            // BEGIN custom
        } else if (data instanceof HashMap<?, ?>) {
            mFormDefinition = (FormDefinition) ((HashMap<String, Generic>) data).get(KEY_FORM_DEFINITION);
            mFormInstance = (FormInstance) ((HashMap<String, Generic>) data).get(KEY_FORM_INSTANCE);
            // END custom
        } else if (data == null) {
            if (!newForm) {
                refreshCurrentView();
                return;
            }

            // Not a restart from a screen orientation change (or other).
            mFormController = null;
            // BEGIN custom
            mInstancePath = null;
            // END custom

            Intent intent = getIntent();
            if (intent != null) {         
                // BEGIN custom
//                Uri uri = intent.getData();
//                                
//                if (getContentResolver().getType(uri) == InstanceColumns.CONTENT_ITEM_TYPE) {
//                    Cursor instanceCursor = this.managedQuery(uri, null, null, null, null);
//                    if (instanceCursor.getCount() != 1) {
//                        this.createErrorDialog("Bad URI: " + uri, EXIT);
//                        return;
//                    } else {
//                        instanceCursor.moveToFirst();
//                        instanceCursor.moveToFirst();
//                        mInstancePath =
//                            instanceCursor.getString(instanceCursor
//                                    .getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH));
//
//                        String jrFormId =
//                            instanceCursor.getString(instanceCursor
//                                    .getColumnIndex(InstanceColumns.JR_FORM_ID));
//
//                        String[] selectionArgs = {
//                            jrFormId
//                        };
//                        String selection = FormsColumns.JR_FORM_ID + " like ?";
//                        
//                        Cursor formCursor =
//                            managedQuery(FormsColumns.CONTENT_URI, null, selection, selectionArgs,
//                                null);
//                        if (formCursor.getCount() == 1) {
//                            formCursor.moveToFirst();
//                            mFormPath =
//                                formCursor.getString(formCursor
//                                        .getColumnIndex(FormsColumns.FORM_FILE_PATH));
//                        } else if (formCursor.getCount() < 1) {
//                            this.createErrorDialog("Parent form does not exist", EXIT);
//                            return;
//                        } else if (formCursor.getCount() > 1) {
//                            this.createErrorDialog("More than one possible parent form", EXIT);
//                            return;
//                        }
//                            
//                    }
//                    
//                } else if (getContentResolver().getType(uri) == FormsColumns.CONTENT_ITEM_TYPE) {
//                    Cursor c = this.managedQuery(uri, null, null, null, null);
//                    if (c.getCount() != 1) {
//                        this.createErrorDialog("Bad URI: " + uri, EXIT);
//                        return;
//                    } else {
//                        c.moveToFirst();
//                        mFormPath = c.getString(c.getColumnIndex(FormsColumns.FORM_FILE_PATH));
//                    }
//                } else {
//                    Log.e(t, "unrecognized URI");
//                    this.createErrorDialog("unrecognized URI: " + uri, EXIT);
//                    return;
//                }
                
                // Set browse list
                Collect.getInstance().setInstanceBrowseList(intent.getStringArrayListExtra(KEY_INSTANCES));
                
                // Create folders for form storage; set form path
                String formFolder = FileUtilsExtended.FORMS_PATH + File.separator + intent.getStringExtra(KEY_FORMPATH);          
                mFormPath = formFolder + File.separator + intent.getStringExtra(KEY_FORMPATH) + ".xml";                

                if (intent.hasExtra(KEY_INSTANCEPATH)) {
                    // Create folders for instance storage; set instance path
                    String instanceFolder = FileUtilsExtended.INSTANCES_PATH + File.separator + intent.getStringExtra(KEY_INSTANCEPATH);
                    mInstancePath = instanceFolder + File.separator + intent.getStringExtra(KEY_INSTANCEPATH) + ".xml";
                    Log.v(Collect.LOGTAG, t + ": InstancePath is " + mInstancePath);
                }
                // END custom
                
                mFormLoaderTask = new FormLoaderTask();
                mFormLoaderTask.execute(mFormPath);
                showDialog(PROGRESS_DIALOG);
            }
        }
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_FORMPATH, mFormPath);
        outState.putBoolean(NEWFORM, false);
        outState.putString(KEY_ERROR, mErrorMessage);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (resultCode == RESULT_CANCELED) {
            // request was canceled, so do nothing
            return;
        }

        ContentValues values;
        Uri imageURI;
        switch (requestCode) {
            case BARCODE_CAPTURE:
                String sb = intent.getStringExtra("SCAN_RESULT");
                ((ODKView) mCurrentView).setBinaryData(sb);
                saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
                break;
            case IMAGE_CAPTURE:
                /*
                 * We saved the image to the tempfile_path, but we really want it to be in:
                 * /sdcard/odk/instances/[current instnace]/something.jpg so we move it there before
                 * inserting it into the content provider. Once the android image capture bug gets
                 * fixed, (read, we move on from Android 1.6) we want to handle images the audio and
                 * video
                 */
                // The intent is empty, but we know we saved the image to the temp file
                // BEGIN custom
//                File fi = new File(Collect.TMPFILE_PATH);
                File fi = new File(FileUtilsExtended.EXTERNAL_CACHE + File.separator + FileUtilsExtended.CAPTURED_IMAGE_FILE);
                // END custom

                String mInstanceFolder =
                    mInstancePath.substring(0, mInstancePath.lastIndexOf("/") + 1);
                String s = mInstanceFolder + "/" + System.currentTimeMillis() + ".jpg";

                File nf = new File(s);
                if (!fi.renameTo(nf)) {
                    Log.e(t, "Failed to rename " + fi.getAbsolutePath());
                } else {
                    Log.i(t, "renamed " + fi.getAbsolutePath() + " to " + nf.getAbsolutePath());
                }

                // Add the new image to the Media content provider so that the
                // viewing is fast in Android 2.0+
                values = new ContentValues(6);
                values.put(Images.Media.TITLE, nf.getName());
                values.put(Images.Media.DISPLAY_NAME, nf.getName());
                values.put(Images.Media.DATE_TAKEN, System.currentTimeMillis());
                values.put(Images.Media.MIME_TYPE, "image/jpeg");
                values.put(Images.Media.DATA, nf.getAbsolutePath());

                imageURI = getContentResolver().insert(Images.Media.EXTERNAL_CONTENT_URI, values);
                Log.i(t, "Inserting image returned uri = " + imageURI.toString());

                ((ODKView) mCurrentView).setBinaryData(imageURI);
                saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
                refreshCurrentView();
                break;
            case IMAGE_CHOOSER:
                /*
                 * We have a saved image somewhere, but we really want it to be in:
                 * /sdcard/odk/instances/[current instnace]/something.jpg so we move it there before
                 * inserting it into the content provider. Once the android image capture bug gets
                 * fixed, (read, we move on from Android 1.6) we want to handle images the audio and
                 * video
                 */

                // get gp of chosen file
                Uri selectedImage = intent.getData();
                String[] projection = {
                    Images.Media.DATA
                };
                Cursor cursor = managedQuery(selectedImage, projection, null, null, null);
                startManagingCursor(cursor);
                int column_index = cursor.getColumnIndexOrThrow(Images.Media.DATA);
                cursor.moveToFirst();
                String sourceImagePath = cursor.getString(column_index);

                // Copy file to sdcard
                String mInstanceFolder1 =
                    mInstancePath.substring(0, mInstancePath.lastIndexOf("/") + 1);
                String destImagePath = mInstanceFolder1 + "/" + System.currentTimeMillis() + ".jpg";

                File source = new File(sourceImagePath);
                File newImage = new File(destImagePath);
                FileUtils.copyFile(source, newImage);

                if (newImage.exists()) {
                    // Add the new image to the Media content provider so that the
                    // viewing is fast in Android 2.0+
                    values = new ContentValues(6);
                    values.put(Images.Media.TITLE, newImage.getName());
                    values.put(Images.Media.DISPLAY_NAME, newImage.getName());
                    values.put(Images.Media.DATE_TAKEN, System.currentTimeMillis());
                    values.put(Images.Media.MIME_TYPE, "image/jpeg");
                    values.put(Images.Media.DATA, newImage.getAbsolutePath());

                    imageURI =
                        getContentResolver().insert(Images.Media.EXTERNAL_CONTENT_URI, values);
                    Log.i(t, "Inserting image returned uri = " + imageURI.toString());

                    ((ODKView) mCurrentView).setBinaryData(imageURI);
                    saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
                } else {
                    Log.e(t, "NO IMAGE EXISTS at: " + source.getAbsolutePath());
                }
                refreshCurrentView();
                break;
            case AUDIO_CAPTURE:
            case VIDEO_CAPTURE:
            case AUDIO_CHOOSER:
            case VIDEO_CHOOSER:
                // For audio/video capture/chooser, we get the URI from the content provider
                // then the widget copies the file and makes a new entry in the content provider.
                Uri media = intent.getData();
                ((ODKView) mCurrentView).setBinaryData(media);
                saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
                refreshCurrentView();
                break;
            case LOCATION_CAPTURE:
                String sl = intent.getStringExtra(LOCATION_RESULT);
                ((ODKView) mCurrentView).setBinaryData(sl);
                saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
                break;
            case HIERARCHY_ACTIVITY:
                // BEGIN custom
//                // We may have jumped to a new index in hierarchy activity, so refresh
//                refreshCurrentView();
                if (intent == null) {
                    // We may have jumped to a new index in hierarchy activity, so refresh
                    refreshCurrentView();
                } else {
                    String a = intent.getAction();
                    
                    if (a.equals(KEY_NEXT_INSTANCE)) {
                        browseToNextInstance(false);
                    } else if (a.equals(KEY_PREVIOUS_INSTANCE)) {
                        browseToPreviousInstance();
                    } else if (a.equals(KEY_FINISH_ACTIVITY)) {
                        tidyBeforeFinish();
                        finish();
                    }
                }
                // END custom
                break;

        }
    }
    

    /**
     * Refreshes the current view. the controller and the displayed view can get out of sync due to
     * dialogs and restarts caused by screen orientation changes, so they're resynchronized here.
     */
    public void refreshCurrentView() {
        int event = mFormController.getEvent();

        // When we refresh, repeat dialog state isn't maintained, so step back to the previous
        // question.
        // Also, if we're within a group labeled 'field list', step back to the beginning of that
        // group.
        // That is, skip backwards over repeat prompts, groups that are not field-lists,
        // repeat events, and indexes in field-lists that is not the containing group.
        while (event == FormEntryController.EVENT_PROMPT_NEW_REPEAT
                || (event == FormEntryController.EVENT_GROUP && !mFormController
                        .indexIsInFieldList())
                || event == FormEntryController.EVENT_REPEAT
                || (mFormController.indexIsInFieldList() && !(event == FormEntryController.EVENT_GROUP))) {
            event = mFormController.stepToPreviousEvent();
        }
        View current = createView(event);
        showView(current, AnimationType.FADE);

    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.removeItem(MENU_DELETE_REPEAT);
        menu.removeItem(MENU_LANGUAGES);
        menu.removeItem(MENU_HIERARCHY_VIEW);
        menu.removeItem(MENU_SAVE);
        // BEGIN custom
        menu.removeItem(MENU_REMOVE);
        menu.removeItem(MENU_INFO);
        // END custom

        menu.add(0, MENU_SAVE, 0, R.string.save_all_answers).setIcon(
            android.R.drawable.ic_menu_save);
        // BEGIN custom
        menu.add(0, MENU_INFO, 0, R.string.tf_form_details).setIcon(
                android.R.drawable.ic_menu_info_details);        
        // END custom
        menu.add(0, MENU_DELETE_REPEAT, 0, getString(R.string.delete_repeat))
                .setIcon(R.drawable.ic_menu_clear_playlist)
                .setEnabled(mFormController.indexContainsRepeatableGroup() ? true : false);
        menu.add(0, MENU_HIERARCHY_VIEW, 0, getString(R.string.view_hierarchy)).setIcon(
            R.drawable.ic_menu_goto);
        menu.add(0, MENU_LANGUAGES, 0, getString(R.string.change_language))
                .setIcon(R.drawable.ic_menu_start_conversation)
                .setEnabled(
                    (mFormController.getLanguages() == null || mFormController.getLanguages().length == 1) ? false
                            : true);
        // BEGIN custom
        menu.add(0, MENU_REMOVE, 0, getString(R.string.tf_remove_form)).setIcon(R.drawable.ic_menu_delete);
        // END custom
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_LANGUAGES:
                createLanguageDialog();
                return true;
            case MENU_DELETE_REPEAT:
                createDeleteRepeatConfirmDialog();
                return true;
            case MENU_SAVE:
                // don't exit
                saveDataToDisk(DO_NOT_EXIT, isInstanceComplete());
                return true;
            case MENU_HIERARCHY_VIEW:
                if (currentPromptIsQuestion()) {
                    saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
                }
                Intent i = new Intent(this, FormHierarchyActivity.class);
                startActivityForResult(i, HIERARCHY_ACTIVITY);
            // BEGIN custom
                return true;
            case MENU_REMOVE:
                showDialog(REMOVE_DIALOG);
                return true;
            // END custom
        }
        return super.onOptionsItemSelected(item);
    }


    /**
     * @return true if the current View represents a question in the form
     */
    private boolean currentPromptIsQuestion() {
        return (mFormController.getEvent() == FormEntryController.EVENT_QUESTION || mFormController
                .getEvent() == FormEntryController.EVENT_GROUP);
    }


    /**
     * Attempt to save the answer(s) in the current screen to into the data model.
     * 
     * @param evaluateConstraints
     * @return false if any error occurs while saving (constraint violated, etc...), true otherwise.
     */
    private boolean saveAnswersForCurrentScreen(boolean evaluateConstraints) {
        // only try to save if the current event is a question or a field-list group
        if (mFormController.getEvent() == FormEntryController.EVENT_QUESTION
                || (mFormController.getEvent() == FormEntryController.EVENT_GROUP && mFormController
                        .indexIsInFieldList())) {
            HashMap<FormIndex, IAnswerData> answers = ((ODKView) mCurrentView).getAnswers();
            Set<FormIndex> indexKeys = answers.keySet();
            for (FormIndex index : indexKeys) {
                // Within a group, you can only save for question events
                if (mFormController.getEvent(index) == FormEntryController.EVENT_QUESTION) {
                    int saveStatus = saveAnswer(answers.get(index), index, evaluateConstraints);
                    if (evaluateConstraints && saveStatus != FormEntryController.ANSWER_OK) {
                        createConstraintToast(mFormController.getQuestionPrompt(index)
                                .getConstraintText(), saveStatus);
                        return false;
                    }
                } else {
                    Log.w(t,
                        "Attempted to save an index referencing something other than a question: "
                                + index.getReference());
                }
            }
        }
        return true;
    }


    /**
     * Clears the answer on the screen.
     */
    private void clearAnswer(QuestionWidget qw) {
        qw.clearAnswer();
    }


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(0, v.getId(), 0, "Clear Answer");
    }


    @Override
    public boolean onContextItemSelected(MenuItem item) {
        /*
         * We don't have the right view here, so we store the View's ID as the item ID and loop
         * through the possible views to find the one the user clicked on.
         */
        for (QuestionWidget qw : ((ODKView) mCurrentView).getWidgets()) {
            if (item.getItemId() == qw.getId()) {
                createClearDialog(qw);
            }
        }
        return super.onContextItemSelected(item);
    }


    /**
     * If we're loading, then we pass the loading thread to our next instance.
     */
    @Override
    public Object onRetainNonConfigurationInstance() {
        // if a form is loading, pass the loader task
        if (mFormLoaderTask != null && mFormLoaderTask.getStatus() != AsyncTask.Status.FINISHED)
            return mFormLoaderTask;

        // if a form is writing to disk, pass the save to disk task
        if (mSaveToDiskTask != null && mSaveToDiskTask.getStatus() != AsyncTask.Status.FINISHED)
            return mSaveToDiskTask;

        // mFormEntryController is static so we don't need to pass it.
        if (mFormController != null && currentPromptIsQuestion()) {
            saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
        }
        // BEGIN custom
//        return null;
        
        // Avoid refetching documents from database by preserving them
        HashMap<String, Generic> persistentData = new HashMap<String, Generic>();
        persistentData.put(KEY_FORM_DEFINITION, mFormDefinition);
        persistentData.put(KEY_FORM_INSTANCE, mFormInstance);

        return persistentData;
        // END custom        
    }


    /**
     * Creates a view given the View type and an event
     * 
     * @param event
     * @return newly created View
     */
    private View createView(int event) {
        setTitle(getString(R.string.app_name) + " > " + mFormController.getFormTitle());

        switch (event) {
            case FormEntryController.EVENT_BEGINNING_OF_FORM:
                View startView = View.inflate(this, R.layout.form_entry_start, null);
                setTitle(getString(R.string.app_name) + " > " + mFormController.getFormTitle());
                ((TextView) startView.findViewById(R.id.description)).setText(getString(
                    R.string.enter_data_description, mFormController.getFormTitle()));

                Drawable image = null;
                // BEGIN custom
//                String[] projection = {
//                    FormsColumns.FORM_MEDIA_PATH
//                };
//                String selection = FormsColumns.FORM_FILE_PATH + "=?";
//                String[] selectionArgs = {
//                    mFormPath
//                };
//                Cursor c =
//                    managedQuery(FormsColumns.CONTENT_URI, projection, selection, selectionArgs,
//                        null);
//                String mediaDir = null;
//                if (c.getCount() < 1) {
//                    createErrorDialog("form Doesn't exist", true);
//                    return new View(this);
//                } else {
//                    c.moveToFirst();
//                    mediaDir = c.getString(c.getColumnIndex(FormsColumns.FORM_MEDIA_PATH));
//                }
                
                String mediaDir = FileUtilsExtended.FORMS_PATH + File.separator + mFormDefinition.getId() + File.separator + FileUtilsExtended.MEDIA_DIR + File.separator; 
                // END custom

                BitmapDrawable bitImage = null;
                // attempt to load the form-specific logo...
                // this is arbitrarily silly
                bitImage = new BitmapDrawable(mediaDir + "form_logo.png");

                if (bitImage != null && bitImage.getBitmap() != null
                        && bitImage.getIntrinsicHeight() > 0 && bitImage.getIntrinsicWidth() > 0) {
                    image = bitImage;
                }

                if (image == null) {
                    // show the opendatakit zig...
                    image = getResources().getDrawable(R.drawable.opendatakit_zig);
                }

                ((ImageView) startView.findViewById(R.id.form_start_bling)).setImageDrawable(image);
                return startView;
            case FormEntryController.EVENT_END_OF_FORM:
                View endView = View.inflate(this, R.layout.form_entry_end, null);
                ((TextView) endView.findViewById(R.id.description)).setText(getString(
                    R.string.save_enter_data_description, mFormController.getFormTitle()));

                // checkbox for if finished or ready to send
                final CheckBox instanceComplete =
                    ((CheckBox) endView.findViewById(R.id.mark_finished));
                instanceComplete.setChecked(isInstanceComplete());

                // Create 'save for later' button
                ((Button) endView.findViewById(R.id.save_exit_button))
                        .setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                // Form is marked as 'saved' here.
                                saveDataToDisk(EXIT, instanceComplete.isChecked());
                            }
                        });

                return endView;
            case FormEntryController.EVENT_QUESTION:
            case FormEntryController.EVENT_GROUP:
                ODKView odkv = null;
                // should only be a group here if the event_group is a field-list
                try {
                    odkv =
                        new ODKView(this, mFormController.getQuestionPrompts(),
                                mFormController.getGroupsForCurrentIndex());
                    Log.i(t, "created view for group");
                } catch (RuntimeException e) {
                    createErrorDialog(e.getMessage(), EXIT);
                    e.printStackTrace();
                    // this is badness to avoid a crash.
                    // really a next view should increment the formcontroller, create the view
                    // if the view is null, then keep the current view and pop an error.
                    return new View(this);
                }

                // Makes a "clear answer" menu pop up on long-click
                for (QuestionWidget qw : odkv.getWidgets()) {
                    registerForContextMenu(qw);
                }

                return odkv;
            default:
                Log.e(t, "Attempted to create a view that does not exist.");
                return null;
        }
    }


    @Override
    public boolean dispatchTouchEvent(MotionEvent mv) {
        boolean handled = onTouchEvent(mv);
        if (!handled) {
            return super.dispatchTouchEvent(mv);
        }
        return handled; // this is always true
    }


    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        /*
         * constrain the user to only be able to swipe (that causes a view transition) once per
         * screen with the mBeenSwiped variable.
         */
        boolean handled = false;
        if (!mBeenSwiped) {
            switch (mGestureDetector.getGesture(motionEvent)) {
                case SWIPE_RIGHT:
                    mBeenSwiped = true;
                    showPreviousView();
                    handled = true;
                    break;
                case SWIPE_LEFT:
                    mBeenSwiped = true;
                    showNextView();
                    handled = true;
                    break;
            }
        }
        return handled;
    }


    /**
     * Determines what should be displayed on the screen. Possible options are: a question, an ask
     * repeat dialog, or the submit screen. Also saves answers to the data model after checking
     * constraints.
     */
    private void showNextView() {
        if (currentPromptIsQuestion()) {
            if (!saveAnswersForCurrentScreen(EVALUATE_CONSTRAINTS)) {
                // A constraint was violated so a dialog should be showing.
                return;
            }
        }

        if (mFormController.getEvent() != FormEntryController.EVENT_END_OF_FORM) {
            int event;
            group_skip: do {
                event = mFormController.stepToNextEvent(FormController.STEP_INTO_GROUP);
                switch (event) {
                    case FormEntryController.EVENT_QUESTION:
                    case FormEntryController.EVENT_END_OF_FORM:
                        View next = createView(event);
                        showView(next, AnimationType.RIGHT);
                        break group_skip;
                    case FormEntryController.EVENT_PROMPT_NEW_REPEAT:
                        createRepeatDialog();
                        break group_skip;
                    case FormEntryController.EVENT_GROUP:
                        if (mFormController.indexIsInFieldList()) {
                            View nextGroupView = createView(event);
                            showView(nextGroupView, AnimationType.RIGHT);
                            break group_skip;
                        }
                        // otherwise it's not a field-list group, so just skip it
                        break;
                    case FormEntryController.EVENT_REPEAT:
                        Log.i(t, "repeat: " + mFormController.getFormIndex().getReference());
                        // skip repeats
                        break;
                    case FormEntryController.EVENT_REPEAT_JUNCTURE:
                        Log.i(t, "repeat juncture: "
                                + mFormController.getFormIndex().getReference());
                        // skip repeat junctures until we implement them
                        break;
                    default:
                        Log.w(t,
                            "JavaRosa added a new EVENT type and didn't tell us... shame on them.");
                        break;
                }
            } while (event != FormEntryController.EVENT_END_OF_FORM);

        } else {
            mBeenSwiped = false;
        }
    }


    /**
     * Determines what should be displayed between a question, or the start screen and displays the
     * appropriate view. Also saves answers to the data model without checking constraints.
     */
    private void showPreviousView() {
        // The answer is saved on a back swipe, but question constraints are ignored.
        if (currentPromptIsQuestion()) {
            saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
        }

        if (mFormController.getEvent() != FormEntryController.EVENT_BEGINNING_OF_FORM) {
            int event = mFormController.stepToPreviousEvent();

            while (event != FormEntryController.EVENT_BEGINNING_OF_FORM
                    && event != FormEntryController.EVENT_QUESTION
                    && !(event == FormEntryController.EVENT_GROUP && mFormController
                            .indexIsInFieldList())) {
                event = mFormController.stepToPreviousEvent();
            }
            View next = createView(event);
            showView(next, AnimationType.LEFT);

        } else {
            mBeenSwiped = false;
        }
    }


    /**
     * Displays the View specified by the parameter 'next', animating both the current view and next
     * appropriately given the AnimationType. Also updates the progress bar.
     */
    public void showView(View next, AnimationType from) {
        switch (from) {
            case RIGHT:
                mInAnimation = AnimationUtils.loadAnimation(this, R.anim.push_left_in);
                mOutAnimation = AnimationUtils.loadAnimation(this, R.anim.push_left_out);
                break;
            case LEFT:
                mInAnimation = AnimationUtils.loadAnimation(this, R.anim.push_right_in);
                mOutAnimation = AnimationUtils.loadAnimation(this, R.anim.push_right_out);
                break;
            case FADE:
                mInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in);
                mOutAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_out);
                break;
        }

        if (mCurrentView != null) {
            mCurrentView.startAnimation(mOutAnimation);
            mRelativeLayout.removeView(mCurrentView);
        }

        mInAnimation.setAnimationListener(this);

        RelativeLayout.LayoutParams lp =
            new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);

        mCurrentView = next;
        mRelativeLayout.addView(mCurrentView, lp);

        mCurrentView.startAnimation(mInAnimation);
        if (mCurrentView instanceof ODKView)
            ((ODKView) mCurrentView).setFocus(this);
        else {
            InputMethodManager inputManager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(mCurrentView.getWindowToken(), 0);
        }
    }


    // Hopefully someday we can use managed dialogs when the bugs are fixed
    /*
     * Ideally, we'd like to use Android to manage dialogs with onCreateDialog() and
     * onPrepareDialog(), but dialogs with dynamic content are broken in 1.5 (cupcake). We do use
     * managed dialogs for our static loading ProgressDialog. The main issue we noticed and are
     * waiting to see fixed is: onPrepareDialog() is not called after a screen orientation change.
     * http://code.google.com/p/android/issues/detail?id=1639
     */

    //
    /**
     * Creates and displays a dialog displaying the violated constraint.
     */
    private void createConstraintToast(String constraintText, int saveStatus) {
        switch (saveStatus) {
            case FormEntryController.ANSWER_CONSTRAINT_VIOLATED:
                if (constraintText == null) {
                    constraintText = getString(R.string.invalid_answer_error);
                }
                break;
            case FormEntryController.ANSWER_REQUIRED_BUT_EMPTY:
                constraintText = getString(R.string.required_answer_error);
                break;
        }

        showCustomToast(constraintText);
        mBeenSwiped = false;
    }


    /**
     * Creates a toast with the specified message.
     * 
     * @param message
     */
    private void showCustomToast(String message) {
        LayoutInflater inflater =
            (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View view = inflater.inflate(R.layout.toast_view, null);

        // set the text in the view
        TextView tv = (TextView) view.findViewById(R.id.message);
        tv.setText(message);

        Toast t = new Toast(this);
        t.setView(view);
        t.setDuration(Toast.LENGTH_SHORT);
        t.setGravity(Gravity.CENTER, 0, 0);
        t.show();
    }


    /**
     * Creates and displays a dialog asking the user if they'd like to create a repeat of the
     * current group.
     */
    private void createRepeatDialog() {
        mAlertDialog = new AlertDialog.Builder(this).create();
        mAlertDialog.setIcon(android.R.drawable.ic_dialog_info);
        DialogInterface.OnClickListener repeatListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON1: // yes, repeat
                        mFormController.newRepeat();
                        showNextView();
                        break;
                    case DialogInterface.BUTTON2: // no, no repeat
                        showNextView();
                        break;
                }
            }
        };
        if (mFormController.getLastRepeatCount() > 0) {
            mAlertDialog.setTitle(getString(R.string.leaving_repeat_ask));
            mAlertDialog.setMessage(getString(R.string.add_another_repeat,
                mFormController.getLastGroupText()));
            mAlertDialog.setButton(getString(R.string.add_another), repeatListener);
            mAlertDialog.setButton2(getString(R.string.leave_repeat_yes), repeatListener);

        } else {
            mAlertDialog.setTitle(getString(R.string.entering_repeat_ask));
            mAlertDialog.setMessage(getString(R.string.add_repeat,
                mFormController.getLastGroupText()));
            mAlertDialog.setButton(getString(R.string.entering_repeat), repeatListener);
            mAlertDialog.setButton2(getString(R.string.add_repeat_no), repeatListener);
        }
        mAlertDialog.setCancelable(false);
        mAlertDialog.show();
        mBeenSwiped = false;
    }


    /**
     * Creates and displays dialog with the given errorMsg.
     */
    private void createErrorDialog(String errorMsg, final boolean shouldExit) {
        mAlertDialog = new AlertDialog.Builder(this).create();
        mAlertDialog.setIcon(android.R.drawable.ic_dialog_alert);
        mAlertDialog.setTitle(getString(R.string.error_occured));
        mAlertDialog.setMessage(errorMsg);
        DialogInterface.OnClickListener errorListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON1:
                        if (shouldExit) {
                            finish();
                        }
                        break;
                }
            }
        };
        mAlertDialog.setCancelable(false);
        mAlertDialog.setButton(getString(R.string.ok), errorListener);
        mAlertDialog.show();
    }


    /**
     * Creates a confirm/cancel dialog for deleting repeats.
     */
    private void createDeleteRepeatConfirmDialog() {
        mAlertDialog = new AlertDialog.Builder(this).create();
        mAlertDialog.setIcon(android.R.drawable.ic_dialog_alert);
        String name = mFormController.getLastRepeatedGroupName();
        int repeatcount = mFormController.getLastRepeatedGroupRepeatCount();
        if (repeatcount != -1) {
            name += " (" + (repeatcount + 1) + ")";
        }
        mAlertDialog.setTitle(getString(R.string.delete_repeat_ask));
        mAlertDialog.setMessage(getString(R.string.delete_repeat_confirm, name));
        DialogInterface.OnClickListener quitListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON1: // yes
                        mFormController.deleteRepeat();
                        showPreviousView();
                        break;
                    case DialogInterface.BUTTON2: // no
                        break;
                }
            }
        };
        mAlertDialog.setCancelable(false);
        mAlertDialog.setButton(getString(R.string.discard_group), quitListener);
        mAlertDialog.setButton2(getString(R.string.delete_repeat_no), quitListener);
        mAlertDialog.show();
    }


    /**
     * Called during a 'save and exit' command. The form is not 'done' here.
     */
    private boolean saveDataToDisk(boolean exit, boolean complete) {
        // save current answer
        if (!saveAnswersForCurrentScreen(EVALUATE_CONSTRAINTS)) {
            Toast.makeText(this, getString(R.string.data_saved_error), Toast.LENGTH_SHORT).show();
            return false;
        }
        
        Log.e("Carl", "what?  intent? " + getIntent());
        if (getIntent() != null) {
            Log.e("Carl", "what?  data? " + getIntent().getData());
        } else {
            Log.e("Carl", "but no data");
        }
        mSaveToDiskTask = new SaveToDiskTask(getIntent().getData());
        mSaveToDiskTask.setFormSavedListener(this);
        mSaveToDiskTask.setExportVars(exit, complete);
        mSaveToDiskTask.execute();
        showDialog(SAVING_DIALOG);

        return true;
    }


    /**
     * Create a dialog with options to save and exit, save, or quit without saving
     */
    private void createQuitDialog() {
        String[] items = {
                getString(R.string.keep_changes), getString(R.string.do_not_save)
        };

        mAlertDialog =
            new AlertDialog.Builder(this)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(getString(R.string.quit_application, mFormController.getFormTitle()))
                    .setNeutralButton(getString(R.string.do_not_exit),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {

                                dialog.cancel();

                            }
                        }).setItems(items, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            switch (which) {

                                case 0: // save and exit
                                    saveDataToDisk(EXIT, isInstanceComplete());
                                    break;
                                    
                                case 1: // discard changes and exit
                                     // BEGIN custom

//                                    String selection =
//                                        InstanceColumns.INSTANCE_FILE_PATH + " like '"
//                                                + mInstancePath + "'";
//                                    Cursor c =
//                                        FormEntryActivity.this.managedQuery(
//                                            InstanceColumns.CONTENT_URI, null, selection, null,
//                                            null);
//                                    // if it's not already saved, erase everything
//                                    if (c.getCount() < 1) {
//                                        // delete media first
//                                        String instanceFolder =
//                                            mInstancePath.substring(0,
//                                                mInstancePath.lastIndexOf("/") + 1);
//                                        Log.i(t, "attempting to delete: " + instanceFolder);
//                                        String where =
//                                            Images.Media.DATA + " like '" + instanceFolder + "%'";
//                                        int images = getContentResolver().delete(
//                                            Images.Media.EXTERNAL_CONTENT_URI, where, null);
//                                        int audio = getContentResolver().delete(
//                                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, where,
//                                            null);
//                                        int video = getContentResolver().delete(
//                                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, where,
//                                            null);
//                                        Log.i(t, "revmoved from content providers: " + images + " image files, " 
//                                            + audio + " audio files" + " and " 
//                                            + video + " video files.");
//                                        File f = new File(instanceFolder);
//                                        if (f.exists() && f.isDirectory()) {
//                                            for (File del : f.listFiles()) {
//                                                Log.i(t, "deleting file: " + del.getAbsolutePath());
//                                                del.delete();
//                                            }
//                                            f.delete();
//                                        }
//                                    }
                                    
                                    tidyBeforeFinish();
                                    // END custom
  
                                    finish();
                                    break;

                                case 2:// do nothing
                                    break;

                            }
                        }
                    }).create();
        mAlertDialog.show();
    }


    /**
     * Confirm clear answer dialog
     */
    private void createClearDialog(final QuestionWidget qw) {
        mAlertDialog = new AlertDialog.Builder(this).create();
        mAlertDialog.setIcon(android.R.drawable.ic_dialog_alert);

        mAlertDialog.setTitle(getString(R.string.clear_answer_ask));

        String question = qw.getPrompt().getLongText();
        if (question.length() > 50) {
            question = question.substring(0, 50) + "...";
        }

        mAlertDialog.setMessage(getString(R.string.clearanswer_confirm, question));

        DialogInterface.OnClickListener quitListener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON1: // yes
                        clearAnswer(qw);
                        saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
                        break;
                    case DialogInterface.BUTTON2: // no
                        break;
                }
            }
        };
        mAlertDialog.setCancelable(false);
        mAlertDialog.setButton(getString(R.string.discard_answer), quitListener);
        mAlertDialog.setButton2(getString(R.string.clear_answer_no), quitListener);
        mAlertDialog.show();
    }


    /**
     * Creates and displays a dialog allowing the user to set the language for the form.
     */
    private void createLanguageDialog() {
        final String[] languages = mFormController.getLanguages();
        int selected = -1;
        if (languages != null) {
            String language = mFormController.getLanguage();
            for (int i = 0; i < languages.length; i++) {
                if (language.equals(languages[i])) {
                    selected = i;
                }
            }
        }
        mAlertDialog =
            new AlertDialog.Builder(this)
                    .setSingleChoiceItems(languages, selected,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                mFormController.setLanguage(languages[whichButton]);
                                dialog.dismiss();
                                if (currentPromptIsQuestion()) {
                                    saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
                                }
                                refreshCurrentView();
                            }
                        })
                    .setTitle(getString(R.string.change_language))
                    .setNegativeButton(getString(R.string.do_not_change),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                            }
                        }).create();
        mAlertDialog.show();
    }


    /**
     * We use Android's dialog management for loading/saving progress dialogs
     */
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case PROGRESS_DIALOG:
                mProgressDialog = new ProgressDialog(this);
                DialogInterface.OnClickListener loadingButtonListener =
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            mFormLoaderTask.setFormLoaderListener(null);
                            mFormLoaderTask.cancel(true);
                            finish();
                        }
                    };
                mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
                mProgressDialog.setTitle(getString(R.string.loading_form));
                mProgressDialog.setMessage(getString(R.string.please_wait));
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                mProgressDialog.setButton(getString(R.string.cancel_loading_form),
                    loadingButtonListener);
                return mProgressDialog;
            case SAVING_DIALOG:
                mProgressDialog = new ProgressDialog(this);
                DialogInterface.OnClickListener savingButtonListener =
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            mSaveToDiskTask.setFormSavedListener(null);
                            mSaveToDiskTask.cancel(true);
                        }
                    };
                mProgressDialog.setIcon(android.R.drawable.ic_dialog_info);
                mProgressDialog.setTitle(getString(R.string.saving_form));
                mProgressDialog.setMessage(getString(R.string.please_wait));
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                mProgressDialog.setButton(getString(R.string.cancel), savingButtonListener);
                mProgressDialog.setButton(getString(R.string.cancel_saving_form),
                    savingButtonListener);
                return mProgressDialog;
                
            // BEGIN custom
            case REMOVE_DIALOG:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                Dialog dialog = null;
                String message;
                
                if (isInstanceComplete()) 
                    message = getString(R.string.tf_confirm_complete_instance_removal_dialog_msg);
                else
                    message = getString(R.string.tf_confirm_instance_removal_dialog_msg);
                
                builder
                    .setCancelable(false)
                    .setIcon(R.drawable.ic_dialog_info)
                    .setMessage(message);
                
                builder.setPositiveButton(getString(R.string.tf_remove), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        try {
                            mFormInstance.setStatus(FormInstance.Status.removed);
                            Collect.getInstance().getDbService().getDb().update(mFormInstance);
                            
                            removeDialog(REMOVE_DIALOG);
                            
                            Toast.makeText(getApplicationContext(), getString(R.string.tf_removed_with_param, mFormDefinition.getName()), Toast.LENGTH_SHORT).show();

                            if (Collect.getInstance().getInstanceBrowseList().size() > 1) {
                                browseToNextInstance(true);
                            } else {
                                finish();
                            }
                        } catch (Exception e) {
                            Log.w(Collect.LOGTAG, t + "problem marking form instance document as removed " + e.toString());
                            Toast.makeText(getApplicationContext(), getString(R.string.tf_unable_to_remove_form_instance), Toast.LENGTH_LONG).show();                        
                        }
                    }
                });
                
                builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        removeDialog(REMOVE_DIALOG);
                    }
                });
                
                dialog = builder.create();
                return dialog;
            // END custom
        }
        return null;
    }


    /**
     * Dismiss any showing dialogs that we manually manage.
     */
    private void dismissDialogs() {
        if (mAlertDialog != null && mAlertDialog.isShowing()) {
            mAlertDialog.dismiss();
        }
    }


    @Override
    protected void onPause() {
        dismissDialogs();
        if (currentPromptIsQuestion()) {
            saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
        }
        super.onPause();
    }


    @Override
    protected void onResume() {
        if (mFormLoaderTask != null) {
            mFormLoaderTask.setFormLoaderListener(this);
            if (mFormLoaderTask.getStatus() == AsyncTask.Status.FINISHED) {
                dismissDialog(PROGRESS_DIALOG);
                refreshCurrentView();
            }
        }
        if (mSaveToDiskTask != null) {
            mSaveToDiskTask.setFormSavedListener(this);
        }
        super.onResume();
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                createQuitDialog();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (event.isAltPressed() && !mBeenSwiped) {
                    mBeenSwiped = true;
                    showNextView();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (event.isAltPressed() && !mBeenSwiped) {
                    mBeenSwiped = true;
                    showPreviousView();
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }


    @Override
    protected void onDestroy() {
        if (mFormLoaderTask != null) {
            mFormLoaderTask.setFormLoaderListener(null);
            // We have to call cancel to terminate the thread, otherwise it
            // lives on and retains the FEC in memory.
            // but only if it's done, otherwise the thread never returns
            if (mFormLoaderTask.getStatus() == AsyncTask.Status.FINISHED) {
                mFormLoaderTask.cancel(true);
                mFormLoaderTask.destroy();
            }
        }
        if (mSaveToDiskTask != null) {
            mSaveToDiskTask.setFormSavedListener(null);
            // We have to call cancel to terminate the thread, otherwise it
            // lives on and retains the FEC in memory.
            if (mSaveToDiskTask.getStatus() == AsyncTask.Status.FINISHED) {
                mSaveToDiskTask.cancel(false);
            }
        }

        // BEGIN custom
//        mFormController = null;
//        mInstancePath = null;
        // END custom
        super.onDestroy();

    }


    @Override
    public void onAnimationEnd(Animation arg0) {
        mBeenSwiped = false;
    }


    @Override
    public void onAnimationRepeat(Animation animation) {
        // Added by AnimationListener interface.
    }


    @Override
    public void onAnimationStart(Animation animation) {
        // Added by AnimationListener interface.
    }


    /**
     * loadingComplete() is called by FormLoaderTask once it has finished loading a form.
     */
    @Override
    // BEGIN custom
//    public void loadingComplete(FormController fc) {
    public void loadingComplete(FormController fc, FormDefinition fdd, FormInstance fid) {
        final String tt = t + ": loadingComplete(): ";
        // END custom
        dismissDialog(PROGRESS_DIALOG);

        mFormController = fc;
                
        // BEGIN custom
        mFormDefinition = fdd;
        mFormInstance = fid;
        // END custom

        // Set saved answer path
        if (mInstancePath == null) {

            // BEGIN custom
//            // Create new answer folder.
//            String time =
//                new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
//                        .format(Calendar.getInstance().getTime());
//            String file =
//                mFormPath.substring(mFormPath.lastIndexOf('/') + 1, mFormPath.lastIndexOf('.'));
//            String path = Collect.INSTANCES_PATH + "/" + file + "_" + time;
//            if (FileUtils.createFolder(path)) {
//                mInstancePath = path + "/" + file + "_" + time + ".xml";
//            }
            
            // Create temporary instance & folder
            try {
                fid = new FormInstance();
                fid.setFormId(fdd.getId());
                fid.setStatus(FormInstance.Status.placeholder);
                Collect.getInstance().getDbService().getDb().create(fid);

                String instanceFolder = FileUtilsExtended.INSTANCES_PATH + File.separator + fid.getId();
                FileUtils.createFolder(instanceFolder); 
                mInstancePath = instanceFolder + File.separator + fid.getId() + ".xml";
                mFormInstance = fid;
            } catch (Exception e) {
                Log.e(Collect.LOGTAG, tt + "failed to create temporary instance and/or folder");
                e.printStackTrace();
                createErrorDialog("Unable to create new form placeholder and/or folder. Please report this problem and try again.", EXIT);
            }
            // END custom
        } else {
            // we've just loaded a saved form, so start in the hierarchy view
            Intent i = new Intent(this, FormHierarchyActivity.class);
            // BEGIN custom
//            startActivity(i);
            i.putExtra(FormHierarchyActivity.KEY_AUTOLOAD, true);
            startActivityForResult(i, HIERARCHY_ACTIVITY);
            // END custom
            return; // so we don't show the intro screen before jumping to the hierarchy
        }

        refreshCurrentView();
    }


    /**
     * called by the FormLoaderTask if something goes wrong.
     */
    @Override
    public void loadingError(String errorMsg) {
        dismissDialog(PROGRESS_DIALOG);
        mErrorMessage = errorMsg;
        if (errorMsg != null) {
            createErrorDialog(errorMsg, EXIT);
        } else {
            createErrorDialog(getString(R.string.parse_error), EXIT);
        }
    }


    /**
     * Called by the FormLoaderTask if everything loads correctly.
     */
    @Override
    // BEGIN custom
//    public void savingComplete(int saveStatus) {
    public void savingComplete(int saveStatus, FormInstance fi) {
    // END custom
        dismissDialog(SAVING_DIALOG);
        switch (saveStatus) {
            case SaveToDiskTask.SAVED:
                Toast.makeText(this, getString(R.string.data_saved_ok), Toast.LENGTH_SHORT).show();
                // BEGIN custom
                mFormInstance = fi;
                // END custom
                break;
            case SaveToDiskTask.SAVED_AND_EXIT:
                Toast.makeText(this, getString(R.string.data_saved_ok), Toast.LENGTH_SHORT).show();
                // BEGIN custom
                tidyBeforeFinish();
                // END custom
                finish();
                break;
            case SaveToDiskTask.SAVE_ERROR:
                Toast.makeText(this, getString(R.string.data_saved_error), Toast.LENGTH_LONG)
                        .show();
                break;
            case FormEntryController.ANSWER_CONSTRAINT_VIOLATED:
            case FormEntryController.ANSWER_REQUIRED_BUT_EMPTY:
                refreshCurrentView();
                Toast.makeText(this, saveStatus, Toast.LENGTH_LONG).show();
                break;
        }
    }


    /**
     * Attempts to save an answer to the specified index.
     * 
     * @param answer
     * @param index
     * @param evaluateConstraints
     * @return status as determined in FormEntryController
     */
    public int saveAnswer(IAnswerData answer, FormIndex index, boolean evaluateConstraints) {
        if (evaluateConstraints) {
            return mFormController.answerQuestion(index, answer);
        } else {
            mFormController.saveAnswer(index, answer);
            return FormEntryController.ANSWER_OK;
        }
    }


    /**
     * Checks the database to determine if the current instance being edited has already been
     * 'marked completed'. A form can be 'unmarked' complete and then resaved.
     * 
     * @return true if form has been marked completed, false otherwise.
     */
    private boolean isInstanceComplete() {
        // BEGIN custom
//        boolean complete = false;
//
//        String selection = InstanceColumns.INSTANCE_FILE_PATH + "=?";
//        String[] selectionArgs = {
//            mInstancePath
//        };
//        Cursor c =
//            getContentResolver().query(InstanceColumns.CONTENT_URI, null, selection, selectionArgs,
//                null);
//        startManagingCursor(c);
//        if (c != null && c.getCount() > 0) {
//            c.moveToFirst();
//            String status = c.getString(c.getColumnIndex(InstanceColumns.STATUS));
//            if (InstanceProviderAPI.STATUS_COMPLETE.compareTo(status) == 0) {
//                complete = true;
//            }
//        }
//        return complete;
        
        if (mFormInstance != null)
            return mFormInstance.getStatus().equals(FormInstance.Status.complete);
        else
            return false;
        // END custom

    }

    // BEGIN custom
    private void browseToInstance(String instanceId) 
    {
        Log.v(Collect.LOGTAG, t + ": about to browse to " + instanceId + " using form " + mFormDefinition.getId() + " with list " + Collect.getInstance().getInstanceBrowseList().toString());
        
        tidyBeforeFinish();
        finish();
        
        Intent i = new Intent(this, FormEntryActivity.class);
        i.putStringArrayListExtra(FormEntryActivity.KEY_INSTANCES, Collect.getInstance().getInstanceBrowseList());
        i.putExtra(FormEntryActivity.KEY_FORMPATH, mFormDefinition.getId());        
        i.putExtra(FormEntryActivity.KEY_INSTANCEPATH, instanceId);
        startActivity(i);
    }


    private void browseToNextInstance(boolean removeCurrentInstance) 
    {
        String nextInstanceId;       
        
        if (Collect.getInstance().getInstanceBrowseList().indexOf(mFormInstance.getId()) < Collect.getInstance().getInstanceBrowseList().size() - 1) {                                                                    
            nextInstanceId = Collect.getInstance().getInstanceBrowseList().listIterator(Collect.getInstance().getInstanceBrowseList().indexOf(mFormInstance.getId()) + 1).next();
        } else {
            nextInstanceId = Collect.getInstance().getInstanceBrowseList().get(0);
        }
        
        // For when a user "removes" a form instance
        if (removeCurrentInstance)
            Collect.getInstance().getInstanceBrowseList().remove(mFormInstance.getId());
        
        browseToInstance(nextInstanceId);
    }
    
    
    private void browseToPreviousInstance() 
    {
        String previousInstanceId;
        
        if (Collect.getInstance().getInstanceBrowseList().listIterator(Collect.getInstance().getInstanceBrowseList().indexOf(mFormInstance.getId())).hasPrevious()) {
            previousInstanceId = Collect.getInstance().getInstanceBrowseList().listIterator(Collect.getInstance().getInstanceBrowseList().indexOf(mFormInstance.getId())).previous();
        } else {
            previousInstanceId = Collect.getInstance().getInstanceBrowseList().
            get(Collect.getInstance().getInstanceBrowseList().size() - 1);
        }

        browseToInstance(previousInstanceId);
    }
    
    
    private void tidyBeforeFinish()
    { 
        // Check to see if we need to remove the placeholder document
        if (mFormInstance != null && mFormInstance.getStatus() == FormInstance.Status.placeholder) {
            try {
                mFormInstance = Collect.getInstance().getDbService().getDb().get(FormInstance.class, mFormInstance.getId());

                if (mFormInstance.getStatus() == FormInstance.Status.placeholder) {
                    Log.d(Collect.LOGTAG, t + ": removing placeholder " + mFormInstance.getId());
                    Collect.getInstance().getDbService().getDb().delete(mFormInstance);
                }
            } catch (Exception e) {
                Log.e(Collect.LOGTAG, t + ": unexpected exception while running tidyBeforeFinish()");
                e.printStackTrace();
            }
        }
        
        // Remove the instance directory, if any
        if (mInstancePath != null) {
            String instanceDir = mInstancePath.substring(0, mInstancePath.lastIndexOf("/"));
            Log.d(Collect.LOGTAG, t + ": removing instance directory " + instanceDir);
            FileUtilsExtended.deleteFolder(instanceDir);
        }
    }
    // END custom
}
