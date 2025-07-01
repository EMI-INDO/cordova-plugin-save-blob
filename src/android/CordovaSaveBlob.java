package emi.indo.cordova.plugin.save.blob;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.format.Formatter;
import android.util.Base64;
import android.util.Log;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Created by EMI INDO So on 10/Mar/2025
 */

public class CordovaSaveBlob extends CordovaPlugin {

    private static final String TAG = "CordovaSaveBlob";


    private static final int REQUEST_CODE_OPEN_DOCUMENT_TREE = 1;
    private static final int FILE_SELECT_CODE = 2;
    private static final int PERMISSION_REQUEST_CODE = 3;

    private JSONObject currentPermissionsStatus;
    private CallbackContext currentCallbackContext;
    private String pendingAction;
    private String pendingMimeType;

    private static final int REQUEST_CODE_PICK_MEDIA = 1234;
    
    protected CordovaWebView mCordovaWebView;

    // NOTE
    // image/txt: OK | Video/Audio/PDF: maxSize 20mb, if (file size 20mb+) apk crash or out of memory
    // handle with if(parseFloat(sizeReadable) <20)
    private Boolean isCreateBase64 = false;
    private Boolean isSaveCreateBase64 = false;
    private Boolean isFileCreateBase64 = false;


    @Override
    public void pluginInitialize() { // only cordova android 14+

        mCordovaWebView = webView;
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        currentCallbackContext = callbackContext;
        this.pendingAction = action;
        if ("checkAndRequestPermissions".equals(action)) {
            JSONObject options = args.getJSONObject(0);
            JSONArray permissionsArr = options.optJSONArray("permissions");
            if (permissionsArr == null) {
                String perm = options.optString("permissions");
                permissionsArr = new JSONArray();
                permissionsArr.put(perm);
            }
            this.checkAndRequestPermissions(permissionsArr);
            return true;

        } else if (action.equals("selectFiles")) {
            JSONObject options = args.getJSONObject(0);
            String mimeType = options.optString("mime");
            this.isCreateBase64 = options.optBoolean("isBase64"); // direct response render in html element
            this.pendingMimeType = mimeType;
            try {
                this.selectFile(mimeType);
            } catch (Exception e) {
                this.currentCallbackContext.error("Error: " + e.getMessage());
            }
            return true;

        } else if (action.equals("registerWebRTC")) {
            this.registerWebRTC();
            return true;

        } else if ("selectTargetPath".equals(action)) {
            this.openDocumentTree();
            return true;

        } else if ("conversionSAFUri".equals(action)) {
            JSONObject options = args.getJSONObject(0);
            String uriPath = options.optString("uriPath");
            this.conversionSAFUri(uriPath, callbackContext);
            return true;

        } else if ("downloadBlob".equals(action)) {
            JSONObject options = args.getJSONObject(0);
            String saveToPath = options.optString("saveToPath");
            String base64Data = options.optString("base64Data");
            String filename = options.optString("fileName");
            this.isSaveCreateBase64 = options.optBoolean("isBase64");  // response render in html element
            this.downloadBlob(saveToPath, base64Data, filename, callbackContext);
            return true;

        } else if (action.equals("downloadFile")) {
            JSONObject options = args.getJSONObject(0);
            String fileUrl = options.optString("fileUrl");
            boolean isCreateStringBase64 = options.optBoolean("isBase64", false); // response render in html element
            String saveToPath = options.optString("saveToPath"); // uri SAF: if isCreateStringBase64 = true saveToPath will be ignored
            String fileName = options.optString("fileName");
            this.downloadFile(fileUrl, isCreateStringBase64, saveToPath, fileName, callbackContext);
            return true;
            
        } else if (action.equals("fileToBase64")) {
            JSONObject options = args.getJSONObject(0);
            String filePath = options.optString("filePath");
            isFileCreateBase64 = true;
            this.fileToBase64(filePath, callbackContext); // response render in html element
            return true;
            
        } else if (action.equals("openAppSettings")) {
            // Once the user has set the permissions, they can only be changed manually from the app settings.
            this.openAppSettings();
            return true;

        } else if ("showMediaPicker".equals(action)) {
            JSONObject options = args.getJSONObject(0);
            String mediaType = options.optString("mediaType", "image");
            boolean toBase64 = options.optBoolean("toBase64", false);
            showMediaPicker(mediaType, toBase64, callbackContext);
            return true;

        } else if ("openGallery".equals(action)) {
            JSONObject options = args.getJSONObject(0);
            String mediaType = options.optString( "mediaType", "image");
            openGallery(mediaType);
            return true;
        }

        return false;
    }


    @SuppressLint("IntentReset")
    private void openGallery(String mediaType) {
        cordova.getThreadPool().execute(() -> {
            Intent intent;

            switch (mediaType) {
                case "video":
                    intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
                    break;

                case "audio":
                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("audio/*");
                    break;

                case "pdf":
                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("application/pdf");
                    break;

                case "image":
                default:
                    intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    break;
            }

            cordova.setActivityResultCallback(this);
            cordova.getActivity()
                    .startActivityForResult(intent, REQUEST_CODE_PICK_MEDIA);
        });
    }




