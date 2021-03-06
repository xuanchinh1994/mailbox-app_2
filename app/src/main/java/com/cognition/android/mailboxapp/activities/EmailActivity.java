package com.cognition.android.mailboxapp.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.LinearLayoutCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;

import com.cognition.android.mailboxapp.DecoderWrapper;
import com.cognition.android.mailboxapp.R;
import com.cognition.android.mailboxapp.models.Message;
import com.cognition.android.mailboxapp.models.Message_Table;
import com.cognition.android.mailboxapp.utils.Utils;

import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.transitionseverywhere.TransitionManager;
import com.wang.avi.AVLoadingIndicatorView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Locale;

public class EmailActivity extends AppCompatActivity  {

    CoordinatorLayout lytParent;
    Toolbar toolbar;
    AppCompatTextView txtSubject, txtFromPreview, txtFrom, txtDate;
    ConstraintLayout lytFromPreviewParent;
    WebView myWebView;
    AVLoadingIndicatorView avLoadingIndicatorView;
    LinearLayoutCompat lytError;
    AppCompatButton btnRetry;
    ImageView imageView;
    String attachmentData;

    Utils mUtils;
    Message mMessage = null;

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
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email);

        mUtils = new Utils(EmailActivity.this);

        int messageId = getIntent().getIntExtra("messageId", -1);
        attachmentData = getIntent().getStringExtra("attachmentData");
        mMessage = SQLite.select().from(Message.class).where(Message_Table.id.eq(messageId)).querySingle();

        if (mMessage != null) {
            initViews();
            setUpWebView();
        } else
            onBackPressed();
    }

    /**
     * Initialize views
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void initViews() {
        lytParent = findViewById(R.id.lytParent);
        toolbar = findViewById(R.id.toolbar);
        txtSubject = findViewById(R.id.txtSubject);
        txtFromPreview = findViewById(R.id.txtFromPreview);
        txtFrom = findViewById(R.id.txtFrom);
        txtDate = findViewById(R.id.txtDate);
        lytFromPreviewParent = findViewById(R.id.lytFromPreviewParent);
        myWebView = findViewById(R.id.webView);
        avLoadingIndicatorView = findViewById(R.id.aviLoadingIndicator);
        lytError = findViewById(R.id.lytError);
        btnRetry = findViewById(R.id.btnRetry);
        imageView  = findViewById(R.id.imageInMail);

        toolbar.setTitle(mMessage.getSubject());
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        txtSubject.setText(mMessage.getSubject());
        txtFromPreview.setText(mMessage.getFrom().substring(0, 1).toUpperCase(Locale.ENGLISH));
        txtFrom.setText(mMessage.getFrom());
        txtDate.setText(mUtils.timestampToDate(mMessage.getTimestamp()));

        android.graphics.drawable.GradientDrawable gradientDrawable = (android.graphics.drawable.GradientDrawable) lytFromPreviewParent.getBackground();
        gradientDrawable.setColor(mMessage.getColor());

        if (attachmentData!= null){
            Bitmap bm = getDecodedBitmap(attachmentData);
            if(bm != null) {
                imageView.setImageBitmap(bm);
            }
        }

        WebSettings myWebSettings = myWebView.getSettings();
        myWebSettings.setJavaScriptEnabled(true);
        myWebSettings.setUseWideViewPort(true);
        myWebSettings.setSupportZoom(true);
        myWebSettings.setBuiltInZoomControls(true);
        myWebSettings.setDisplayZoomControls(false);
        myWebSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        myWebSettings.setDomStorageEnabled(true);
        myWebView.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
        myWebView.setScrollbarFadingEnabled(true);
        myWebView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                TransitionManager.beginDelayedTransition(lytParent);
                lytError.setVisibility(View.GONE);
                avLoadingIndicatorView.smoothToHide();
                myWebView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onReceivedError(WebView view, int statuscode, String description, String url) {
                super.onReceivedError(view, statuscode, description, url);

                Log.e(MainActivity.TAG, String.format("code: %d\tdescription: %s\turl: %s", statuscode, description, url));

                TransitionManager.beginDelayedTransition(lytParent);
                myWebView.setVisibility(View.GONE);
                lytError.setVisibility(View.VISIBLE);
                avLoadingIndicatorView.smoothToHide();
            }

            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);

                Log.e(MainActivity.TAG, String.format("code: %d\tdescription: %s\turl: %s", error.getErrorCode(), error.getDescription(), request.getUrl()));

                TransitionManager.beginDelayedTransition(lytParent);
                myWebView.setVisibility(View.GONE);
                lytError.setVisibility(View.VISIBLE);
                avLoadingIndicatorView.smoothToHide();
            }

            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                super.onReceivedHttpError(view, request, response);

                Log.e(MainActivity.TAG, String.format("code: %d\tdescription: %s\turl: %s", response.getStatusCode(), response.getReasonPhrase(), request.getUrl()));

                TransitionManager.beginDelayedTransition(lytParent);
                myWebView.setVisibility(View.GONE);
                lytError.setVisibility(View.VISIBLE);
                avLoadingIndicatorView.smoothToHide();
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                super.onReceivedSslError(view, handler, error);
                handler.proceed();

                Log.e(MainActivity.TAG, error.toString());

            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
                return true;
            }

            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(request.getUrl());
                startActivity(i);
                return true;
            }

        });

        btnRetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setUpWebView();
            }
        });
    }

    /**
     * Prepare the webview to display the email
     */
    private void setUpWebView() {
        TransitionManager.beginDelayedTransition(lytParent);
        myWebView.setVisibility(View.GONE);
        lytError.setVisibility(View.GONE);
        avLoadingIndicatorView.smoothToShow();

        try {
            JSONObject parentPart = new JSONObject(mMessage.getParentPartJson());

            if (parentPart.getJSONObject("body").getInt("size") != 0) {
                byte[] dataBytes = Base64.decodeBase64(parentPart.getJSONObject("body").getString("data"));
                String data = new String(dataBytes, "UTF-8");

                myWebView.loadData(data, mMessage.getMimetype(), "UTF-8");
                Log.d(MainActivity.TAG, mMessage.getMimetype());
            } else {
                JSONArray partsArray = new JSONArray(mMessage.getPartsJson());

                String[] result = getData(partsArray);
                if (result[0] != null && result[1] != null) {
                    myWebView.loadData(result[1], result[0], "UTF-8");
                    Log.d(MainActivity.TAG, result[0]);
                } else {
                    TransitionManager.beginDelayedTransition(lytParent);
                    myWebView.setVisibility(View.GONE);
                    lytError.setVisibility(View.VISIBLE);
                    avLoadingIndicatorView.smoothToHide();
                }
            }
        } catch (Exception e) {
            Log.w(MainActivity.TAG, e);
            e.printStackTrace();

            TransitionManager.beginDelayedTransition(lytParent);
            myWebView.setVisibility(View.GONE);
            lytError.setVisibility(View.VISIBLE);
            avLoadingIndicatorView.smoothToHide();
        }
    }

    /**
     * Get email data
     *
     * @param parts JSONArray
     * @return String[]
     * @throws JSONException                JSONException
     * @throws UnsupportedEncodingException UnsupportedEncodingException
     */
    private String[] getData(JSONArray parts) throws JSONException, UnsupportedEncodingException {
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = new JSONObject(parts.getString(i));
            if (part.has("parts"))
                return getData(new JSONArray(part.getString("parts")));
            else {
                if (part.getString("mimeType").equals("text/html"))
                    return new String[]{
                            part.getString("mimeType"),
                            new String(
                                    Base64.decodeBase64(part.getJSONObject("body").getString("data")),
                                    "UTF-8"
                            )
                    };
            }
        }

        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = new JSONObject(parts.getString(i));
            if (part.getString("mimeType").equals("text/plain"))
                return new String[]{
                        part.getString("mimeType"),
                        new String(
                                Base64.decodeBase64(part.getJSONObject("body").getString("data")),
                                "UTF-8"
                        )
                };
        }

        return new String[]{null, null};
    }
}