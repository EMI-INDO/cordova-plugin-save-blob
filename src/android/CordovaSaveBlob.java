package emi.indo.cordova.plugin.save.blob;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Base64OutputStream;
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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Random;

/**
 * Created by EMI INDO So on 10/Mar/2025
 */

public class CordovaSaveBlob extends CordovaPlugin {

    private static final String TAG = "CordovaSaveBlob";

    private String selectedTargetPath;

   private static final int REQ_WRITE_EXT = 1234;
    // Pending conversion variables
    private String pendingConversionUri;
    private CallbackContext pendingConversionCallback;


    private static final int REQUEST_CODE_OPEN_DOCUMENT_TREE = 1;
    private static final int FILE_SELECT_CODE = 2;
    private static final int PERMISSION_REQUEST_CODE = 3;

    private CallbackContext currentCallbackContext;
    private JSONObject currentPermissionsStatus;

    private static final long BASE64_THRESHOLD = 35 * 1024 * 1024; // 35 MB
    
    protected CordovaWebView mCordovaWebView;

    private Boolean isGatBase64 = false;

    @Override
    public void pluginInitialize() {

        mCordovaWebView = webView;

    }



    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        currentCallbackContext = callbackContext;
        if ("checkAndRequestPermissions".equals(action)) {
            JSONObject options = args.getJSONObject(0);

            JSONArray permissionsArr = options.optJSONArray("permissions");
            if (permissionsArr == null) {
                String perm = options.optString("permissions");
                permissionsArr = new JSONArray();
                permissionsArr.put(perm);
            }
            checkAndRequestPermissions(permissionsArr);
            return true;

        } else if (action.equals("selectFiles")) {
            JSONObject options = args.getJSONObject(0);
            String mimeType = options.optString("mime");
            boolean isBase64 = options.optBoolean("isBase64");

            try {
                this.isGatBase64 = isBase64;
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

        } else if ("conversionSAFUri".equals(action)) { // update v0.0.3
            JSONObject options = args.getJSONObject(0);
            String uriPath = options.optString("uriPath");
            this.conversionSAFUri(uriPath, callbackContext);
            return true;

        } else if ("downloadBlob".equals(action)) {
            JSONObject options = args.getJSONObject(0);
            String saveToPath = options.optString("saveToPath"); // update v0.0.2
            String base64Data = options.optString("base64Data");
            String filename = options.optString("fileName");
            this.selectedTargetPath = saveToPath;
            this.downloadBlob(base64Data, filename, callbackContext);
            return true;

        } else if (action.equals("downloadFile")) {
            JSONObject options = args.getJSONObject(0);
            String fileUrl = options.optString("fileUrl");
            String fileName = options.optString("fileName");
            this.downloadFile(fileUrl, fileName, callbackContext);
            return true;
        }

        return false;
    }