    private void showMediaPicker(String mediaType, boolean toBase64, CallbackContext callbackContext) {
        cordova.getThreadPool().execute(() -> {
            try {
                ContentResolver cr = cordova.getActivity().getContentResolver();
                Uri uri;
                String[] projection;
                String selection    = null;
                String[] selArgs    = null;
                String sortOrder;

                switch (mediaType) {
                    case "image":
                        uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                        projection = new String[]{
                                MediaStore.Images.Media._ID,
                                MediaStore.Images.Media.DISPLAY_NAME,
                                MediaStore.Images.Media.MIME_TYPE,
                                MediaStore.Images.Media.DATA,
                                MediaStore.MediaColumns.SIZE,
                                MediaStore.Images.Media.DATE_ADDED
                        };
                        sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";
                        break;

                    case "video":
                        uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                        projection = new String[]{
                                MediaStore.Video.Media._ID,
                                MediaStore.Video.Media.DISPLAY_NAME,
                                MediaStore.Video.Media.MIME_TYPE,
                                MediaStore.Video.Media.DATA,
                                MediaStore.Video.Media.DATE_ADDED,
                                MediaStore.Video.Media.DURATION,
                                MediaStore.MediaColumns.SIZE
                        };
                        sortOrder = MediaStore.Video.Media.DATE_ADDED + " DESC";
                        break;

                    case "audio":
                        uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                        projection = new String[]{
                                MediaStore.Audio.Media._ID,
                                MediaStore.Audio.Media.TITLE,
                                MediaStore.Audio.Media.ARTIST,
                                MediaStore.Audio.Media.ALBUM,
                                MediaStore.Audio.Media.ALBUM_ID,
                                MediaStore.Audio.Media.MIME_TYPE,
                                MediaStore.Audio.Media.DATA,
                                MediaStore.MediaColumns.SIZE,
                                MediaStore.Audio.Media.DURATION,
                                MediaStore.Audio.Media.DATE_ADDED
                        };
                        selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";
                        sortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC";
                        break;

                    case "pdf":
                        // only permissions android.permission.MANAGE_EXTERNAL_STORAGE | or use the selectFiles method
                        uri = MediaStore.Files.getContentUri("external");
                        projection = new String[]{
                                MediaStore.Files.FileColumns._ID,
                                MediaStore.Files.FileColumns.DISPLAY_NAME,
                                MediaStore.Files.FileColumns.MIME_TYPE,
                                MediaStore.Files.FileColumns.DATA,
                                MediaStore.MediaColumns.SIZE,
                                MediaStore.Files.FileColumns.DATE_ADDED
                        };
                        selection = "(" + MediaStore.Files.FileColumns.MIME_TYPE + " LIKE ? OR " +
                                MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE ?)";
                        selArgs = new String[]{ "%pdf%", "%.pdf" };
                        sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC";
                        break;

                    default:
                      //  Log.e(TAG, "Unsupported mediaType: " + mediaType);
                        callbackContext.error("Unsupported mediaType: " + mediaType);
                        return;
                }

                Cursor cursor = cr.query(uri, projection, selection, selArgs, sortOrder);
                if (cursor == null) {
                    callbackContext.error("Failed to query " + mediaType);
                    return;
                }

                JSONArray resultArray = new JSONArray();
                while (cursor.moveToNext()) {
                    JSONObject obj = new JSONObject();
                    long id        = cursor.getLong(cursor.getColumnIndexOrThrow(projection[0]));
                    Uri contentUri= ContentUris.withAppendedId(uri, id);

                    String name   = cursor.getString(cursor.getColumnIndexOrThrow(projection[1]));
                    String mime   = cursor.getString(cursor.getColumnIndexOrThrow(projection[2]));
                    String path   = cursor.getString(cursor.getColumnIndexOrThrow(projection[3]));
                    long size      = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE));
                    long dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED));

                    // human-readable size
                    String humanSize = Formatter.formatShortFileSize(cordova.getActivity(), size);
                    obj.put("humanSize", humanSize);

                    obj.put("id",        id);
                    obj.put("uri",       contentUri.toString());
                    obj.put("name",      name);
                    obj.put("mimeType",  mime);
                    obj.put("path",      path);
                    obj.put("size",       size);
                    obj.put("dateAdded", dateAdded);


                    // Traditional file path
                    String realPath = getRealPathFromUri(contentUri);
                    obj.put("filePath", realPath != null ? realPath : path);

                    // Optional Base64 of the file itself
                    if (toBase64) {
                        try (InputStream is = cr.openInputStream(contentUri);
                             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                            byte[] buf = new byte[4096];
                            int len;
                            while (is != null && (len = is.read(buf)) > 0) {
                                bos.write(buf, 0, len);
                            }
                            String b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
                            obj.put("data", b64);
                        } catch (Exception e) {
                            Log.w(TAG, "Error encoding base64 for id=" + id + ": " + e.getMessage());
                        }
                    }


