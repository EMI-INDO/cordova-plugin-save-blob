
- You can handle, manipulate, any image, audio, video processed by the server, or AI, or files on the user's device.
- I am currently creating a new plugin cordova-plugin-opencv later cordova-plugin-save-blob will become a dependency to handle the cordova-plugin-opencv process. https://github.com/opencv/opencv
- This plugin is also designed to handle WebRTC: https://webrtc.github.io/samples/

---

# CordovaSaveBlob Plugin ( Document File )

A Cordova plugin for Android that provides functionalities to interact with the device's file system, including selecting files, managing Storage Access Framework (SAF), downloading blobs and remote files, and converting files to Base64.

---

## Installation

```bash
cordova plugin add cordova-plugin-save-blob
```


---

## Methods

The plugin exposes the following methods via `cordova.plugin.CordovaSaveBlob.<MethodName>`.

---

### `checkAndRequestPermissions(options)`

Checks and requests necessary Android permissions for file and media access.

* **Description:** This method checks if the specified permissions have been granted. If not, it will request them from the user. It's crucial to call this method before performing operations that require permissions, such as `selectFiles` or `selectTargetPath`.
* **Parameters:**
    * `options` (Object):
        * `permissions` (Array of Strings or String): An array of permission keys (e.g., `"READ_EXTERNAL_STORAGE"`, `"WRITE_EXTERNAL_STORAGE"`, `"READ_MEDIA_IMAGES"`, `"READ_MEDIA_VIDEO"`, `"READ_MEDIA_AUDIO"`, `"RECORD_AUDIO"`, `"MODIFY_AUDIO_SETTINGS"`) or a single permission key string.
* **Response (Success):**
    * `Object`: A JSON object where keys are the full Android permission strings and values are `true` if the permission is granted, or `false` otherwise.
    * **Example:**
        ```json
        {
          "android.permission.READ_EXTERNAL_STORAGE": true,
          "android.permission.WRITE_EXTERNAL_STORAGE": false
        }
        ```
* **Response (Error):**
    * `String`: An error message if the permission request fails or is denied by the user.

---

### `selectFiles(options)`

Opens a file picker to allow the user to select one or more files.

* **Description:** This method launches the Android system's file picker, enabling the user to select a file. Once a file is chosen, its metadata (including its URI, name, size, MIME type, and optionally Base64 data) will be returned. **The Base64 data is primarily intended for direct rendering in HTML elements (e.g., `<img>`, `<audio>`, `<video>`) or for in-memory processing within your app.**
* **Parameters:**
    * `options` (Object):
        * `mime` (String, optional): The MIME type to filter the displayed files (e.g., `"image/*"`, `"application/pdf"`, `"audio/*"`). If not provided, it defaults to `"*/*"` (all file types).
        * `isBase64` (Boolean, optional): If `true`, the content of the selected file will be read and encoded as a **Base64 string**, which is then included in the response. This allows the file's content to be directly used in web views (e.g., as `src` for `<img>` tags). **Caution:** Enabling this option for large files may lead to memory issues (Out Of Memory) on the device.
* **Response (Success):**
    * `Object`: A JSON object containing file metadata:
        * `uri` (String): The content URI (`content://`) of the selected file.
        * `name` (String): The display name of the file.
        * `size` (Number): The size of the file in bytes.
        * `sizeReadable` (String): The file size in a human-readable format (e.g., "1.23 MB").
        * `mimeType` (String): The MIME type of the file.
        * `lastModified` (Number): The last modified timestamp of the file in milliseconds.
        * `isDirectory` (Boolean): `true` if the selected item is a directory, `false` if it is a file.
        * `base64` (String, optional): The Base64 encoded string of the file content (only if `isBase64` was `true`). This can be prepended with `data:MIME_TYPE;base64,` for direct use in HTML.
* **Response (Error):**
    * `String`: An error message if the selection is canceled by the user or fails for other reasons.

---

### `registerWebRTC()`

Registers a `WebChromeClient` to handle WebRTC permission requests.

* **Description:** This method is specifically designed for Android 14+ and ensures that WebRTC media permissions (such as camera and microphone access) are automatically granted when requested by the WebView. This is very helpful for enabling WebRTC functionalities within your Cordova app without manual user intervention.
* **Parameters:** None.
* **Response (Success):** None (Void).
* **Response (Error):** None.

---

### `selectTargetPath()`

Opens a directory picker to allow the user to select a target directory for saving files using the Storage Access Framework (SAF).

* **Description:** This method leverages Android's Storage Access Framework (SAF) to let the user pick a folder. Once a folder is selected, your app will be granted persistent permissions to read and write within that folder, which is crucial for file management on Android 10+ where direct file system access has become more restricted.
* **Parameters:** None.
* **Response (Success):**
    * `Object`: A JSON object containing information about the selected directory:
        * `uri` (String): The SAF URI (`content://`) of the selected directory.
        * `traditionalPath` (String, optional): The traditional file system path (e.g., `/storage/emulated/0/Download/`) if the selected directory is on primary external storage. This might be `null` if the traditional path cannot be determined or is not applicable.
