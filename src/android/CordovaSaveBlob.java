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

import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.CordovaWebView;
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

    private static final int REQUEST_CODE_OPEN_DOCUMENT_TREE = 1;
    private static final int FILE_SELECT_CODE = 2;

    private JSONObject currentPermissionsStatus = new JSONObject();
    private static final long BASE64_THRESHOLD = 35 * 1024 * 1024; // 35 MB

    private CallbackContext currentCallbackContext;

    protected CordovaWebView mCordovaWebView;

    private Boolean isGatBase64 = false;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
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
            checkAndRequestPermissions(callbackContext, permissionsArr);
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


    @SuppressLint("SdCardPath")
    private void conversionSAFUri(String uriPath, CallbackContext callbackContext) {
        cordova.getThreadPool().execute(() -> {
            try {
                String finalPath;

                if (uriPath.startsWith("content://")) {
                    finalPath = convertContentUriToFilePath(uriPath);
                } else if (uriPath.startsWith("/data/user/0/")) {
                    File sourceFile = new File(uriPath);
                    File destDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File destFile = new File(destDir, sourceFile.getName());

                    copyFile(sourceFile, destFile);
                    finalPath = destFile.getAbsolutePath();
                } else {
                    finalPath = uriPath;
                }

                callbackContext.success(finalPath);
            } catch (Exception e) {
                callbackContext.error("conversionUri Error : " + e.getMessage());
            }
        });
    }

    private void copyFile(File sourceFile, File destFile) throws IOException {
        try (InputStream in = new FileInputStream(sourceFile);
             OutputStream out = new FileOutputStream(destFile)) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
    }



    private String convertContentUriToFilePath(String uriString) {
        if (uriString == null) return null;
        Context ctx = cordova.getContext();

        // Raw path?
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
            // 4a) Tree‑URI → folder
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                    && DocumentsContract.isTreeUri(uri)) {
                String treeId = DocumentsContract.getTreeDocumentId(uri);
                String[] split = treeId.split(":");
                String vol = split[0];
                String part = split.length>1? split[1] : "";
                if ("primary".equalsIgnoreCase(vol)) {
                    return Environment.getExternalStorageDirectory()
                            .getAbsolutePath()
                            + (part.isEmpty()? "" : "/"+part);
                } else {
                    return "/storage/"+vol
                            + (part.isEmpty()? "" : "/"+part);
                }
            }
            // 4b) Dokumen/file
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                    && DocumentsContract.isDocumentUri(ctx, uri)) {
                String auth = uri.getAuthority();
                String docId = DocumentsContract.getDocumentId(uri);
                String[] split = docId.split(":");
                String type = split[0];
                String part = split.length>1? split[1] : "";

                // ExternalStorage
                if ("com.android.externalstorage.documents".equals(auth)) {
                    if ("primary".equalsIgnoreCase(type)) {
                        return Environment.getExternalStorageDirectory()
                                .getAbsolutePath()
                                + (part.isEmpty()? "" : "/"+part);
                    } else {
                        return "/storage/"+type
                                + (part.isEmpty()? "" : "/"+part);
                    }
                }
                // Downloads
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
                // Media
                else if ("com.android.providers.media.documents".equals(auth)) {
                    String id = split[1];
                    Uri mediaUri = null;
                    if ("image".equals(type))
                        mediaUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                    else if ("video".equals(type))
                        mediaUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                    else if ("audio".equals(type))
                        mediaUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                    String sel = "_id=?";
                    String[] selArgs = { id };
                    String p = getDataColumn(ctx, mediaUri, sel, selArgs);
                    if (p != null) return p;
                }
            }
            // 4c) Fallback generic content://
            String dataPath = getDataColumn(ctx, uri, null, null);
            if (dataPath != null) return dataPath;
        }

        // Fallback
        return uriString;
    }

    private String getDataColumn(
            Context ctx, Uri uri, String sel, String[] selArgs) {
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



    private void openDocumentTree() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        cordova.startActivityForResult(this, intent, REQUEST_CODE_OPEN_DOCUMENT_TREE);
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

        Log.d(TAG, "Metadata: " + metaData.toString());
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





/*
    private void handleSelectTargetPath(Intent data) {
        Uri treeUri = data.getData();
        if (treeUri != null && this.currentCallbackContext != null) {
            try {
                // Pastikan ini adalah tree URI
                if (!DocumentsContract.isTreeUri(treeUri)) {
                    this.currentCallbackContext.error("Invalid directory URI.");
                    return;
                }

                // Simpan permission akses persist
                int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION &
                        (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                cordova.getContext().getContentResolver()
                        .takePersistableUriPermission(treeUri, takeFlags);

                // Konversi tree URI ke filesystem path tradisional
                String filePath = convertContentUriToFilePath(treeUri.toString());
                if (filePath != null && !filePath.isEmpty()) {
                    this.selectedTargetPath = filePath;
                    this.currentCallbackContext.success(filePath);
                } else {
                    this.currentCallbackContext.error("Failed to convert directory URI to file path.");
                }
            } catch (SecurityException e) {
                this.currentCallbackContext.error("Failed to persist permissions: " + e.getMessage());
            }
        } else if (this.currentCallbackContext != null) {
            this.currentCallbackContext.error("Failed to select a directory.");
        }
    }

*/




    // 2) Murni kembalikan SAF tree‑URI folder
    private void handleSelectTargetPath(Intent data) {
        Uri treeUri = data.getData();
        if (treeUri == null || currentCallbackContext == null) {
            assert currentCallbackContext != null;
            currentCallbackContext.error("Failed to select a directory.");
            return;
        }
        if (!DocumentsContract.isTreeUri(treeUri)) {
            currentCallbackContext.error("Invalid directory URI.");
            return;
        }
        int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        cordova.getContext()
                .getContentResolver()
                .takePersistableUriPermission(treeUri, takeFlags);

        currentCallbackContext.success(treeUri.toString());
    }



    private String mapPermission(String permissionKey) {
        switch (permissionKey) {
            case "RECORD_AUDIO":
                return Manifest.permission.RECORD_AUDIO;
            case "MODIFY_AUDIO_SETTINGS":
                return Manifest.permission.MODIFY_AUDIO_SETTINGS;
            case "READ_EXTERNAL_STORAGE":
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    return null;
                }
                return Manifest.permission.READ_EXTERNAL_STORAGE;
            case "WRITE_EXTERNAL_STORAGE":
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    return null;
                }
                return Manifest.permission.WRITE_EXTERNAL_STORAGE;
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

    private void checkAndRequestPermissions(CallbackContext callbackContext, JSONArray permissionsArr) throws JSONException {
        ArrayList<String> permissionsToRequest = new ArrayList<>();
        // Log.d(TAG, "Checking permissions...");

        for (int i = 0; i < permissionsArr.length(); i++) {
            String permissionKey = permissionsArr.getString(i);
            String androidPermission = mapPermission(permissionKey);
            // Log.d(TAG, "Mapped permission for key " + permissionKey + ": " + androidPermission);


        }

        // Log.d(TAG, "All requested permissions are already granted.");
        currentCallbackContext.success(currentPermissionsStatus);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult: requestCode=" + requestCode);
        if (requestCode == 1) {
            try {
                for (int i = 0; i < permissions.length; i++) {
                    boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                    currentPermissionsStatus.put(permissions[i], granted);
                    // Log.d(TAG, "Permission " + permissions[i] + " granted: " + granted);
                }
                currentCallbackContext.success(currentPermissionsStatus);
            } catch (JSONException e) {
                // Log.e(TAG, "Error processing permissions result: " + e.getMessage());
                currentCallbackContext.error("Error processing permissions result: " + e.getMessage());
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        //  Log.d(TAG, "onActivityResult called: requestCode=" + requestCode + ", resultCode=" + resultCode);

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
            if (this.currentCallbackContext != null) {
                currentCallbackContext.error("Unhandled activity result");
            }
        }
    }



}