                    if ("video".equals(mediaType)) {
                        long duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION));
                        obj.put("duration", duration);

                        // human-readable duration mm:ss
                        int totalSec = (int) (duration / 1000);
                        int min = totalSec / 60;
                        int sec = totalSec % 60;
                        String humanDuration = String.format(Locale.getDefault(), "%02d:%02d", min, sec);
                        obj.put("humanDuration", humanDuration);
                        
                    }
                    if ("audio".equals(mediaType)) {
                        obj.put("title",  name);
                        obj.put("artist", cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)));
                        obj.put("album",  cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)));
                        long duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));
                        obj.put("duration", duration);

                        // human-readable duration mm:ss
                        int totalSec = (int) (duration / 1000);
                        int min = totalSec / 60;
                        int sec = totalSec % 60;
                        String humanDuration = String.format(Locale.getDefault(), "%02d:%02d", min, sec);
                        obj.put("humanDuration", humanDuration);

                        // Optional: cover art Base64
                        if (toBase64) {
                            long albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID));
                            Uri artUri   = ContentUris.withAppendedId(
                                    Uri.parse("content://media/external/audio/albumart"),
                                    albumId
                            );
                            try (InputStream is2 = cr.openInputStream(artUri);
                                 ByteArrayOutputStream bos2 = new ByteArrayOutputStream()) {
                                byte[] buf2 = new byte[4096];
                                int len2;
                                while (is2 != null && (len2 = is2.read(buf2)) > 0) {
                                    bos2.write(buf2, 0, len2);
                                }
                                String coverB64 = Base64.encodeToString(bos2.toByteArray(), Base64.NO_WRAP);
                                obj.put("coverData", coverB64);
                            } catch (Exception ignored) { }
                        }
                    }

                    resultArray.put(obj);
                }
                cursor.close();

                callbackContext.success(resultArray);

            } catch (Exception e) {
               // Log.e(TAG, "showMediaPicker error", e);
                callbackContext.error("Error getting " + mediaType + ": " + e.getMessage());
            }
        });
    }

    private String getRealPathFromUri(Uri uri) {
        String realPath = null;
        String[] proj   = { MediaStore.MediaColumns.DATA };
        ContentResolver cr = cordova.getActivity().getContentResolver();
        try (Cursor c = cr.query(uri, proj, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                realPath = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA));
            }
        } catch (Exception e) {
           // Log.w(TAG, "getRealPathFromUri error: " + e.getMessage());
        }
        return realPath;
    }




    private void registerWebRTC() {

        if (mCordovaWebView.getView() instanceof WebView) {
            ((WebView) mCordovaWebView.getView()).setWebChromeClient(new WebChromeClient() {
                @Override
                public void onPermissionRequest(final PermissionRequest request) {
                    cordova.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            request.grant(request.getResources());
                        }
                    });
                }
            });

        }

    }



    private void downloadFile(String fileUrl, boolean isCreateStringBase64, String saveToPath, String fileName, CallbackContext callbackContext) {

        cordova.getThreadPool().execute(() -> {
            HttpURLConnection conn = null;
            InputStream in = null;
            OutputStream out = null;
            ByteArrayOutputStream baos = null;

            try {
                URL url = new URL(fileUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15_000);
                conn.setReadTimeout(15_000);
                conn.connect();

                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    callbackContext.error("Failed to download: HTTP " + code + " " + conn.getResponseMessage());
                    return;
                }

                in = conn.getInputStream();

                if (isCreateStringBase64) {
                    baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        baos.write(buffer, 0, read);
                    }
                    byte[] data = baos.toByteArray();
                    String base64 = Base64.encodeToString(data, Base64.NO_WRAP);
                    JSONObject meta = new JSONObject();
                    try {
                        meta.put("base64", base64);
                        callbackContext.success(meta);
                    } catch (JSONException e) {
                        callbackContext.error("Failed to encode Base64: " + e.getMessage());
                    }
                    return;
                }

                long fileLength = 0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    fileLength = conn.getContentLengthLong();
                }
                Uri targetUri = prepareTargetUri(
                        cordova.getActivity().getContentResolver(),
                        saveToPath,
                        fileName,
                        callbackContext
                );
                if (targetUri == null) return;

                out = cordova.getActivity()
                        .getContentResolver()
                        .openOutputStream(targetUri);

                byte[] buffer = new byte[4096];
                long totalRead = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    totalRead += read;
                    int progress = fileLength > 0
                            ? (int)(totalRead * 100 / fileLength)
                            : -1;
                    PluginResult pr = new PluginResult(PluginResult.Status.OK, progress);
                    pr.setKeepCallback(true);
                    callbackContext.sendPluginResult(pr);
                }

                try {
                    JSONObject meta = getMetadata(targetUri);
                    if (meta != null) {
                        callbackContext.success(meta);
                    } else {
                        callbackContext.error("Failed to retrieve metadata.");
                    }
                } catch (JSONException e) {
                    callbackContext.error("Error getting metadata: " + e.getMessage());
                }

            } catch (Exception e) {
                callbackContext.error("Download failed: " + e.getMessage());
            } finally {
                if (conn  != null) conn.disconnect();
                try { if (in  != null) in.close();  } catch (IOException ignored) {}
                try { if (out != null) out.close(); } catch (IOException ignored) {}
                try { if (baos!= null) baos.close();} catch (IOException ignored) {}
            }
        });
    }




    private Uri prepareTargetUri(ContentResolver cr, String saveToPath, String fileName, CallbackContext callbackContext) {
        try {
            if (saveToPath != null && !saveToPath.isEmpty()) {
                Uri treeUri = Uri.parse(saveToPath);
                int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                cr.takePersistableUriPermission(treeUri, flags);
                DocumentFile pickedDir = DocumentFile.fromTreeUri(cordova.getActivity(), treeUri);
                if (pickedDir == null || !pickedDir.canWrite()) {
                    callbackContext.error("Invalid or non-writable SAF folder.");
                    return null;
                }
                DocumentFile newFile = pickedDir.createFile("application/octet-stream", fileName);
                if (newFile == null) {
                    callbackContext.error("Failed to create file in SAF.");
                    return null;
                }
                return newFile.getUri();
            } else {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                Uri uri = cr.insert(MediaStore.Files.getContentUri("external"), cv);
                if (uri == null) {
                    callbackContext.error("Failed to create file in Downloads.");
                }
                return uri;
            }
        } catch (Exception e) {
            callbackContext.error("File preparation error: " + e.getMessage());
            return null;
        }
    }




    private void openAppSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", cordova.getActivity().getPackageName(), null);
            intent.setData(uri);
            cordova.getActivity().startActivity(intent);
        } catch (ActivityNotFoundException e) {
            currentCallbackContext.error("Unable to open settings");
        }
    }


    private void fileToBase64(final String filePath, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(() -> {
            InputStream inputStream = null;

            try {
                Uri uri = Uri.parse(filePath);
                ContentResolver resolver = this.cordova.getActivity().getContentResolver();
                if ("content".equals(uri.getScheme())) {
                    // SAF URI (content://)
                    inputStream = resolver.openInputStream(uri);
                } else if ("file".equals(uri.getScheme()) || !uri.isAbsolute()) {
                    // file:// URI or traditional path
                    String cleanPath = uri.getPath(); // Handles file:// and raw paths
                    assert cleanPath != null;
                    File file = new File(cleanPath);
                    if (!file.exists()) {
                        callbackContext.error("File not found: " + cleanPath);
                        return;
                    }
                    inputStream = new FileInputStream(file);
                } else {
                    callbackContext.error("Unsupported URI scheme: " + uri.getScheme());
                    return;
                }

                try {
                    JSONObject meta = getMetadata(uri);
                    if (meta != null) {
                        callbackContext.success(meta);
                    } else {
                        callbackContext.error("Failed to retrieve metadata.");
                    }

                } catch (JSONException e) {
                    callbackContext.error("Error getting metadata: " + e.getMessage());
                }

            } catch (Exception e) {
                callbackContext.error("Error processing file: " + e.getMessage());
            } finally {
                try {
                    if (inputStream != null) inputStream.close();
                   // if (baos != null) baos.close();
                } catch (IOException ignored) {}
            }
        });
    }




    @SuppressLint("SdCardPath")
    private void conversionSAFUri(String uriPath, CallbackContext callbackContext) {
        cordova.getThreadPool().execute(() -> {
            try {
                String finalPath;
                if (uriPath.startsWith("content://")) {
                    String lower = uriPath.toLowerCase();

                    if (lower.endsWith(".mp3")
                            || lower.endsWith(".wav")
                            || lower.endsWith(".aac")
                            || lower.endsWith(".ogg")
                            || lower.endsWith(".flac")
                            || lower.endsWith(".m4a")
                            || lower.endsWith(".wma")
                            || lower.endsWith(".opus")
                            || lower.endsWith(".amr")
                            || lower.endsWith(".ac3")
                            || lower.endsWith(".aiff")
                            || lower.endsWith(".mp2")
                            || lower.endsWith(".wv")
                            || lower.endsWith(".alac")
                            || lower.endsWith(".mp4")
                            || lower.endsWith(".m4v")
                            || lower.endsWith(".3gp")
                            || lower.endsWith(".3g2")
                            || lower.endsWith(".mkv")
                            || lower.endsWith(".webm")
                            || lower.endsWith(".avi")
                            || lower.endsWith(".mov")
                            || lower.endsWith(".flv")
                            || lower.endsWith(".mpeg")
                            || lower.endsWith(".mpg")
                            || lower.endsWith(".ts")
                            || lower.endsWith(".wmv")
                            || lower.endsWith(".jpg")
                            || lower.endsWith(".jpeg")
                            || lower.endsWith(".png")
                            || lower.endsWith(".gif")
                            || lower.endsWith(".pdf")
                            || lower.endsWith(".txt")) {
                        finalPath = convertContentUriToFilePathName(uriPath);
                    }

                    else {
                        finalPath = convertContentUriToFilePath(uriPath);
                    }
                }

                else if (uriPath.startsWith("/data/user/0/")) {
                    finalPath = convertDataUriToFilePath(uriPath);
                }

                else {
                    finalPath = uriPath;
                }

                callbackContext.success(finalPath);
            }
            catch (Exception e) {
                callbackContext.error("conversionUri Error : " + e.getMessage());
            }
        });
    }




    @SuppressLint("SdCardPath")
    private String convertContentUriToFilePathName(String uriString) throws IOException {
        if (uriString == null) return null;

        Context ctx = cordova.getContext();
        Uri uri = Uri.parse(uriString);
        String scheme = uri.getScheme();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                    && DocumentsContract.isTreeUri(uri)) {
                String docId = DocumentsContract.getDocumentId(uri);
                String[] split = docId.split(":");
                String vol = split[0];
                String path = split.length > 1 ? split[1] : "";

                String basePath;
                if ("primary".equalsIgnoreCase(vol)) {
                    basePath = Environment.getExternalStorageDirectory().getAbsolutePath();
                } else {
                    basePath = "/storage/" + vol;
                }
                return basePath + (path.startsWith("/") ? path : "/" + path);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                && DocumentsContract.isDocumentUri(ctx, uri)) {
            String auth = uri.getAuthority();
            String docId = DocumentsContract.getDocumentId(uri);
            String[] split = docId.split(":");
            String type = split[0];
            String path = split.length > 1 ? split[1] : "";

            // a) ExternalStorage
            if ("com.android.externalstorage.documents".equals(auth)) {
                String basePath = "primary".equalsIgnoreCase(type)
                        ? Environment.getExternalStorageDirectory().getAbsolutePath()
                        : "/storage/" + type;
                return basePath + (path.startsWith("/") ? path : "/" + path);
            }
            // b) Downloads
            else if ("com.android.providers.downloads.documents".equals(auth)) {
                // is raw:, docId = "raw:/storage/..."
                if (docId.startsWith("raw:")) {
                    return docId.substring(4);
                }

                String displayName = queryDisplayName(ctx, uri);

                String existing = convertContentUriToFilePath(uriString);
                return existing != null ? existing : copyToCacheAndGetPath(ctx, uri, displayName);
            }
            // c) Media (image, audio, video)
            else if ("com.android.providers.media.documents".equals(auth)) {
                String id = split[1];
                Uri mediaUri = null;
                if ("image".equals(type)) mediaUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                else if ("video".equals(type)) mediaUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                else if ("audio".equals(type)) mediaUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

                String sel = "_id=?";
                String[] selArgs = new String[]{ id };
                String fullPath = getDataColumn(ctx, mediaUri, sel, selArgs);
                if (fullPath != null) return fullPath;

                String displayName = queryDisplayName(ctx, uri);
                return copyToCacheAndGetPath(ctx, uri, displayName);
            }
        }

        if (ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(scheme)) {
            String displayName = queryDisplayName(ctx, uri);
            return copyToCacheAndGetPath(ctx, uri, displayName);
        }

        if (ContentResolver.SCHEME_FILE.equalsIgnoreCase(scheme)) {
            return uri.getPath();
        }
        return uriString;
    }



    private String queryDisplayName(Context ctx, Uri uri) {
        Cursor cursor = null;
        final String[] proj = { MediaStore.MediaColumns.DISPLAY_NAME };
        try {
            cursor = ctx.getContentResolver().query(uri, proj, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
                return cursor.getString(idx);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return "tempfile";
    }

    private String copyToCacheAndGetPath(Context ctx, Uri uri, String fileName) {
        File dst = new File(ctx.getCacheDir(), fileName);
        try (InputStream in = ctx.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.flush();
            Log.d("copyToCacheAndGetPath", "dst=" + dst.getAbsolutePath());

            return dst.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }




    private String convertDataUriToFilePath(String sourcePath) throws IOException {
        File src = new File(sourcePath);
        if (!src.exists() || !src.canRead()) {
            throw new IOException("Source cannot be read: " + sourcePath);
        }

        Context ctx = cordova.getContext();
        String fileName = src.getName();
        String lower   = fileName.toLowerCase();

        File publicDir;
        if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".m4a")) {
            publicDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MUSIC);
        }
        else if (lower.endsWith(".mp4") || lower.endsWith(".3gp") || lower.endsWith(".mkv")) {
            publicDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MOVIES);
        }
        else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".gif")) {
            publicDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES);
        }
        else {
            publicDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
        }

        if (!publicDir.exists() && !publicDir.mkdirs()) {
            throw new IOException("Failed to create a public folder: " + publicDir.getAbsolutePath());
        }

        File dst = new File(publicDir, fileName);
        if (dst.exists()) dst.delete();


        currentCallbackContext.success(dst.getAbsolutePath());

        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.flush();
        }

        return dst.getAbsolutePath();

    }




    private String convertContentUriToFilePath(String uriString) {
        if (uriString == null) return null;
        Context ctx = cordova.getContext();

        if (uriString.startsWith("/")) {
            return uriString;
        }
        Uri uri = uriString.startsWith("//")
                ? Uri.parse("content:" + uriString)
                : Uri.parse(uriString);

        String scheme = uri.getScheme();
        if (ContentResolver.SCHEME_FILE.equalsIgnoreCase(scheme)) {
            return uri.getPath();
        }
        if (ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(scheme)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                        && DocumentsContract.isTreeUri(uri)) {
                    String treeId = DocumentsContract.getTreeDocumentId(uri);
                    String[] split = treeId.split(":");
                    String vol = split[0], part = split.length>1? split[1] : "";
                    if ("primary".equalsIgnoreCase(vol)) {
                        return Environment.getExternalStorageDirectory()
                                .getAbsolutePath()
                                + (part.isEmpty()? "" : "/"+part);
                    } else {
                        return "/storage/"+vol + (part.isEmpty()? "" : "/"+part);
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                    && DocumentsContract.isDocumentUri(ctx, uri)) {
                String auth = uri.getAuthority();
                String docId = DocumentsContract.getDocumentId(uri);
                String[] split = docId.split(":");
                String type = split[0], part = split.length>1? split[1] : "";

                if ("com.android.externalstorage.documents".equals(auth)) {
                    if ("primary".equalsIgnoreCase(type)) {
                        return Environment.getExternalStorageDirectory()
                                .getAbsolutePath()
                                + (part.isEmpty()? "" : "/"+part);
                    } else {
                        return "/storage/"+type + (part.isEmpty()? "" : "/"+part);
                    }
                }
                else if ("com.android.providers.downloads.documents".equals(auth)) {
                    if (docId.startsWith("raw:")) {
                        return docId.substring(4);
                    }
                    Uri contentUri = ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"),
                            Long.parseLong(docId));
                    String p = getDataColumn(ctx, contentUri, null, null);
                    if (p != null) return p;
                }
                else if ("com.android.providers.media.documents".equals(auth)) {
                    String id = split[1];
                    Uri mediaUri2 = null;
                    if ("image".equals(type))
                        mediaUri2 = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                    else if ("video".equals(type))
                        mediaUri2 = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                    else if ("audio".equals(type))
                        mediaUri2 = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                    String sel = "_id=?", selArgs[] = { id };
                    String p = getDataColumn(ctx, mediaUri2, sel, selArgs);
                    if (p != null) return p;
                }
            }
            String dataPath = getDataColumn(ctx, uri, null, null);
            if (dataPath != null) return dataPath;
        }
        return uriString;
    }

    private String getDataColumn(Context ctx, Uri uri, String sel, String[] selArgs) {
        Cursor cursor = null;
        final String col = "_data";
        final String[] proj = { col };
        try {
            cursor = ctx.getContentResolver()
                    .query(uri, proj, sel, selArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndexOrThrow(col);
                return cursor.getString(idx);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }



    private void downloadBlob(final String saveToPath, final String base64Data, final String filename, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    String base64Preview = base64Data.substring(0, Math.min(50, base64Data.length()));
                    // Log.d(TAG, "Base64 data preview: " + base64Preview);
                    byte[] fileData = Base64.decode(base64Data, Base64.DEFAULT);
                    if (saveToPath != null) {
                        Uri treeUri = Uri.parse(saveToPath);
                        DocumentFile pickedDir = DocumentFile.fromTreeUri(cordova.getContext(), treeUri);
                        if (pickedDir != null && pickedDir.canWrite()) {
                            DocumentFile newFile = pickedDir.createFile("application/octet-stream", filename);
                            if (newFile != null) {
                                OutputStream out = cordova.getContext().getContentResolver().openOutputStream(newFile.getUri());
                                if (out != null) {
                                    out.write(fileData);
                                    out.close();
                                    try {
                                        JSONObject meta = getMetadata(newFile.getUri());
                                        if (meta != null) {
                                            callbackContext.success(meta);
                                        } else {
                                            callbackContext.error("Failed to retrieve metadata.");
                                        }
                                    } catch (JSONException e) {
                                        callbackContext.error("Error getting metadata: " + e.getMessage());
                                    }
                                } else {
                                    callbackContext.error("Failed to open output stream for the new file.");
                                }
                            } else {
                                callbackContext.error("Failed to create new file in the picked directory.");
                            }
                        } else {
                            callbackContext.error("Picked directory is null or not writable.");
                        }
                    } else {
                        // Using the default Downloads folder via MediaStore.
                        ContentResolver contentResolver = cordova.getActivity().getContentResolver();
                        ContentValues contentValues = getDefaultDownloadContentValues(filename);
                        Uri fileUri = null;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            fileUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
                        }
                        if (fileUri != null) {
                            OutputStream out = contentResolver.openOutputStream(fileUri);
                            if (out != null) {
                                out.write(fileData);
                                out.close();
                                try {
                                    JSONObject meta = getMetadata(fileUri);
                                    if (meta != null) {
                                        callbackContext.success(meta);
                                    } else {
                                        callbackContext.error("Failed to retrieve metadata.");
                                    }
                                } catch (JSONException e) {
                                    callbackContext.error("Error getting metadata: " + e.getMessage());
                                }
                            } else {
                                callbackContext.error("Failed to open output stream for default Downloads folder.");
                            }
                        } else {
                            callbackContext.error("Failed to create file in the Downloads folder using MediaStore.");
                        }
                    }
                } catch (IOException e) {
                    callbackContext.error("Error saving file: " + e.getMessage());
                }
            }
        });
    }



    private ContentValues getDefaultDownloadContentValues(String filename) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        return contentValues;
    }



    private void handleSelectTargetPath(Uri uri) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("uri", uri.toString());
        String dirTraditionalPath = getTraditionalTreePath(uri);
        if (dirTraditionalPath != null) {
            result.put("traditionalPath", dirTraditionalPath);
        }
        currentCallbackContext.success(result);
    }


    private String getTraditionalTreePath(Uri uri) {
        try {
            String docId = DocumentsContract.getTreeDocumentId(uri);
            return buildTraditionalPath(docId);
        } catch (Exception e) {
            Log.e(TAG, "getTraditionalTreePath error: " + e.getMessage());
            return null;
        }
    }

    private String buildTraditionalPath(String docId) throws Exception {
        String decoded = URLDecoder.decode(docId, StandardCharsets.UTF_8.name());
        String[] parts = decoded.split(":");
        if (parts.length < 2) throw new Exception("Invalid docId=" + decoded);
        String type = parts[0];
        String rel = parts[1];
        if ("primary".equalsIgnoreCase(type)) {
            return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + rel;
        } else {
            return "/storage/" + type + "/" + rel;
        }
    }



