package emi.indo.cordova.plugin.save.blob;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Base64;
import android.util.Size;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;


/**
 * Created by EMI INDO So on 10/Mar/2025
 */

public class CordovaSaveBlob extends CordovaPlugin {

    private static final String TAG = "CordovaSaveBlob";


    private static final int REQUEST_CODE_OPEN_DOCUMENT_TREE = 1; // action: selectFile
    private static final int FILE_SELECT_CODE = 2; // action: selectFile
    private static final int PERMISSION_REQUEST_CODE = 3; // action: checkAndRequestPermissions

    private static final int REQUEST_CODE_PICK_MEDIA = 1234; // action: showAudioListNative
    private static final int MANAGE_STORAGE_REQUEST_CODE = 301; // action: checkAndRequestPermissions

    private JSONObject currentPermissionsStatus;
    private CallbackContext currentCallbackContext = null;
    private String pendingAction;
    private String pendingMimeType;

    private String pendingCategory;
    private String pendingFolderName;


    protected CordovaWebView mCordovaWebView;

    // NOTE create string base64
    // Video/Audio/PDF: maxSize 20mb, if (file size 20mb+) apk crash or out of memory
    // handle with if(parseFloat(sizeReadable) <20)
    private Boolean isCreateBase64 = false;
    private Boolean isSaveCreateBase64 = false;
    private Boolean isFileCreateBase64 = false;

    // Audio list Native UI
    private FrameLayout listContainer;
    String audioMimeType;
    String overlayBgColor;
    String separatorItemColor;

    private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(4);
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    
    private static class SongInfo {
        public final String title;
        public final String artist;
        public final long albumId;
        public final String uriPath;
        public final String itemPath;
        public final long duration;
        public final long size;

        public SongInfo(String title, String artist, long albumId, String uriPath, String itemPath, long duration, long size) {
            this.title = title;
            this.artist = artist;
            this.albumId = albumId;
            this.uriPath = uriPath;
            this.itemPath = itemPath;
            this.duration = duration;
            this.size = size;
        }
    }



    @Override
    public void pluginInitialize() { // only cordova android 14+
        mCordovaWebView = webView;
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.currentCallbackContext = callbackContext;
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

        } else if ("revokeManageStorage".equals(action)) {
            this.openManageStorageSettings();
            return true;
        }
        else if (action.equals("selectFiles")) {
            JSONObject options = args.getJSONObject(0);
            String mimeType = options.optString("mime");
            this.isCreateBase64 = options.optBoolean("isBase64", false); // direct response render in html element
            this.pendingMimeType = mimeType;

            try {
                this.selectFile(mimeType);
            } catch (Exception e) {
                // this.currentCallbackContext.error("Error: " + e.getMessage());
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
            this.copyFileAndGetMetadata(uriPath, callbackContext);
           // this.conversionSAFUri(uriPath, callbackContext);
            return true;

        } else if ("downloadBlob".equals(action)) {
            JSONObject options = args.getJSONObject(0);
            String saveToPath = options.optString("saveToPath");
            String base64Data = options.optString("base64Data");
            String filename = options.optString("fileName");
            String format = options.optString("format");
            this.isSaveCreateBase64 = options.optBoolean("isBase64");  // response render in html element
            this.downloadBlob(saveToPath, base64Data, filename, format, callbackContext);
            return true;

        } else if (action.equals("downloadFile")) {
            // response render in html element
            JSONObject options = args.getJSONObject(0);
            String fileUrl = options.optString("fileUrl");
            boolean isCreateStringBase64 = options.optBoolean("isBase64", false); // uri SAF: if isCreateStringBase64 = true saveToPath will be ignored
            String saveToPath = options.optString("saveToPath");
            String fileName = options.optString("fileName");
            this.downloadFile(fileUrl, isCreateStringBase64, saveToPath, fileName, callbackContext);
            return true;

        } else if (action.equals("fileToBase64")) {
            // response render in html element
            JSONObject options = args.getJSONObject(0);
            String filePath = options.optString("filePath");
            isFileCreateBase64 = true;
            this.fileToBase64(filePath, callbackContext);
            return true;

        } else if (action.equals("openAppSettings")) {
            // Once the user has set the permissions, they can only be changed manually from the app settings.
            this.openAppSettings();
            return true;

        } else if ("openGallery".equals(action)) {
            JSONObject options = args.getJSONObject(0);
            String mediaType = options.optString("mediaType", "image");
            this.isCreateBase64 = options.optBoolean("isCreateBase64", false); // response render in html element
            cordova.getThreadPool().execute(() -> {
                openGallery(mediaType);
            });
            return true;

        } else if ("clearAppOldCache".equals(action)) {
            // selectFile action: will use a lot of storage, for example selectFile video: 500mb, so use action: clearAppOldCache.
            JSONObject options = args.getJSONObject(0);
            int setTime = options.optInt("setTime", 24); // default 24
            clearAppOldCache(setTime);
            return true;

        } else if ("createCategorizedFolder".equals(action)) {
            JSONObject options = args.getJSONObject(0);
            String category = options.getString("category");
            String folderName = options.getString("folderName");
            this.pendingCategory = category; // Use 'music', 'movies', 'pictures', 'dcim', or 'documents'
            this.pendingFolderName = folderName;

            cordova.getThreadPool().execute(() -> {
                    createCategorizedFolder(category, folderName, callbackContext);
            });
            return true;
            
        } else if (action.equals("showAudioListNative")) {
            // Native UI
            JSONObject options = args.getJSONObject(0);
            this.audioMimeType = options.optString("audioMimeType", "mp3");
            boolean isFilter = options.optBoolean("isFilter", false);
            String onlyFolderPath = options.optString("folderPath", "");
            this.overlayBgColor = options.optString("overlayBgColor", "#E6121212");
            this.separatorItemColor = options.optString("separatorItemColor", "#30FFFFFF");

            if (isFilter && !onlyFolderPath.isEmpty() && !onlyFolderPath.endsWith("/")) {
                onlyFolderPath += "/";
            }
            this.showAudioListNative(isFilter, onlyFolderPath, callbackContext);
            return true;

        } else if ("getFileMetadata".equals(action)) {
            JSONObject options = args.optJSONObject(0);
            if (options == null) {
                callbackContext.error("Options must be provided.");
                return false;
            }
            String filePath = options.optString("filePath");
            this.isCreateBase64 = options.optBoolean("isCreateBase64", false);

            if (filePath == null || filePath.isEmpty()) {
                callbackContext.error("filePath cannot be null or empty.");
                return false;
            }
            cordova.getThreadPool().execute(() -> {
                getFileMetadata(filePath, callbackContext);
            });

            return true;
        }

        return false;
    }



