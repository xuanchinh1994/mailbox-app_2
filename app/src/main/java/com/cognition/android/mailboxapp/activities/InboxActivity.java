package com.cognition.android.mailboxapp.activities;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.cognition.android.mailboxapp.DecoderWrapper;
import com.cognition.android.mailboxapp.R;
import com.cognition.android.mailboxapp.models.Message;
import com.cognition.android.mailboxapp.utils.EndlessRecyclerViewScrollListener;
import com.cognition.android.mailboxapp.utils.MessagesAdapter;
import com.cognition.android.mailboxapp.utils.Utils;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.raizlabs.android.dbflow.sql.language.Delete;
import com.raizlabs.android.dbflow.sql.language.SQLite;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pub.devrel.easypermissions.EasyPermissions;

import static com.cognition.android.mailboxapp.activities.MainActivity.PREF_ACCOUNT_NAME;
import static com.cognition.android.mailboxapp.activities.MainActivity.REQUEST_AUTHORIZATION;
import static com.cognition.android.mailboxapp.activities.MainActivity.REQUEST_GOOGLE_PLAY_SERVICES;
import static com.cognition.android.mailboxapp.activities.MainActivity.SCOPES;
import static com.cognition.android.mailboxapp.activities.MainActivity.TAG;
import static com.google.android.gms.common.internal.safeparcel.SafeParcelable.NULL;

public class InboxActivity extends AppCompatActivity {

    CoordinatorLayout lytParent;
    Toolbar toolbar;
    SwipeRefreshLayout refreshMessages;
    RecyclerView listMessages;
    FloatingActionButton fabCompose;

    List<Message> messageList;
    MessagesAdapter messagesAdapter;

    GoogleAccountCredential mCredential;
    Gmail mService;
    SharedPreferences sharedPref;
    Utils mUtils;

    String pageToken = null;
    boolean isFetching = false;

    // Load library
    static {
        System.loadLibrary("bpg_decoder");
    };

    public static byte[] toByteArray(InputStream input) throws IOException
    {
        byte[] buffer = new byte[1024];
        int bytesRead;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while ((bytesRead = input.read(buffer)) != -1)
        {
            output.write(buffer, 0, bytesRead);
        }
        return output.toByteArray();
    }

    public Bitmap getDecodedBitmap(String resourceId){
        Bitmap bm = null;
//        InputStream is = getResources().openRawResource(resourceId);
        Base64 base64Url = new Base64(true);
//        byte[] fileByteArray = base64Url.decode(resourceId);
        byte[] byteArray = base64Url.decode(resourceId);
//            byte[] byteArray = toByteArray(is);
        byte[] decBuffer = null;
        int decBufferSize = 0;
        decBuffer = DecoderWrapper.decodeBuffer(byteArray, byteArray.length);
        decBufferSize = decBuffer.length;
        if(decBuffer != null){
            bm = BitmapFactory.decodeByteArray(decBuffer, 0, decBufferSize);
        }
        return bm;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inbox);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
        mService = null;
        sharedPref = InboxActivity.this.getSharedPreferences(getString(R.string.preferences_file_name), Context.MODE_PRIVATE);
        mUtils = new Utils(InboxActivity.this);

        String accountName = sharedPref.getString(PREF_ACCOUNT_NAME, null);
        if (accountName != null) {
            mCredential.setSelectedAccountName(accountName);

            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.gmail.Gmail.Builder(
                    transport, jsonFactory, mCredential)
                    .setApplicationName("SMART DOOR BELL")
                    .build();

        } else {
            startActivity(new Intent(InboxActivity.this, MainActivity.class));
            ActivityCompat.finishAffinity(InboxActivity.this);
        }

        messageList = new ArrayList<>();
        messagesAdapter = new MessagesAdapter(InboxActivity.this, messageList);

        initViews();
        getMessagesFromDB();
    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     *
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode  code indicating the result of the incoming
     *                    activity result.
     * @param data        Intent (containing result data) returned by incoming
     *                    activity result.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    if (!isFetching && mUtils.isDeviceOnline()) {
                        getMessagesFromDB();
                    } else
                        mUtils.showSnackbar(lytParent, getString(R.string.device_is_offline));
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(InboxActivity.this);
                    builder.setMessage(R.string.app_requires_auth);
                    builder.setPositiveButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            ActivityCompat.finishAffinity(InboxActivity.this);
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.show();
                }
                break;
        }
    }

    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     *
     * @param requestCode  The request code passed in
     *                     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    /**
     * Initialize the views
     */
    private void initViews() {
        lytParent = findViewById(R.id.lytParent);
        toolbar = findViewById(R.id.toolbar);
        refreshMessages = findViewById(R.id.refreshMessages);
        listMessages = findViewById(R.id.listMessages);
//        fabCompose = findViewById(R.id.fabCompose);

        toolbar.inflateMenu(R.menu.menu_inbox);

//        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
//        SearchView searchView = (SearchView) toolbar.getMenu().findItem(R.id.action_search).getActionView();
//        searchView.setQueryHint(getString(R.string.search));
//        searchView.setSearchableInfo(searchManager != null ? searchManager.getSearchableInfo(getComponentName()) : null);
//        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
//            @Override
//            public boolean onQueryTextSubmit(String query) {
//                messagesAdapter.getFilter().filter(query);
//
//                return true;
//            }
//
//            @Override
//            public boolean onQueryTextChange(String newText) {
//                messagesAdapter.getFilter().filter(newText);
//
//                return true;
//            }
//        });

        refreshMessages.setColorSchemeResources(R.color.colorPrimary);
        refreshMessages.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (!isFetching && mUtils.isDeviceOnline()) {
                    getMessagesFromDB();
                } else
                    mUtils.showSnackbar(lytParent, getString(R.string.device_is_offline));
            }
        });

        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(InboxActivity.this);
        listMessages.setLayoutManager(mLayoutManager);
        listMessages.setItemAnimator(new DefaultItemAnimator());
        listMessages.addOnScrollListener(new EndlessRecyclerViewScrollListener((LinearLayoutManager) mLayoutManager) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                if (!isFetching && mUtils.isDeviceOnline())
                    new GetEmailsTask(false).execute();
            }
        });
        listMessages.setAdapter(messagesAdapter);