    private void registerWebRTC(){

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





    @SuppressLint("SdCardPath")
    private void conversionSAFUri(String uriPath, CallbackContext callbackContext) {
        cordova.getThreadPool().execute(() -> {
            try {
                String finalPath;
                if (uriPath.startsWith("content://")) {
                    String lower = uriPath.toLowerCase();

                    if (lower.endsWith(".mp3")
                            || lower.endsWith(".wav")
                            || lower.endsWith(".m4a")
                            || lower.endsWith(".mp4")
                            || lower.endsWith(".3gp")
                            || lower.endsWith(".mkv")
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

        // Logging debug
       // Log.d("convertDataUri", "sourcePath=" + sourcePath);
       // Log.d("convertDataUri", "publicDir="   + publicDir.getAbsolutePath());
       // Log.d("convertDataUri", "dst full   =" + dst.getAbsolutePath());

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




    private String createBase64(DocumentFile documentFile) throws IOException {
        InputStream inputStream = cordova.getContext().getContentResolver().openInputStream(documentFile.getUri());
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            byteArrayOutputStream.write(buffer, 0, bytesRead);
        }
        inputStream.close();
        return Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT);
    }


    private String createBase64(Uri fileUri) throws IOException {
        InputStream inputStream = cordova.getContext().getContentResolver().openInputStream(fileUri);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            byteArrayOutputStream.write(buffer, 0, bytesRead);
        }
        inputStream.close();
        return Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT);
    }



    private void downloadBlob(final String base64Data, final String filename, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {

                    String base64Preview = base64Data.substring(0, Math.min(50, base64Data.length()));
                    // Log.d(TAG, "Base64 data preview: " + base64Preview);

                    byte[] fileData = Base64.decode(base64Data, Base64.DEFAULT);

                    if (selectedTargetPath != null) {
                        Uri treeUri = Uri.parse(selectedTargetPath);
                        DocumentFile pickedDir = DocumentFile.fromTreeUri(cordova.getContext(), treeUri);
                        if (pickedDir != null && pickedDir.canWrite()) {
                            DocumentFile newFile = pickedDir.createFile("application/octet-stream", filename);
                            if (newFile != null) {
                                OutputStream out = cordova.getContext().getContentResolver().openOutputStream(newFile.getUri());
                                if (out != null) {
                                    out.write(fileData);
                                    out.close();

                                    try {
                                        JSONObject metaData = new JSONObject();
                                        metaData.put("name", newFile.getName());
                                        metaData.put("base64", createBase64(newFile));
                                        metaData.put("saveToPath", newFile.getUri().toString()); // uriSAF
                                        metaData.put("size", newFile.length());
                                        callbackContext.success(metaData.toString());
                                    } catch (IOException | JSONException e) {
                                        // Log.e(TAG, "Error getting metadata: " + e.getMessage());
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
                                    JSONObject metaData = new JSONObject();
                                    metaData.put("name", filename);
                                    metaData.put("base64", createBase64(fileUri));
                                    metaData.put("saveToPath", fileUri.toString()); // uriSAF
                                    metaData.put("size", fileData.length);
                                    callbackContext.success(metaData.toString());
                                } catch (IOException | JSONException e) {
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


    private void downloadFile(String fileUrl, String fileName, CallbackContext callbackContext) {
        cordova.getThreadPool().execute(() -> {
            try {

                URL url = new URL(fileUrl);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                if (urlConnection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    callbackContext.error("Failed to download file: " + urlConnection.getResponseMessage());
                    return;
                }

                // Get the total file size
                int fileLength = urlConnection.getContentLength();

                // Create files in shared storage using MediaStore
                ContentResolver contentResolver = cordova.getActivity().getContentResolver();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");

                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                String extension = fileName.substring(fileName.lastIndexOf('.'));
                String randomString = generateRandomString(); // Generates a random string of 8 characters long
                String uniqueFileName = baseName + "_" + randomString + extension;

                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueFileName);

                Uri uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues);
                if (uri == null) {
                    callbackContext.error("Failed to create file in Downloads.");
                    return;
                }

                InputStream inputStream = urlConnection.getInputStream();
                OutputStream outputStream = contentResolver.openOutputStream(uri);

                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalBytesRead = 0;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    assert outputStream != null;
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;

                    int progress = (int) ((totalBytesRead * 100) / fileLength);

                    PluginResult progressResult = new PluginResult(PluginResult.Status.OK, progress);
                    progressResult.setKeepCallback(true);
                    callbackContext.sendPluginResult(progressResult);
                }

                outputStream.close();
                inputStream.close();
                urlConnection.disconnect();
                callbackContext.success(uri.toString());
            } catch (Exception e) {
                callbackContext.error("Download failed: " + e.getMessage());
            }
        });
    }


    // Generates a random string of 8 characters long
    private String generateRandomString() {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder randomString = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 8; i++) {
            randomString.append(characters.charAt(random.nextInt(characters.length())));
        }
        return randomString.toString();
    }



    private void selectFile(String mimeType) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        if (mimeType != null && !mimeType.isEmpty()) {
            String[] mimetypes = { mimeType };
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimetypes);
        }
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        cordova.startActivityForResult(this, intent, FILE_SELECT_CODE);
    }



    private void handleSelectFile(Intent data) {
        Uri uri = data.getData();
        if (uri != null && this.currentCallbackContext != null) {
            try {
                String filePath = getFilePathFromUri(uri);
                this.selectedTargetPath = filePath;
                if (filePath != null && !filePath.isEmpty()) {
                    JSONObject metaData = getMetadata(filePath);
                    if (metaData != null) {
                        this.currentCallbackContext.success(metaData);
                    } else {
                        this.currentCallbackContext.error("Failed to get metadata.");
                    }
                } else {
                    this.currentCallbackContext.error("File path is null or empty.");
                }
            } catch (Exception e) {
                this.currentCallbackContext.error("Failed to get file path: " + e.getMessage());
            }
        } else if (this.currentCallbackContext != null) {
            this.currentCallbackContext.error("Uri is null.");
        }
    }





    private JSONObject getMetadata(String filePath) throws JSONException, IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            return null;
        }

        JSONObject metaData = new JSONObject();
        metaData.put("name", file.getName());
        metaData.put("uriSAF", filePath);
        metaData.put("size", file.length());

        if (isGatBase64) {
            if (file.length() <= BASE64_THRESHOLD) {
                String base64 = createBase64String(file);
                metaData.put("base64", base64);
            } else {
                String base64Path = createBase64File(file);
                metaData.put("base64FilePath", base64Path);
            }
        }

       // Log.d(TAG, "Metadata: " + metaData.toString());
        return metaData;
    }

    private String createBase64String(File file) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (
                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
                Base64OutputStream b64os = new Base64OutputStream(baos, Base64.NO_WRAP)
        ) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                b64os.write(buffer, 0, bytesRead);
            }
        }
        return baos.toString("UTF-8");
    }

    private String createBase64File(File file) throws IOException {
        File cacheDir = cordova.getActivity().getCacheDir();
        File base64File = new File(cacheDir, file.getName() + ".b64");
        try (
                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
                FileOutputStream fos = new FileOutputStream(base64File);
                Base64OutputStream b64os = new Base64OutputStream(fos, Base64.NO_WRAP)
        ) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                b64os.write(buffer, 0, bytesRead);
            }
        }
        return base64File.getAbsolutePath();
    }



    private String getFilePathFromUri(Uri uri) {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = cordova.getContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        String displayName = cursor.getString(index);
                        @SuppressLint("UnsanitizedFilenameFromContentProvider") File file = new File(cordova.getContext().getCacheDir(), displayName);
                        try (InputStream inputStream = cordova.getContext().getContentResolver().openInputStream(uri);
                             FileOutputStream outputStream = new FileOutputStream(file)) {
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while (true) {
                                assert inputStream != null;
                                if ((bytesRead = inputStream.read(buffer)) == -1) break;
                                outputStream.write(buffer, 0, bytesRead);
                            }
                            return file.getAbsolutePath();
                        }
                    }
                }
            } catch (Exception e) {
                this.currentCallbackContext.error("Error getting file path from URI" + e.getMessage());
            }
        }
        return null;
    }











    private String mapPermission(String permissionKey) {
        switch (permissionKey) {
            case "RECORD_AUDIO":
                return Manifest.permission.RECORD_AUDIO;
            case "MODIFY_AUDIO_SETTINGS":
                return Manifest.permission.MODIFY_AUDIO_SETTINGS;
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
        currentPermissionsStatus = new JSONObject();
        ArrayList<String> toRequest = new ArrayList<>();

        for (int i = 0; i < permissionsArr.length(); i++) {
            String key = permissionsArr.getString(i);
            String perm = mapPermission(key);
            if (perm == null) continue;

            boolean granted = ContextCompat.checkSelfPermission( cordova.getContext(), perm) == PackageManager.PERMISSION_GRANTED;

            currentPermissionsStatus.put(perm, granted);
            if (!granted) {
                toRequest.add(perm);
            }
        }

        if (toRequest.isEmpty()) {
            currentCallbackContext.success(currentPermissionsStatus);
        } else {
            String[] permsArray = toRequest.toArray(new String[0]);
            PermissionHelper.requestPermissions(this, PERMISSION_REQUEST_CODE, permsArray);
        }
    }




    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_WRITE_EXT) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                try {
                    String result = convertDataUriToFilePath(pendingConversionUri);
                    pendingConversionCallback.success(result);
                } catch (Exception e) {
                    pendingConversionCallback.error("conversionUri Error: " + e.getMessage());
                }
            } else {
                pendingConversionCallback.error("Permission WRITE_EXTERNAL_STORAGE ditolak");
            }
            pendingConversionUri = null;
            pendingConversionCallback = null;
            return;
        }
        // Original permissions handler
        if (requestCode == PERMISSION_REQUEST_CODE) {
            try {
                for (int i = 0; i < permissions.length; i++) {
                    boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                    currentPermissionsStatus.put(permissions[i], granted);
                }
                currentCallbackContext.success(currentPermissionsStatus);
            } catch (JSONException e) {
                currentCallbackContext.error("Error processing permissions result: " + e.getMessage());
            }
        }
    }




    private void openDocumentTree() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );
        cordova.startActivityForResult(this, intent, REQUEST_CODE_OPEN_DOCUMENT_TREE);
    }




    private void handleSelectTargetPath(Intent data) {
        Uri treeUri = data.getData();
        if (treeUri == null) {
            currentCallbackContext.error("Failed to select a directory.");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!DocumentsContract.isTreeUri(treeUri)) {
                currentCallbackContext.error("Invalid directory URI.");
                return;
            }
        }

        final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

        cordova.getContext().getContentResolver()
                .takePersistableUriPermission(treeUri, takeFlags);

        currentCallbackContext.success(treeUri.toString());
    }



    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_OPEN_DOCUMENT_TREE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                handleSelectTargetPath(data);
            } else {
                currentCallbackContext.error("Directory selection canceled.");
            }

        } else if (requestCode == FILE_SELECT_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                cordova.getThreadPool().execute(() -> handleSelectFile(data));
            } else {
                currentCallbackContext.error("File selection canceled.");
            }

        } else {
            if (currentCallbackContext != null) {
                currentCallbackContext.error("Unhandled activity result: " + requestCode);
            }
        }
    }







}