    // Native UI
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



    // Native UI
    private void showAudioListNative(boolean isFilter, String folderPath, CallbackContext callbackContext) {
        this.currentCallbackContext = callbackContext;

        cordova.getThreadPool().execute(() -> {
            List<SongInfo> songList = fetchSongsFromDevice(isFilter, folderPath);
            if (songList == null) {
                return;
            }
            cordova.getActivity().runOnUiThread(() -> buildAndShowUI(songList));
        });
    }



    private List<SongInfo> fetchSongsFromDevice(boolean isFilter, String folderPath) {
        List<SongInfo> songList = new ArrayList<>();
        ContentResolver cr = cordova.getActivity().getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE
        };

        StringBuilder selection = new StringBuilder(MediaStore.Audio.Media.IS_MUSIC + " != 0");
        List<String> selectionArgs = new ArrayList<>();

        selection.append(" AND " + MediaStore.Audio.Media.DATA + " LIKE ?");
        if (Objects.equals(audioMimeType, "all")){
            selectionArgs.add("%");
        } else {
            selectionArgs.add("%." + audioMimeType);
        }

        // ----------------------------------------------

        if (isFilter && folderPath != null && !folderPath.isEmpty()) {
            selection.append(" AND " + MediaStore.Audio.Media.DATA + " LIKE ?");
            selectionArgs.add(folderPath + "%");
        }

        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

        String[] selectionArgsArray = selectionArgs.toArray(new String[0]);

        try (Cursor cursor = cr.query(uri, projection, selection.toString(), selectionArgsArray, sortOrder)) {
            if (cursor != null) {
                int idxId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int idxTitle = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int idxArtist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int idxAlbumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                int idxData = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                int idxDuration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int idxSize = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idxId);
                    String title = cursor.getString(idxTitle);
                    String artist = cursor.getString(idxArtist);
                    long albumId = cursor.getLong(idxAlbumId);
                    String filePath = cursor.getString(idxData);
                    long duration = cursor.getLong(idxDuration);
                    long size = cursor.getLong(idxSize);

                    Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);