private void handleSelectFile(Uri uri) throws JSONException, IOException {
        JSONObject meta = getMetadata(uri);
        if (meta != null) {
            currentCallbackContext.success(meta);
        } else {
            currentCallbackContext.error("Failed to retrieve metadata.");
        }
    }


    private JSONObject getMetadata(Uri uri) throws JSONException, IOException {
        ContentResolver cr = cordova.getActivity().getContentResolver();
        JSONObject meta = new JSONObject();
        // Query name and size via OpenableColumns
        try (Cursor cursor = cr.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIdx >= 0) meta.put("name", cursor.getString(nameIdx));
                if (sizeIdx >= 0) meta.put("size", cursor.getLong(sizeIdx));
                meta.put("sizeReadable", getReadableSize(cursor.getLong(sizeIdx)));
            }
        }

        DocumentFile doc = DocumentFile.fromSingleUri(cordova.getActivity(), uri);
        if (doc != null && doc.exists()) {
            meta.put("uri", uri.toString());
            meta.put("mimeType", doc.getType());
            meta.put("lastModified", doc.lastModified());
            meta.put("isDirectory", doc.isDirectory());

        }

        // method action.equals("selectFiles")
        if (isCreateBase64 || isFileCreateBase64) {
            try {
                String base64 = createBase64String(uri);
                meta.put("base64", base64);
            } catch (IOException e) {
               // currentCallbackContext.error("Failed to encode Base64: " + e.getMessage());
                return null;
            }
        }

        // method action.equals("downloadBlob")
        if (isSaveCreateBase64) {
            try {
                String base64 = createBase64String(uri);
                meta.put("base64", base64);
            } catch (IOException e) {
                // currentCallbackContext.error("Failed to encode Base64: " + e.getMessage());
                return null;
            }
        }



            String type = cordova.getActivity().getContentResolver().getType(uri);
            if (type != null && (type.startsWith("video/") || type.startsWith("audio/"))) {
                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                mmr.setDataSource(cordova.getActivity(), uri);

                String durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                long durationMs = durationStr != null ? Long.parseLong(durationStr) : 0;

                meta.put("duration", durationMs);

                // Human readable mm:ss
                int totalSec = (int) (durationMs / 1000);
                int min = totalSec / 60;
                int sec = totalSec % 60;
                String humanDuration = String.format(Locale.getDefault(), "%02d:%02d", min, sec);
                meta.put("humanDuration", humanDuration);

                mmr.release();
            }


        // Traditional filesystem path (if from primary external storage)
        String traditional = getTraditionalPath(uri);
        if (traditional != null) {
            meta.put("traditionalPath", traditional);
        }

        String realPath = getRealPathFromUri(uri);
        if (traditional == null && realPath != null) {
            meta.put("traditionalPath", realPath);
        }

        return meta;
    }


    private String getReadableSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format(Locale.US, "%.2f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private String getTraditionalPath(Uri uri) {
        if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
            String docId = DocumentsContract.getDocumentId(uri);
            String[] parts = docId.split(":");
            if (parts.length >= 2) {
                String type = parts[0];
                String relPath = parts[1];
                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory().getAbsolutePath()
                            + "/" + relPath;
                } else {
                    // non-primary volumes (e.g., SD cards)
                    return "/storage/" + type + "/" + relPath;
                }
            }
        }
        return null;
    }



    private String createBase64String(Uri uri) throws IOException {
        ContentResolver cr = cordova.getActivity().getContentResolver();

        try (InputStream is = cr.openInputStream(uri);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[8 * 1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
        }
    }



    private String mapPermission(String permissionKey) {
        // Log.d(TAG, "mapPermission: " + permissionKey);
         switch (permissionKey) {
             case "RECORD_AUDIO":
                 return Manifest.permission.RECORD_AUDIO;
             case "MODIFY_AUDIO_SETTINGS":
                 return Manifest.permission.MODIFY_AUDIO_SETTINGS;
             case "CAMERA":
                 return Manifest.permission.CAMERA;
             case "READ_EXTERNAL_STORAGE":
                 if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                     return Manifest.permission.READ_EXTERNAL_STORAGE;
                 }
                 return null;
             case "WRITE_EXTERNAL_STORAGE":
                 if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                     return Manifest.permission.WRITE_EXTERNAL_STORAGE;
                 }
                 return null;
             case "MANAGE_EXTERNAL_STORAGE":
                 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                     return Manifest.permission.MANAGE_EXTERNAL_STORAGE;
                 }
                 return null;
             case "READ_MEDIA_AUDIO":
                 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                     return Manifest.permission.READ_MEDIA_AUDIO;
                 }
                 return null;
             case "READ_MEDIA_VIDEO":
                 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                     return Manifest.permission.READ_MEDIA_VIDEO;
                 }
                 return null;
             case "READ_MEDIA_IMAGES":
                 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                     return Manifest.permission.READ_MEDIA_IMAGES;
                 }
                 return null;
             default:
                 return null;
         }
     }



    private void checkAndRequestPermissions(JSONArray permissionsArr) throws JSONException {
        cordova.getThreadPool().execute(() -> {
            currentPermissionsStatus = new JSONObject();
            ArrayList<String> toRequest = new ArrayList<>();

            Context ctx = cordova.getActivity();

            for (int i = 0; i < permissionsArr.length(); i++) {
                String key = null;
                try {
                    key = permissionsArr.getString(i);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                String perm = mapPermission(key);
                if (perm == null) {
                    continue;
                }
                boolean granted = ContextCompat.checkSelfPermission(ctx, perm)
                        == PackageManager.PERMISSION_GRANTED;
                try {
                    currentPermissionsStatus.put(perm, granted);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                if (!granted) {
                    toRequest.add(perm);
                }
            }

            if (toRequest.isEmpty()) {
                currentCallbackContext.success(currentPermissionsStatus);
                currentCallbackContext = null;
            } else {
                String[] permsArray = toRequest.toArray(new String[0]);
                PermissionHelper.requestPermissions(this, PERMISSION_REQUEST_CODE, permsArray);
            }

        });

    }



    private void selectFile(String mimeType) {
        cordova.getThreadPool().execute(() -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            if (mimeType != null && !mimeType.isEmpty()) {
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{ mimeType });
            }
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION |
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            );
            cordova.startActivityForResult(this, intent, FILE_SELECT_CODE);
        });
    }



    private void openDocumentTree() {
        cordova.getThreadPool().execute(() -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION |
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            );
            cordova.startActivityForResult(this, intent, REQUEST_CODE_OPEN_DOCUMENT_TREE);
        });
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
            throws JSONException {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if ("selectFiles".equals(pendingAction)) {
                    selectFile(pendingMimeType);
                } else if ("selectTargetPath".equals(pendingAction)) {
                    openDocumentTree();
                }
            } else {
                currentCallbackContext.error("Permission denied.");
            }
        }


    }



    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode != Activity.RESULT_OK || data == null) {
            currentCallbackContext.error("Selection canceled.");
            return;
        }

        Uri uri = data.getData();
        if (uri == null) {
            currentCallbackContext.error("Uri is null.");
            return;
        }

        try {
            @SuppressLint("WrongConstant")
            final int takeFlags = data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            cordova.getActivity().getContentResolver()
                    .takePersistableUriPermission(uri, takeFlags);
        } catch (Exception e) {
            // ignore
        }

        cordova.getThreadPool().execute(() -> {
            try {
                if (requestCode == FILE_SELECT_CODE) {
                    handleSelectFile(uri);

                } else if (requestCode == REQUEST_CODE_OPEN_DOCUMENT_TREE) {
                    handleSelectTargetPath(uri);

                } else if (requestCode == REQUEST_CODE_PICK_MEDIA) {

                    JSONObject meta = getMetadata(uri);

                    if (meta != null) {
                        currentCallbackContext.success(meta);
                    } else {
                        currentCallbackContext.error("Failed to retrieve metadata.");
                    }

                } else {
                    currentCallbackContext.error("Unknown request code: " + requestCode);
                }
            } catch (Exception e) {
                currentCallbackContext.error("Error processing URI: " + e.getMessage());
            }
        });
    }




}
