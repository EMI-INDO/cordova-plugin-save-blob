package emi.indo.cordova.plugin.save.blob;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
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
import android.util.Log;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Created by EMI INDO So on 10/Mar/2025
 */

public class CordovaSaveBlob extends CordovaPlugin {

    private static final String TAG = "CordovaSaveBlob";

    private String selectedTargetPath;

    private static final int REQUEST_CODE_OPEN_DOCUMENT_TREE = 1;
    private static final int FILE_SELECT_CODE = 2;

    private CallbackContext currentCallbackContext;

    protected CordovaWebView mCordovaWebView;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        mCordovaWebView = webView;
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
       currentCallbackContext = callbackContext;
        if (action.equals("checkAndRequestPermissions")) {
            JSONObject options = args.getJSONObject(0);
            try {
                JSONArray permissionsArr = options.optJSONArray("permissions");
                if (permissionsArr == null) {
                    String perm = options.optString("permissions");
                    permissionsArr = new JSONArray();
                    permissionsArr.put(perm);
                }
                this.checkAndRequestPermissions(callbackContext, permissionsArr);
            } catch (Exception e) {
                this.currentCallbackContext.error("Error: " + e.getMessage());
            }
            return true;
            
        } else if (action.equals("selectFiles")) {
            JSONObject options = args.getJSONObject(0);
            String mimeType = options.optString("mime");
            this.selectFile(mimeType);
            return true;
            
        } else if (action.equals("registerWebRTC")) {
            this.registerWebRTC();
            return true;
            
        } else if ("selectTargetPath".equals(action)) {
            this.openDocumentTree();
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


    private void downloadBlob(final String base64Data, final String filename, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    String base64Preview = base64Data.substring(0, Math.min(50, base64Data.length()));
                  //  Log.d(TAG, "Base64 data preview: " + base64Preview);

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
                                    callbackContext.success(newFile.getUri().toString()); // "File downloaded to: " + newFile.getUri().toString()
                                    return;
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
                        // Use default Downloads folder via MediaStore.
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
                                callbackContext.success(fileUri.toString()); // "File downloaded to: " + fileUri.toString()
                                return;
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
                assert filePath != null;
                this.currentCallbackContext.success(filePath);
            } catch (Exception e) {
                this.currentCallbackContext.error("Failed to get file path: " + e.getMessage());
            }
        } else if (this.currentCallbackContext != null) {
            this.currentCallbackContext.error("Uri is null.");
        }
    }



    private void handleSelectTargetPath(Intent data) {
        Uri treeUri = data.getData();
        if (treeUri != null && this.currentCallbackContext != null) {
            try {
                if (!DocumentsContract.isTreeUri(treeUri)) {
                  //  Log.e(TAG, "The selected URI is not a tree URI: " + treeUri);
                    this.currentCallbackContext.error("Invalid directory URI.");
                    return;
                }

                int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                cordova.getContext().getContentResolver().takePersistableUriPermission(treeUri, takeFlags);

                this.selectedTargetPath = treeUri.toString();
                this.currentCallbackContext.success(treeUri.toString());
            } catch (SecurityException e) {
                this.currentCallbackContext.error("Failed to persist permissions: " + e.getMessage());
            }
        } else if (this.currentCallbackContext != null) {
            this.currentCallbackContext.error("Failed to select a directory.");
        }
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






    private void checkAndRequestPermissions(CallbackContext callbackContext, JSONArray permissionsArr) {
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {

        Activity activity = cordova.getActivity();
        List<String> permissionsToRequest = new ArrayList<>();

        for (int i = 0; i < permissionsArr.length(); i++) {
            String permissionKey = permissionsArr.optString(i);
            String mappedPermission = mapPermission(permissionKey);
            if (mappedPermission != null && ContextCompat.checkSelfPermission(activity, mappedPermission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(mappedPermission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            String[] permsArray = permissionsToRequest.toArray(new String[0]);
            ActivityCompat.requestPermissions(activity, permsArray, 1);
            callbackContext.error("Permission has not been granted. Inquiry: " + Arrays.toString(permsArray));
        } else {
            callbackContext.success("All requested permits have been granted.");
        }
    }

    /**
     * A function to map permission names from JS to string values in Manifest.
     */
    private String mapPermission(String permissionKey) {
        switch (permissionKey) {
            case "CAMERA":
                return Manifest.permission.CAMERA;
            case "RECORD_AUDIO":
                return Manifest.permission.RECORD_AUDIO;
            case "MODIFY_AUDIO_SETTINGS":
                return Manifest.permission.MODIFY_AUDIO_SETTINGS;
            case "READ_EXTERNAL_STORAGE":
                return Manifest.permission.READ_EXTERNAL_STORAGE;
            case "WRITE_EXTERNAL_STORAGE":
                return Manifest.permission.WRITE_EXTERNAL_STORAGE;
            case "MANAGE_EXTERNAL_STORAGE":
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    return Manifest.permission.MANAGE_EXTERNAL_STORAGE;
                } else {
                    return null;
                }
                // If it supports Android 14+ with new permissions:
            case "READ_MEDIA_AUDIO":
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    return Manifest.permission.READ_MEDIA_AUDIO;
                } else {
                    return null;
                }
            case "READ_MEDIA_VIDEO":
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    return Manifest.permission.READ_MEDIA_VIDEO;
                } else {
                    return null;
                }
            case "READ_MEDIA_IMAGES":
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    return Manifest.permission.READ_MEDIA_IMAGES;
                } else {
                    return null;
                }
            default:
                return null;
        }

      }
    });

 }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) throws JSONException {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                currentCallbackContext.success("Permission granted by the user.");
            } else {
                currentCallbackContext.error("Permission denied by user");
            }
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            if (requestCode == REQUEST_CODE_OPEN_DOCUMENT_TREE) {
                handleSelectTargetPath(data);
            } else if (requestCode == FILE_SELECT_CODE) {
                handleSelectFile(data);
            }
        } else {
            if (this.currentCallbackContext != null) {
                this.currentCallbackContext.error(requestCode == REQUEST_CODE_OPEN_DOCUMENT_TREE
                        ? "Directory selection canceled."
                        : "File selection canceled.");
            }
        }
    }



}