* **Response (Error):**
    * `String`: An error message if the selection is canceled or fails.

---

### `conversionSAFUri(uriPath)`

Converts a content URI (SAF) or an internal data URI to a traditional file system path.

* **Description:** This method attempts to resolve a given URI, which can be either a SAF URI (`content://...`) or an internal app data URI (`/data/user/0/...`), into its corresponding traditional, directly accessible file system path (e.g., `/storage/emulated/0/Music/song.mp3`). This is particularly useful for scenarios where other plugins or native APIs require an absolute file path rather than a content URI.
* **Parameters:**
    * `uriPath` (String): The URI string (`content://` or `/data/user/0/`) to convert.
* **Response (Success):**
    * `String`: The corresponding traditional file system path (e.g., `/storage/emulated/0/Download/filename.pdf`).
* **Response (Error):**
    * `String`: An error message if the conversion fails or the URI cannot be resolved to a traditional path.

---

### `downloadBlob(options)`

Saves Base64 encoded data as a file on the device.

* **Description:** This method takes a raw Base64 string and saves it as a file on the device. You have the option to specify a target directory using a previously selected SAF URI, or you can let the plugin save it to the device's default Downloads folder.
* **Parameters:**
    * `options` (Object):
        * `saveToPath` (String, optional): The SAF URI (`content://`) of the directory where the file should be saved. If not provided, the file will be saved to the device's default Downloads folder.
        * `base64Data` (String): The Base64 encoded string of the file content to be saved.
        * `fileName` (String): The desired filename for the saved file.
        * `isBase64` (Boolean, optional): If `true`, the content of the newly saved file will be read back and encoded as a Base64 string, which is then included in the response. This is for verifying the saved content or for immediate in-memory use, **not for re-saving Base64 as a file.** **Caution:** Enabling this option for large files may lead to memory issues.
* **Response (Success):**
    * `Object`: A JSON object containing metadata of the successfully saved file, similar to the `selectFiles` response.
        * `uri` (String): The content URI (`content://`) of the saved file.
        * `name` (String): The display name of the file.
        * `size` (Number): The size of the file in bytes.
        * `sizeReadable` (String): The file size in a human-readable format.
        * `mimeType` (String): The MIME type of the file.
        * `lastModified` (Number): The last modified timestamp of the file in milliseconds.
        * `isDirectory` (Boolean): `true` if it's a directory, `false` if it's a file.
        * `base64` (String, optional): The Base64 encoded string of the saved file's content (only if `isBase64` was `true`).
* **Response (Error):**
    * `String`: An error message if saving fails (e.g., invalid path, no write permissions, I/O error).

---

### `downloadFile(options)`

Downloads a file from a remote URL and saves it to the device.

* **Description:** This method fetches a file from a given URL. You can choose to save the file to a specific directory (using a SAF URI) or to the default Downloads folder. The plugin also provides progress updates during the download. If `isBase64` is `true`, the file content will be returned as Base64 for **direct app/HTML usage** without local storage.
* **Parameters:**
    * `options` (Object):
        * `fileUrl` (String): The full URL of the file to download.
        * `isBase64` (Boolean, optional): If `true`, the content of the downloaded file will be returned as a **Base64 string**, and the **file will NOT be physically saved to device storage**. This allows for immediate use of the file data in your app or HTML elements.
        * `saveToPath` (String, optional): The SAF URI (`content://`) of the directory to save the file. This parameter will be ignored if `isBase64` is set to `true`.
        * `fileName` (String): The desired filename for the downloaded file.
* **Response (Success):**
    * `Number` (for progress updates): An integer value (0-100) representing the download progress percentage. These are sent incrementally as `PluginResult.Status.OK` with `setKeepCallback(true)`.
    * `Object` (upon download completion):
        * If `isBase64` is `true`:
            * `base64` (String): The Base64 encoded string of the downloaded file. This can be prepended with `data:MIME_TYPE;base64,` for direct use in HTML.
        * If `isBase64` is `false`: A JSON object containing metadata of the successfully saved file, similar to the `selectFiles` response.
            * `uri` (String): The content URI (`content://`) of the saved file.
            * `name` (String): The display name of the file.
            * `size` (Number): The size of the file in bytes.
            * `sizeReadable` (String): The file size in a human-readable format.
            * `mimeType` (String): The MIME type of the file.
            * `lastModified` (Number): The last modified timestamp of the file in milliseconds.
            * `isDirectory` (Boolean): `true` if it's a directory, `false` if it's a file.
* **Response (Error):**
    * `String`: An error message if the download fails (e.g., network error, invalid URL, write error).

---

### `fileToBase64(options)`

Converts a local file (specified by its path or URI) to a Base64 encoded string.