                    songList.add(new SongInfo(title, artist, albumId, contentUri.toString(), filePath, duration, size));
                }
                return songList;
            }
        } catch (Exception e) {
            if (this.currentCallbackContext != null) {
                this.currentCallbackContext.error("Failed to retrieve audio data: " + e.getMessage());
            }
        }
        return null;
    }





    private void buildAndShowUI(List<SongInfo> songList) {
        try {
            cleanupAudioListUI(false);

            Context context = cordova.getActivity();
            float density = context.getResources().getDisplayMetrics().density;
            ViewGroup rootView = (ViewGroup) webView.getView().getParent();

            listContainer = new FrameLayout(context);
            listContainer.setBackgroundColor(Color.parseColor(overlayBgColor));
            listContainer.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            listContainer.setClickable(true);

            ScrollView scrollView = new ScrollView(context);
            scrollView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            LinearLayout mainListLayout = new LinearLayout(context);
            mainListLayout.setOrientation(LinearLayout.VERTICAL);
            mainListLayout.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

            for (SongInfo song : songList) {
                LinearLayout itemLayout = new LinearLayout(context);
                itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                itemLayout.setGravity(Gravity.CENTER_VERTICAL);
                int padding = (int) (12 * density);
                itemLayout.setPadding(padding, padding, padding, padding);

                TypedValue outValue = new TypedValue();
                context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
                itemLayout.setBackgroundResource(outValue.resourceId);

                itemLayout.setOnClickListener(v -> {
                    try {
                        JSONObject songData = new JSONObject();
                        songData.put("name", song.title);
                        songData.put("uri", song.uriPath);
                        songData.put("traditionalPath", song.itemPath);
                        songData.put("humanDuration", formatDuration(song.duration));
                        songData.put("sizeReadable", formatSize(song.size));
                        PluginResult result = new PluginResult(PluginResult.Status.OK, songData);
                        result.setKeepCallback(true);
                        this.currentCallbackContext.sendPluginResult(result);
                        cleanupAudioListUI(true);
                    } catch (Exception e) {
                        // Log.e(TAG, "Failed to generate JSON", e);
                    }
                });

                ImageView coverView = new ImageView(context);
                int coverSize = (int) (50 * density);
                LinearLayout.LayoutParams coverLp = new LinearLayout.LayoutParams(coverSize, coverSize);
                coverLp.rightMargin = padding;
                coverView.setLayoutParams(coverLp);
                coverView.setScaleType(ImageView.ScaleType.CENTER_CROP);

                loadAlbumArtAsync(context, coverView, song.uriPath);

                LinearLayout textLayout = new LinearLayout(context);
                textLayout.setOrientation(LinearLayout.VERTICAL);
                textLayout.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

                TextView tvTitle = new TextView(context);
                tvTitle.setText(song.title != null ? song.title : "Unknown Title");
                tvTitle.setTextColor(Color.WHITE);
                tvTitle.setTextSize(16);
                tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
                tvTitle.setMaxLines(1);
                tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);

                TextView tvArtist = new TextView(context);
                tvArtist.setText(song.artist != null ? song.artist : "Unknown Artist");
                tvArtist.setTextColor(Color.LTGRAY);
                tvArtist.setTextSize(14);
                tvArtist.setMaxLines(1);
                tvArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);

                textLayout.addView(tvTitle);
                textLayout.addView(tvArtist);
                itemLayout.addView(coverView);
                itemLayout.addView(textLayout);
                mainListLayout.addView(itemLayout);

                View separator = new View(context);
                separator.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
                separator.setBackgroundColor(Color.parseColor(separatorItemColor));
                mainListLayout.addView(separator);
            }

            ImageButton btnClose = new ImageButton(context);
            int buttonSize = (int) (48 * density);
            btnClose.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            btnClose.setBackgroundColor(Color.TRANSPARENT);
            FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(buttonSize, buttonSize, Gravity.END | Gravity.TOP);
            btnClose.setOnClickListener(v -> cleanupAudioListUI(true));

            scrollView.addView(mainListLayout);
            listContainer.addView(scrollView);
            listContainer.addView(btnClose, closeLp);
            rootView.addView(listContainer);

            PluginResult result = new PluginResult(PluginResult.Status.OK, "UI Displayed");
            result.setKeepCallback(true);
            this.currentCallbackContext.sendPluginResult(result);

        } catch (Exception e) {
            // Log.e(TAG, "Failed to build UI", e);
            if (this.currentCallbackContext != null) {
                this.currentCallbackContext.error("Failed to build UI: " + e.getMessage());
            }
        }
    }




    private void loadAlbumArtAsync(Context context, ImageView imageView, String songUriString) {

        imageView.setImageResource(android.R.drawable.ic_menu_slideshow);
        imageView.setBackgroundColor(Color.parseColor("#333333"));
        int padding = (int) (12 * context.getResources().getDisplayMetrics().density);
        imageView.setPadding(padding/2, padding/2, padding/2, padding/2);

        executor.execute(() -> {
            Bitmap artwork = null;
            try {
                Uri uri = Uri.parse(songUriString);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    artwork = context.getContentResolver().loadThumbnail(uri, new Size(100, 100), null);
                }
            } catch (Exception e) {
             // Leave artwork null if there is an error (for example, file not found)
            }

            final Bitmap finalArtwork = artwork;
            mainHandler.post(() -> {
                if (finalArtwork != null) {
                    imageView.setImageBitmap(finalArtwork);
                    imageView.setBackgroundColor(Color.TRANSPARENT);
                    imageView.setPadding(0, 0, 0, 0);
                }
            });
        });
    }




    private static String formatDuration(long millis) {
        if (millis <= 0) {
            return "0:00";
        }
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes);
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    private static String formatSize(long size) {
        if (size <= 0) {
            return "0 B";
        }
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }


    private void cleanupAudioListUI(boolean closeCallback) {
        if (listContainer != null && listContainer.getParent() != null) {
            ((ViewGroup) listContainer.getParent()).removeView(listContainer);
        }
        listContainer = null;

        if (closeCallback && this.currentCallbackContext != null) {
            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(false);
            this.currentCallbackContext.sendPluginResult(result);
            this.currentCallbackContext = null;
        }
    }



    private void createCategorizedFolder(String category, String folderName, CallbackContext callbackContext) {
        try {
            String publicDirectory;

            switch (category.toLowerCase()) {
                case "music":
                    publicDirectory = Environment.DIRECTORY_MUSIC;
                    break;
                case "movies":
                    publicDirectory = Environment.DIRECTORY_MOVIES;
                    break;
                case "pictures":
                    publicDirectory = Environment.DIRECTORY_PICTURES;
                    break;
                case "dcim":
                    publicDirectory = Environment.DIRECTORY_DCIM;
                    break;
                case "documents":
                    publicDirectory = Environment.DIRECTORY_DOCUMENTS;
                    break;
                default:
                   // Log.d(TAG, "Invalid category specified. Use 'music', 'movies', 'pictures', 'dcim', or 'documents'.");
                    callbackContext.error("Invalid category specified. Use 'music', 'movies', 'pictures', 'dcim', or 'documents'.");
                    return;
            }

            // MODERN APPROACH: Android 10 (API 29) and higher
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = this.cordova.getActivity().getContentResolver();
                Uri collectionUri = getCollectionUri(category.toLowerCase());

                if (collectionUri == null) {
                   // Log.d(TAG, "Could not resolve MediaStore collection for category: " + category);
                    callbackContext.error("Could not resolve MediaStore collection for category: " + category);
                    return;
                }

                ContentValues values = new ContentValues();
                String relativePath = publicDirectory + File.separator + folderName;
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);

                Uri itemUri = null;
                try {
                    itemUri = resolver.insert(collectionUri, values);
                } catch (Exception e) {
                   // Log.e(TAG, "MediaStore insert failed, folder might already exist or another error occurred.", e);
                }

                File dir = Environment.getExternalStoragePublicDirectory(publicDirectory);
                String traditionalPath = new File(dir, folderName).getAbsolutePath();

                File checkFolder = new File(traditionalPath);
                if (checkFolder.exists() || itemUri != null) {
                    String uriPathString = (itemUri != null) ? itemUri.toString() : "N/A (folder likely pre-existed)";
                    sendSuccessResponse(uriPathString, traditionalPath, callbackContext);
                } else {
                    callbackContext.error("Failed to create folder using MediaStore and folder does not exist.");
                }

            } else {
                // LEGACY APPROACH: Android 9 (API 28) and below
                File dir = Environment.getExternalStoragePublicDirectory(publicDirectory);
                if (dir == null) {
                   // Log.d(TAG,"Public directory is null: " + publicDirectory);
                    callbackContext.error("Public directory is null: " + publicDirectory);
                    return;
                }
                File finalFolder = new File(dir, folderName);
                boolean success = finalFolder.exists() || finalFolder.mkdirs();

                if (success) {
                    String traditionalPath = finalFolder.getAbsolutePath();
                    String uriPath = Uri.fromFile(finalFolder).toString();
                    sendSuccessResponse(uriPath, traditionalPath, callbackContext);
                } else {
                   // Log.d(TAG,"Failed to create folder using legacy method. Check permissions.");
                    callbackContext.error("Failed to create folder using legacy method. Check permissions.");
                }
            }
        } catch (Exception e) {
           // Log.d(TAG,"An unexpected error occurred: " + e.getMessage());
            callbackContext.error("An unexpected error occurred: " + e.getMessage());
        }
    }




    private void sendSuccessResponse(String uriPath, String traditionalPath, CallbackContext callbackContext) {
        try {
            JSONObject result = new JSONObject();
            result.put("uriPath", uriPath);
            result.put("traditionalPath", traditionalPath);
            callbackContext.success(result);
        } catch (JSONException e) {
            callbackContext.error("Failed to create JSON success response.");
        }
    }

    private Uri getCollectionUri(String category) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String volumeName = MediaStore.VOLUME_EXTERNAL_PRIMARY;
            switch (category) {
                case "music":
                    return MediaStore.Audio.Media.getContentUri(volumeName);
                case "movies":
                    return MediaStore.Video.Media.getContentUri(volumeName);
                case "pictures":
                case "dcim":
                    return MediaStore.Images.Media.getContentUri(volumeName);
                case "documents":
                    return MediaStore.Files.getContentUri(volumeName);
                default:
                    return null;
            }
        }
        return null;
    }



    // selectFile action: will use a lot of storage, for example selectFile video: 500mb, so use action: clearAppOldCache.
    private void clearAppOldCache(int setTime) {
        File cacheDir = cordova.getContext().getCacheDir();

        if (cacheDir == null || !cacheDir.isDirectory()) {
          //  Log.w(TAG, "Cache directory is not valid.");
            return;
        }

        if (setTime == 0) {
            deleteRecursively(cacheDir);
            cacheDir.mkdir();
           // Log.d(TAG, "All cache cleared.");
            return;
        }

        long durationMs;
        if (setTime == 7 || setTime == 30 || setTime == 90) {
            durationMs = setTime * 24L * 60 * 60 * 1000;
        } else {
            durationMs = setTime * 60L * 60 * 1000;
        }

        long cutoff = System.currentTimeMillis() - durationMs;
        File[] files = cacheDir.listFiles();

        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.lastModified() < cutoff) {
                deleteRecursively(file);
            }
        }
    }


    private void deleteRecursively(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }

        if (fileOrDirectory.delete()) {
           // Log.d(TAG, "Deleted cache: " + fileOrDirectory.getPath());
        } else {
          //  Log.w(TAG, "Failed to delete cache: " + fileOrDirectory.getPath());
        }
    }






    private String getRealPathFromUri(Uri uri) {
        String realPath = null;
        String[] proj = {MediaStore.MediaColumns.DATA};
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
        cordova.getThreadPool().execute(() -> {
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
        });
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
                fileLength = conn.getContentLengthLong();
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
                            ? (int) (totalRead * 100 / fileLength)
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
                if (conn != null) conn.disconnect();
                try {
                    if (in != null) in.close();
                } catch (IOException ignored) {
                }
                try {
                    if (out != null) out.close();
                } catch (IOException ignored) {
                }
                try {
                    if (baos != null) baos.close();
                } catch (IOException ignored) {
                }
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
                } catch (IOException ignored) {
                }
            }
        });
    }






    private void copyFileAndGetMetadata(String uriString, CallbackContext callbackContext) {
        Context context = this.cordova.getActivity().getApplicationContext();
        Uri uri = Uri.parse(uriString);
        JSONObject result = new JSONObject();

        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            String originalFileName = null;
            long size = 0;

            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    originalFileName = cursor.getString(nameIndex);
                }

                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex);
                }
            }

            if (originalFileName == null) {
                originalFileName = "file_" + System.currentTimeMillis();
            }

            File cacheDir = context.getCacheDir();

            String sanitizedFileName = originalFileName.replaceAll("[\\\\/:*?\"<>|]", "_");
            File outputFile = new File(cacheDir, sanitizedFileName);

            try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
                 OutputStream outputStream = new FileOutputStream(outputFile)) {

                if (inputStream == null) {
                    throw new Exception("Unable to open the input stream of the URI.");
                }

                byte[] buffer = new byte[4096];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
            }

            result.put("uri", uri.toString());
            result.put("name", originalFileName);
            result.put("cachedPath", "file://" + outputFile.getAbsolutePath());
            result.put("size", size);
            result.put("sizeReadable", getReadableSize(size));

            String mimeType = context.getContentResolver().getType(uri);
            if (mimeType != null) {
                result.put("mimeType", mimeType);

                if (mimeType.startsWith("image/")) {
                    try (InputStream imageStream = context.getContentResolver().openInputStream(uri)) {
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inJustDecodeBounds = true; //
                        BitmapFactory.decodeStream(imageStream, null, opts);
                        result.put("width", opts.outWidth);
                        result.put("height", opts.outHeight);
                    } catch (Exception e) {
                       // Log.w(TAG, "Failed to read the image metadata.", e);
                    }
                }

                if (mimeType.startsWith("video/") || mimeType.startsWith("audio/")) {
                    MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                    try {
                        mmr.setDataSource(context, uri);
                        String durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                        long durationMs = durationStr != null ? Long.parseLong(durationStr) : 0;
                        result.put("duration", durationMs);

                        int totalSec = (int) (durationMs / 1000);
                        int min = totalSec / 60;
                        int sec = totalSec % 60;
                        result.put("humanDuration", String.format(Locale.getDefault(), "%02d:%02d", min, sec));
                    } catch (Exception e) {
                       // Log.w(TAG, "Failed to read media metadata.", e);
                    } finally {
                        mmr.release();
                    }
                }
            }

            callbackContext.success(result);

        } catch (Exception e) {
           // Log.e(TAG, "Failed to copy files and get metadata", e);
            callbackContext.error("Failed to process the file: " + e.getMessage());
        }
    }




    private void downloadBlob(final String saveToPath, final String base64Data, final String filename, final String format, final CallbackContext callbackContext) {

        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {

                    final String fullFilename = filename + "." + format;

                    final byte[] fileData = Base64.decode(base64Data, Base64.DEFAULT);

                    if (saveToPath.startsWith("content://")) {
                        Uri treeUri = Uri.parse(saveToPath);
                        DocumentFile pickedDir = DocumentFile.fromTreeUri(cordova.getContext(), treeUri);

                        if (pickedDir != null && pickedDir.canWrite()) {
                            String mimeType = getMimeType(format);
                            DocumentFile newFile = pickedDir.createFile(mimeType, fullFilename);

                            if (newFile != null) {
                                try (OutputStream out = cordova.getContext().getContentResolver().openOutputStream(newFile.getUri())) {
                                    out.write(fileData);

                                    JSONObject metadata = getMetadataFile(newFile.getUri());
                                    if (metadata != null) {
                                        callbackContext.success(metadata);
                                    } else {
                                        callbackContext.error("Failed to retrieve metadata for URI: " + newFile.getUri());
                                    }
                                }
                            } else {
                                callbackContext.error("SAF: Failed to create a file in the destination directory.");
                            }
                        } else {
                            callbackContext.error("SAF: Directory is unwritable or invalid.");
                        }

                    } else {

                        File dir = new File(saveToPath);
                        if (!dir.exists()) {
                            dir.mkdirs();
                        }

                        File file = new File(dir, fullFilename);
                        try (FileOutputStream fos = new FileOutputStream(file)) {
                            fos.write(fileData);
                        }

                        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        Uri contentUri = Uri.fromFile(file);
                        mediaScanIntent.setData(contentUri);
                        cordova.getContext().sendBroadcast(mediaScanIntent);

                        JSONObject metadata = getMetadataFile(contentUri);

                        if (metadata != null) {
                            callbackContext.success(metadata);
                        } else {
                            callbackContext.error("Failed to retrieve metadata for URI: " + contentUri);
                        }

                    }

                } catch (Exception e) {
                    callbackContext.error("Error: " + e.getMessage());
                }
            }
        });
    }



    private String getMimeType(String format) {
        if (format == null) return "application/octet-stream";
        switch (format.toLowerCase()) {
            case "mp3":
                return "audio/mpeg";
            case "wav":
                return "audio/wav";
            case "mp4":
                return "video/mp4";
            case "png":
                return "image/png";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "pdf":
                return "application/pdf";
            default:
                return "application/octet-stream";
        }
    }



    private String getTraditionalTreePath(Uri uri) {
        try {
            String docId = DocumentsContract.getTreeDocumentId(uri);
            return buildTraditionalPath(docId);
        } catch (Exception e) {
            //  Log.e(TAG, "getTraditionalTreePath error: " + e.getMessage());
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





    private void getFileMetadata(String filePath, CallbackContext callbackContext) {
        try {
            Uri fileUri;

            if (filePath.startsWith("content://") || filePath.startsWith("file://")) {
                fileUri = Uri.parse(filePath);
            } else {

                File file = new File(filePath);
                if (!file.exists()) {
                    callbackContext.error("File not found for path: " + filePath);
                    return;
                }
                fileUri = Uri.fromFile(file);
            }

            JSONObject metadata = getMetadataFile(fileUri);

            if (metadata != null) {
                callbackContext.success(metadata);
            } else {
                callbackContext.error("Failed to retrieve metadata for URI: " + fileUri);
            }

        } catch (Exception e) {
          //  Log.e(TAG, "Error getting file metadata", e);
            callbackContext.error("Error getting file metadata: " + e.getMessage());
        }
    }






    private JSONObject getMetadataFile(Uri uri) throws JSONException, IOException {
        ContentResolver cr = cordova.getActivity().getContentResolver();
        JSONObject meta = new JSONObject();
        String scheme = uri.getScheme();

        if (ContentResolver.SCHEME_FILE.equals(scheme)) {
            File file = new File(uri.getPath());
            if (file.exists()) {
                meta.put("name", file.getName());
                meta.put("size", file.length());
                meta.put("sizeReadable", getReadableSize(file.length()));
                meta.put("lastModified", file.lastModified());
                meta.put("isDirectory", file.isDirectory());
                meta.put("traditionalPath", file.getAbsolutePath());
            }
        } else if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {

            try (Cursor cursor = cr.query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    int sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (nameIdx >= 0) {
                        meta.put("name", cursor.getString(nameIdx));
                    }
                    if (sizeIdx >= 0 && cursor.getType(sizeIdx) != Cursor.FIELD_TYPE_NULL) {
                        long size = cursor.getLong(sizeIdx);
                        meta.put("size", size);
                        meta.put("sizeReadable", getReadableSize(size));
                    }
                }
            }
        }

        DocumentFile doc = DocumentFile.fromSingleUri(cordova.getActivity(), uri);
        if (doc != null && doc.exists()) {
            meta.put("uri", uri.toString());
            meta.put("mimeType", doc.getType());
            if (!meta.has("lastModified")) meta.put("lastModified", doc.lastModified());
            if (!meta.has("isDirectory")) meta.put("isDirectory", doc.isDirectory());
        }

        if (!meta.has("traditionalPath")) {
            String traditional = getTraditionalPath(uri);
            if (traditional != null) {
                meta.put("traditionalPath", traditional);
            } else {
                String realPath = getRealPathFromUri(uri);
                if (realPath != null) {
                    meta.put("traditionalPath", realPath);
                }
            }
        }

        if (isCreateBase64) {
            try {
                String base64 = createBase64String(uri);
                meta.put("base64", base64);
            } catch (IOException e) {

            }
        }

        String type = meta.optString("mimeType");
        if (type != null && type.startsWith("image/")) {
            try (InputStream raw = cr.openInputStream(uri);
                 BufferedInputStream is = new BufferedInputStream(raw)) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(is, null, opts);
                meta.put("width", opts.outWidth);
                meta.put("height", opts.outHeight);
            } catch (Exception e) {
                // Failed to read image metadata
            }
        }

        if (type != null && (type.startsWith("video/") || type.startsWith("audio/"))) {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            try {
                mmr.setDataSource(cordova.getActivity(), uri);
                String durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                long durationMs = durationStr != null ? Long.parseLong(durationStr) : 0;
                meta.put("duration", durationMs);

                int totalSec = (int) (durationMs / 1000);
                int min = totalSec / 60;
                int sec = totalSec % 60;
                meta.put("humanDuration", String.format(Locale.getDefault(), "%02d:%02d", min, sec));
            } finally {
                mmr.release();
            }
        }

        return meta;
    }





    // res action downloadBlob | downloadFile | fileToBase64
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

        // Traditional filesystem path (if from primary external storage)
        String traditional = getTraditionalPath(uri);
        if (traditional != null) {
            meta.put("traditionalPath", traditional);
        }

        String realPath = getRealPathFromUri(uri);
        if (traditional == null && realPath != null) {
            meta.put("traditionalPath", realPath);
        }

        // method action.equals("selectFiles")
        if (isCreateBase64 || isFileCreateBase64 || isSaveCreateBase64) {
            try {
                String base64 = createBase64String(uri);
                meta.put("base64", base64);
            } catch (IOException e) {
                // currentCallbackContext.error("Failed to encode Base64: " + e.getMessage());
                return null;
            }
        }


        assert doc != null;
        if (doc.getType() != null && doc.getType().startsWith("image/")) {

            try (InputStream raw = cr.openInputStream(uri);
                 BufferedInputStream is = new BufferedInputStream(raw)) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(is, null, opts);
                meta.put("width", opts.outWidth);
                meta.put("height", opts.outHeight);

            } catch (Exception e) {
                // Log.w(TAG, "Failed to read metadata: " + e.getMessage());
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



    private String mapPermission(String permissionKey) {

        switch (permissionKey) {
            case "RECORD_AUDIO":
                return Manifest.permission.RECORD_AUDIO;
            case "MODIFY_AUDIO_SETTINGS":
                return Manifest.permission.MODIFY_AUDIO_SETTINGS;
            case "CAMERA":
                return Manifest.permission.CAMERA;

            // Old storage permissions, only relevant for Android 9 (API 28) and below
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
                } else {
                    // Fallback for Android 12 and below
                    return Manifest.permission.READ_EXTERNAL_STORAGE;
                }

            case "READ_MEDIA_VIDEO":
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    return Manifest.permission.READ_MEDIA_VIDEO;
                } else {
                    // Fallback for Android 12 and below
                    return Manifest.permission.READ_EXTERNAL_STORAGE;
                }

            case "READ_MEDIA_IMAGES":
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    return Manifest.permission.READ_MEDIA_IMAGES;
                } else {
                    // Fallback for Android 12 and below
                    return Manifest.permission.READ_EXTERNAL_STORAGE;
                }
            default:
                return null;
        }
    }


    private void checkAndRequestPermissions(JSONArray permissionsArr) throws JSONException {


        boolean isManageStorageRequest = false;
        for (int i = 0; i < permissionsArr.length(); i++) {
            if ("MANAGE_EXTERNAL_STORAGE".equals(permissionsArr.getString(i))) {
                isManageStorageRequest = true;
                break;
            }
        }

        if (isManageStorageRequest) {

            cordova.getThreadPool().execute(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        try {
                            JSONObject status = new JSONObject();
                            status.put(Manifest.permission.MANAGE_EXTERNAL_STORAGE, true);
                            currentCallbackContext.success(status);
                        } catch (JSONException e) {
                            currentCallbackContext.error("Error creating status JSON: " + e.getMessage());
                        }
                    } else {
                        requestManageExternalStoragePermission();
                    }
                } else {
                    currentCallbackContext.success();
                }
            });

        } else {


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

    }




    @TargetApi(Build.VERSION_CODES.R)
    private void requestManageExternalStoragePermission() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            Uri uri = Uri.fromParts("package", cordova.getActivity().getPackageName(), null);
            intent.setData(uri);
            cordova.setActivityResultCallback(this);
            cordova.getActivity().startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
        } catch (Exception e) {
            if (currentCallbackContext != null) {
                currentCallbackContext.error("Failed to open the storage settings page: " + e.getMessage());
            }
        }
    }




    @TargetApi(Build.VERSION_CODES.R)
    private void openManageStorageSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.fromParts("package", this.cordova.getActivity().getPackageName(), null));
            this.cordova.getActivity().startActivity(intent);
        } catch (Exception e) {
            // This error is rare, but it's good to note in case of problems.
        }
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
       
            try {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        if (requestCode == PERMISSION_REQUEST_CODE) {
            CallbackContext callbackContext = this.currentCallbackContext;

            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if ("createCategorizedFolder".equals(pendingAction)) {
                    cordova.getThreadPool().execute(() -> {
                        createCategorizedFolder(pendingCategory, pendingFolderName, callbackContext);
                    });
                }
                else if ("selectFiles".equals(pendingAction)) {
                    cordova.getThreadPool().execute(() -> {
                        selectFile(pendingMimeType);
                    });
                }
                else if ("selectTargetPath".equals(pendingAction)) {
                    cordova.getThreadPool().execute(this::openDocumentTree);
                }
                else {
                    callbackContext.error("Action not recognized: " + pendingAction);
                }
            }
            else {
                callbackContext.error("Permission denied.");
            }

            this.currentCallbackContext = null;
            this.pendingAction         = null;
            this.pendingCategory       = null;
            this.pendingFolderName     = null;
            this.pendingMimeType       = null;


        }

    }



    // action selectFile
    // so that, path, uri, metadata can be obtained in a non-public location for example: /storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents/myFiles.pdf
    private JSONObject copyFileAndGetMetadata(Context context, Uri uri) {
        JSONObject result = new JSONObject();
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            String originalFileName = null;
            long size = 0;
            String sizeReadable = "0 B";

            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    originalFileName = cursor.getString(nameIndex);
                }

                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex);
                    sizeReadable = getReadableSize(size);
                }
            }

            if (originalFileName == null) {
                originalFileName = "file_" + System.currentTimeMillis();
            }

            File cacheDir = context.getCacheDir();
            String sanitizedFileName = originalFileName.replaceAll("[\\\\/:*?\"<>|]", "_");
            File outputFile = new File(cacheDir, sanitizedFileName);
            try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
                 OutputStream outputStream = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[4096];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
            }

            result.put("uri", uri.toString());
            result.put("name", originalFileName);
            result.put("cachedPath", outputFile.getAbsolutePath());
            result.put("size", size);
            result.put("sizeReadable", sizeReadable);

            String mimeType = context.getContentResolver().getType(uri);
            if (mimeType != null) {
                result.put("mimeType", mimeType);

                if (mimeType.startsWith("image/")) {
                    try (InputStream raw = context.getContentResolver().openInputStream(uri);
                         BufferedInputStream is = new BufferedInputStream(raw)) {
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inJustDecodeBounds = true;
                        BitmapFactory.decodeStream(is, null, opts);
                        result.put("width", opts.outWidth);
                        result.put("height", opts.outHeight);
                    } catch (Exception e) {
                       // Log.w(TAG, "Failed to read image metadata.", e);
                    }
                }

                if (mimeType.startsWith("video/") || mimeType.startsWith("audio/")) {
                    MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                    try {
                        mmr.setDataSource(context, uri);
                        String durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                        long durationMs = durationStr != null ? Long.parseLong(durationStr) : 0;
                        result.put("duration", durationMs);

                        int totalSec = (int) (durationMs / 1000);
                        int min = totalSec / 60;
                        int sec = totalSec % 60;
                        result.put("humanDuration", String.format(Locale.getDefault(), "%02d:%02d", min, sec));
                    } catch (Exception e) {
                       // Log.w(TAG, "Failed to read media metadata.", e);
                    } finally {
                        mmr.release();
                    }
                }
            }

            String traditionalPath = getTraditionalPath(uri);
            if (traditionalPath != null) {
                result.put("traditionalPath", traditionalPath);
            }

            return result;

        } catch (Exception e) {
            return null;
        }
    }




    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        final CallbackContext callback = currentCallbackContext;
        currentCallbackContext = null;
        if (callback == null) {
           // Log.w(TAG, "Callback context was lost or already used. Ignoring result.");
            return;
        }

        if (resultCode != Activity.RESULT_OK || data == null) {
            callback.error("Selection canceled.");
            return;
        }

        Uri uri = data.getData();
        if (uri == null) {
            callback.error("Uri is null.");
            return;
        }

        try {
            @SuppressLint("WrongConstant")
            final int takeFlags = data.getFlags()& (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            cordova.getActivity().getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (Exception e) {
           // Log.w(TAG, "Failed to take persistable URI permission. This might be okay.", e);
        }

        cordova.getThreadPool().execute(() -> {
            try {
                if (requestCode == FILE_SELECT_CODE) {
                    JSONObject fileData = copyFileAndGetMetadata(cordova.getActivity(), uri);
                    if (fileData != null) {
                        callback.success(fileData);
                    } else {
                        callback.error("Failed to process selected file.");
                    }
                } else if (requestCode == REQUEST_CODE_OPEN_DOCUMENT_TREE) {

                    JSONObject result = new JSONObject();
                    result.put("uri", uri.toString()); // SAF
                    String dirTraditionalPath = getTraditionalTreePath(uri);
                    if (dirTraditionalPath != null) {
                        result.put("traditionalPath", dirTraditionalPath);
                    }
                    callback.success(result);

                } else if (requestCode == REQUEST_CODE_PICK_MEDIA) {

                    JSONObject meta = getMetadata(uri);
                    if (meta != null) {
                        callback.success(meta);
                    } else {
                        callback.error("Failed to retrieve metadata.");
                    }

                } else {
                    callback.error("Unknown request code: " + requestCode);
                }
            } catch (Exception e) {
                callback.error("Error processing URI: " + e.getMessage());
            }
        });
    }




    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }



}