//        fabCompose.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                startActivity(new Intent(InboxActivity.this, ComposeActivity.class));
//            }
//        });
    }

    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     *
     * @param connectionStatusCode code describing the presence (or lack of)
     *                             Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                InboxActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    /**
     * Get cached emails
     */
    private void getMessagesFromDB() {
        InboxActivity.this.refreshMessages.setRefreshing(true);
        InboxActivity.this.messageList.clear();
        InboxActivity.this.messageList.addAll(SQLite.select().from(Message.class).queryList());
        InboxActivity.this.messagesAdapter.notifyDataSetChanged();
        InboxActivity.this.refreshMessages.setRefreshing(false);

        if (mUtils.isDeviceOnline())
            new GetEmailsTask(true).execute();
        else
            mUtils.showSnackbar(lytParent, getString(R.string.device_is_offline));
    }

    /**
     * Get emails in the background
     */
    @SuppressLint("StaticFieldLeak")
    private class GetEmailsTask extends AsyncTask<Void, Void, List<Message>> {

        private int itemCount = 0;
        private boolean clear;
        private Exception mLastError = null;
        private String attId;
        private String stringData;


        GetEmailsTask(boolean clear) {
            this.clear = clear;
        }

        @Override
        protected List<Message> doInBackground(Void... voids) {
            isFetching = true;
            List<Message> messageListReceived = null;

            if (clear) {
                Delete.table(Message.class);
                InboxActivity.this.pageToken = null;
            }

            try {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        InboxActivity.this.refreshMessages.setRefreshing(true);
                    }
                });
                String user = "me";
                String query = "in:inbox subject:[SMART_DOORBEEL]";
                ListMessagesResponse messageResponse = mService.users().messages().list(user).setQ(query).setMaxResults(20L).setPageToken(InboxActivity.this.pageToken).execute();
                InboxActivity.this.pageToken = messageResponse.getNextPageToken();

                messageListReceived = new ArrayList<>();
                List<com.google.api.services.gmail.model.Message> receivedMessages = messageResponse.getMessages();
                for (com.google.api.services.gmail.model.Message message : receivedMessages) {
                    com.google.api.services.gmail.model.Message actualMessage = mService.users().messages().get(user, message.getId()).execute();

                    Map<String, String> headers = new HashMap<>();
                    for (MessagePartHeader messagePartHeader : actualMessage.getPayload().getHeaders())
                        headers.put(
                                messagePartHeader.getName(), messagePartHeader.getValue()
                        );

                    JSONObject actualMessageJSON = new JSONObject(actualMessage.getPayload());
                    stringData = null;
                    if (actualMessageJSON.has("parts")){
                        List<MessagePart> parts = actualMessage.getPayload().getParts();
                        for (MessagePart part : parts) {
                            if (part.getFilename() != null && part.getFilename().length() > 0) {
                                String filename = part.getFilename();
                                String attId = part.getBody().getAttachmentId();
                                MessagePartBody attachPart = mService.users().messages().attachments().
                                        get(user, actualMessage.getId(), attId).execute();
                                stringData = attachPart.getData();
                            }
                        }
                    }

                    Message newMessage = new Message(
                            actualMessage.getId(),
                            actualMessage.getPayload().getBody().getAttachmentId(),
                            stringData,
                            actualMessage.getLabelIds(),
                            actualMessage.getSnippet(),
                            actualMessage.getPayload().getMimeType(),
                            headers,
                            actualMessage.getPayload().getParts(),
                            actualMessage.getInternalDate(),
                            InboxActivity.this.mUtils.getRandomMaterialColor(),
                            actualMessage.getPayload()
                    );

                    newMessage.save();
                    messageListReceived.add(newMessage);

                    itemCount++;
                }
            } catch (Exception e) {
                Log.w(TAG, e);
                mLastError = e;
                cancel(true);
            }

            return messageListReceived;
        }

        @Override
        protected void onPostExecute(List<Message> output) {
            isFetching = false;

            if (output != null && output.size() != 0) {
                if (clear) {
                    InboxActivity.this.messageList.clear();
                    InboxActivity.this.messageList.addAll(output);
                    InboxActivity.this.messagesAdapter.notifyDataSetChanged();
                } else {
                    int listSize = InboxActivity.this.messageList.size();
                    InboxActivity.this.messageList.addAll(output);
                    InboxActivity.this.messagesAdapter.notifyItemRangeInserted(listSize, itemCount);
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        InboxActivity.this.refreshMessages.setRefreshing(false);
                    }
                });
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        InboxActivity.this.refreshMessages.setRefreshing(false);
                    }
                });
                InboxActivity.this.mUtils.showSnackbar(lytParent, getString(R.string.fetch_failed));
            }
        }

        @Override
        protected void onCancelled() {
            isFetching = false;

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    InboxActivity.this.refreshMessages.setRefreshing(false);
                }
            });
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            REQUEST_AUTHORIZATION);
                } else {
                    mUtils.showSnackbar(lytParent, getString(R.string.an_error_occurred));
                }
            } else {
                mUtils.showSnackbar(lytParent, getString(R.string.an_error_occurred));
            }
        }

    }
}