* **Description:** This method reads the content of a file from the provided `filePath` (which can be a `content://` URI, a `file://` URI, or a traditional file system path) and encodes it into a **Base64 string**. The primary use case for this is to allow the file's content to be directly used in HTML elements (e.g., `<img>`, `<audio>`, `<video>`) or for in-memory processing within your app.
* **Parameters:**
    * `options` (Object):
        * `filePath` (String): The path or URI of the file to convert.
* **Response (Success):**
    * `Object`: A JSON object containing the file metadata and its Base64 representation:
        * `uri` (String): The URI of the file.
        * `name` (String): The display name of the file.
        * `size` (Number): The size of the file in bytes.
        * `sizeReadable` (String): The file size in a human-readable format.
        * `mimeType` (String): The MIME type of the file.
        * `lastModified` (Number): The last modified timestamp of the file in milliseconds.
        * `isDirectory` (Boolean): `true` if it's a directory, `false` if it's a file.
        * `base64` (String): The Base64 encoded string of the file content. This can be prepended with `data:MIME_TYPE;base64,` for direct use in HTML.
* **Response (Error):**
    * `String`: An error message if the file is not found, cannot be read, or the encoding process fails.

---

### `openAppSettings()`

Opens the application's detailed settings page on Android.

* **Description:** This method provides a shortcut for users to directly access your app's detailed settings screen where they can manually adjust app permissions, manage storage usage, or modify other settings if needed (e.g., after an initial permission denial).
* **Parameters:** None.
* **Response (Success):** None (Void).
* **Response (Error):**
    * `String`: An error message if the app settings page cannot be opened.

---







# Plugin Variable permissions

to automatically and dynamically add audio/video permissions, minimizing unnecessary permissions so you don't have to fill out the declaration form when uploading your APK/AAB to the Google Play Console.

---

## 🔍 Description

This plugin aims to:

* Automatically add the plugin's built-in permissions to `AndroidManifest.xml` when the plugin or Android platform is added, without requiring a permission rationale form to be filled out when the APK/AAB is uploaded to the Google Play Console.
* Add additional permissions based on **plugin variables** set during installation, ensuring that only truly needed permissions are activated.
* Use **hooks** to inject permissions into `AndroidManifest.xml`, and also add `android:requestLegacyExternalStorage="true"` to the `<application>` tag for storage compatibility.

---

## 📦 Installation

Run the following command to add the plugin to your Cordova project:

```bash
cordova plugin add cordova-plugin-save-blob \
  --save \
  --variable IS_CAMERA_PERMISSION=true \
  --variable IS_READ_M_VIDEO_PERMISSION=false \
  --variable IS_READ_M_IMAGES_PERMISSION=false \
  --variable IS_MANAGE_STORAGE_PERMISSION=false
```

If variables are not included, all will default to `false`.
A `true` value indicates that the optional permission is enabled and will be added.

---

## 🛡️ Built-in Plugin Permissions (Default)

The plugin automatically adds **five (5)** permissions to `AndroidManifest.xml` without triggering the declaration form in the Google Play Console when the APK/AAB is uploaded:

| Permission                                                               | Description                                                                     |
| :----------------------------------------------------------------------- | :------------------------------------------------------------------------------ |
| `android.permission.READ_MEDIA_AUDIO`                                    | Reads audio files from user's storage                                           |
| `android.permission.RECORD_AUDIO`                                        | Records audio using the device's microphone                                     |
| `android.permission.MODIFY_AUDIO_SETTINGS`                               | Modifies audio configurations (volume, routing, etc.)                           |
| `android.permission.READ_EXTERNAL_STORAGE` (`maxSdkVersion=32`)          | Reads generic files from external storage                                       |
| `android.permission.WRITE_EXTERNAL_STORAGE` (`maxSdkVersion=32`, `tools:ignore="ScopedStorage"`) | Writes files to external storage and bypasses ScopedStorage on Android 11–12 |

The five permissions listed above **do not require** filling out a permission declaration form in the Play Console.

---

## 🔧 Optional Permissions (Based on Plugin Variables)

Some sensitive permissions will only be added if you set the following variables during installation:

| Variable                          | Added Permission                      | Play Console Form      |
| :-------------------------------- | :------------------------------------ | :--------------------- |
| `IS_CAMERA_PERMISSION=true`       | `android.permission.CAMERA`           | Simple permission      |
| `IS_READ_M_VIDEO_PERMISSION=true` | `android.permission.READ_MEDIA_VIDEO` | Simple permission      |
| `IS_READ_M_IMAGES_PERMISSION=true`| `android.permission.READ_MEDIA_IMAGES`| Simple permission      |
| `IS_MANAGE_STORAGE_PERMISSION=true`| `android.permission.MANAGE_EXTERNAL_STORAGE`| Highly sensitive     |

The permissions listed above **require** filling out a permission declaration form when the APK/AAB is uploaded to the Google Play Console.
