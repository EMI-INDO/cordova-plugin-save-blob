
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

- Addon for construct 3 https://www.construct.net/en/make-games/addons/1452/document-file

---

## Methods

The plugin exposes the following methods via `cordova.plugin.CordovaSaveBlob.<MethodName>`.

---


## new api cordova-plugin-save-blob@0.0.4  (12 new methods)

---

To ensure the `filePath` from LegacyExternalStorage (`traditionalPath`) continues to work on modern devices, whether the file source is from an SD card or a USB OTG, use the `copyFileToCache` method.

### Note

The `copyFileToCache` method, or any other method with the `isCopyFileToCache` parameter set to `true`, will consume storage space on the user's device. To clear the cache, use the `clearAppOldCache` method after the file manipulation process is complete.

The `response.cachedPath` URI can be used without any permission restrictions, for example, to upload to Google Drive or your own hosting service.


---
- v0.0.4 No description yet; I will create it when I have free time.
- This plugin works fully from Android 10 to Android 16, 
- 8 tests per release, until stable release.

<details>
<summary>View code example v0.0.4</summary>
<pre> 

/*
Is the uri selectTargetPath permission still valid? 
Note: On Android 14 or higher, if the user sets limited permissions after the device is rebooted, the limited permissions will no longer apply.
*/
```
cordova.plugin.CordovaSaveBlob.hasPersistedPermission(function (response) {
    const isUri = response.hasPermission;
    // permission still valid? 
    if (isUri) {

        console.log("still valid: " +response.uri);

    } else {
        console.log("No longer valid: " + JSON.stringify(response));
        
    }
}, function (error) {
    console.error(`${error}`);
});

```

///////////////////////////////
///////////////////////////////
///////////////////////////////



```

/*
0 = now
24 = 24 hours  
7 = 7 days  
30 = 30 days
90 = 90 days
*/

const time = 0;
let setTime = time === 0 ? 0 : time === 1 ? 24 : time === 2 ? 7 : time === 3 ? 30 : time === 4 ? 90 : 0;

cordova.plugin.CordovaSaveBlob.clearAppOldCache({
    setTime: setTime
}, function (response) {
}, function (error) {
    console.error(`${error}`);
});


```


///////////////////////////////
///////////////////////////////
///////////////////////////////


```

// Native UI 
// image | video | else request MANAGE_EXTERNAL_STORAGE

cordova.plugin.CordovaSaveBlob.openGallery({
    mediaType: "image",
    isCreateBase64: false
}, function (response) {
    
    console.log("Metadata: " + JSON.stringify(response));
    
    /*
    response.name || "";
    response.mimeType || "";
    response.base64 || "";
    response.uri || "";
    response.traditionalPath || "";
    response.cachedPath || "";
    response.size || 0;
    response.sizeReadable || "";

    ////////////////
    response.duration || 0;        // If mime video
    response.humanDuration || "";  // If mime video
    response.width || 0;           // if mime image
    response.height || 0;          // if mime image
    */

}, function (error) {
    console.error(`${error}`);
});


```




///////////////////////////////
///////////////////////////////
///////////////////////////////


```

// https://www.thoughtco.com/audio-file-mime-types-3469485

cordova.plugin.CordovaSaveBlob.showAudioListNative({
    audioMimeType: "audio/mpeg", // mp3   
    isFilter: false, // If true, folderPath = uri selectTargetPath
    folderPath: "",
    overlayBgColor: "black",
    separatorItemColor: "white"
}, (item) => {

    console.log("item click: " + JSON.stringify(item));
    
    /*
    item.name || "";
    item.uri || "";
    item.traditionalPath || "";
    item.humanDuration || "";
    item.sizeReadable || "";
    */

}, (error) => {
    console.error(`${error}`);
});

```



///////////////////////////////
///////////////////////////////
///////////////////////////////


```

cordova.plugin.CordovaSaveBlob.getFileMetadata({
    filePath: "SAF or response.cachedPath",
    isCreateBase64: false
}, function (response) {

    console.log("Metadata: " + JSON.stringify(response));
 
    /*
    response.name || "";
    response.mimeType || "";
    response.base64 || "";
    response.uri || "";
    response.traditionalPath || "";
    response.cachedPath || "";
    response.size || 0;
    response.sizeReadable || "";

    ////////////////
    response.duration || 0;        // If mime video | audio
    response.humanDuration || "";  // If mime video | audio
    response.width || 0;           // if mime image
    response.height || 0;          // if mime image
    */


}, function (error) {
    console.error(`${error}`);
});

```


///////////////////////////////
///////////////////////////////
///////////////////////////////


```

// support traditionalPath Example /storage/emulated/0/Music/mySong.mp3

cordova.plugin.CordovaSaveBlob.copyFileToCache({ uriPath: "SAF | response.cachedPath" }, function (response) {
    
    console.log("Metadata: " + JSON.stringify(response));
    
    /*
    response.name || "";
    response.mimeType || "";
    response.base64 || "";
    response.uri || "";
    response.traditionalPath || "";
    response.cachedPath || "";
    response.size || 0;
    response.sizeReadable || "";

    ////////////////
    response.duration || 0;        // If mime video | audio
    response.humanDuration || "";  // If mime video | audio
    response.width || 0;           // if mime image
    response.height || 0;          // if mime image
    */


}, function (error) {
    console.error(`${error}`);
});

```

///////////////////////////////
///////////////////////////////
///////////////////////////////


```

const category = 4;
let terget = category === 0 ? "pictures" : category === 1 ? "music" : category === 2 ? "movies" : category === 3 ? "dcim" : category === 4 ? "documents" : "download";

cordova.plugin.CordovaSaveBlob.createCategorizedFolder({
    category: terget,
    folderName: "MyAppName"
}, function (response) {

    console.log("Metadata: " + JSON.stringify(response));

   // response.uriPath || "";
   // response.traditionalPath || "";

}, function (error) {
    console.error(`${error}`);
});


```



///////////////////////////////
///////////////////////////////
///////////////////////////////

```

cordova.plugin.CordovaSaveBlob.checkAndroidVersion(function (response) {
    switch (response) {
        case ">=14":
            // READ_MEDIA_AUDIO true
            // READ_MEDIA_VIDEO true
            // READ_MEDIA_IMAGES true
           
            break;
        case "13":
            // READ_MEDIA_AUDIO true
            // READ_MEDIA_VIDEO true
            // READ_MEDIA_IMAGES true

            break;
        case "12":
            // READ_MEDIA_AUDIO true
            // READ_MEDIA_VIDEO true
            // READ_MEDIA_IMAGES true
            // READ_EXTERNAL_STORAGE true

            break;
        case "11":
            // READ_EXTERNAL_STORAGE true

            break;
        case "<=10":
            // READ_EXTERNAL_STORAGE true
            // WRITE_EXTERNAL_STORAGE true

            break;

    }

}, function (error) {

});

```




///////////////////////////////
///////////////////////////////
///////////////////////////////


```

cordova.plugin.CordovaSaveBlob.uploadFile(); // PHP https://github.com/EMI-INDO/cordova-plugin-save-blob/discussions/2

```


///////////////////////////////
///////////////////////////////
///////////////////////////////

```
// txt | json | js | html | and others

let finalNewFileName = "note_" + new Date().getTime();

cordova.plugin.CordovaSaveBlob.createNewFile({
    outputPath: "selectTargetPath",
    fileName: finalNewFileName,
    fileExtension: "txt",
    content: "Hello world"
},
    function (response) {

        console.log("Metadata: " + JSON.stringify(response));

       // response.fileUri;
       // response.fileName;
  

    }, function (error) {
        console.error(`${error}`);

    });
```


///////////////////////////////
///////////////////////////////
///////////////////////////////


```
    cordova.plugin.CordovaSaveBlob.loadFileContent({
        filePath: "SAF"
    },
        function (content) {
            console.log("content: " + JSON.stringify(content))
        }, function (error) {
            console.error(`${error}`);

        });

```


///////////////////////////////
///////////////////////////////
///////////////////////////////


```

         cordova.plugin.CordovaSaveBlob.saveFile({
            filePath: "SAF",
            content: "New hello world"
        },
        function (content) {
            console.log("content: " + JSON.stringify(content))
        }, function (error) {
            console.error(`${error}`);

        });

```

</pre>
</details>

---


## `checkAndRequestPermissions(options)`

Checks and requests necessary Android permissions for file and media access.

<details>
<summary>View description</summary>


* **Description:** This method checks if the specified permissions have been granted. If not, it will request them from the user. It's crucial to call this method before performing operations that require permissions, such as `selectFiles` or `selectTargetPath`.
* **Parameters:**
    * `options` (Object):
        * `permissions` [Array of Strings or String]: An array of permission keys [e.g., `"READ_EXTERNAL_STORAGE"`, `"WRITE_EXTERNAL_STORAGE"`, `"READ_MEDIA_IMAGES"`, `"READ_MEDIA_VIDEO"`, `"READ_MEDIA_AUDIO"`, `"RECORD_AUDIO"`, `"MODIFY_AUDIO_SETTINGS"`] or a single permission key string.
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

</details>




<details>
<summary>View code example</summary>
<pre> 
// Recommended cordova.plugin.CordovaSaveBlob.checkAndroidVersion();
   
// ["CAMERA", "READ_MEDIA_AUDIO", "READ_MEDIA_VIDEO", "READ_MEDIA_IMAGES", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "RECORD_AUDIO", "MODIFY_AUDIO_SETTINGS", "MANAGE_EXTERNAL_STORAGE"];

```
let permissionsData = ["CAMERA", "READ_MEDIA_AUDIO", "READ_MEDIA_VIDEO", "READ_MEDIA_IMAGES", "READ_EXTERNAL_STORAGE"];

cordova.plugin.CordovaSaveBlob.checkAndRequestPermissions({
    permissions: permissionsData
}, (result) => {

    let permissionsStatus = {};
    for (const permissionName in result) {
        if (Object.prototype.hasOwnProperty.call(result, permissionName)) {
            permissionsStatus[permissionName] = !!result[permissionName];
        }
    }

    const isCamera = permissionsStatus["android.permission.CAMERA"] || false;
    const isReadMediaAudio = permissionsStatus["android.permission.READ_MEDIA_AUDIO"] || false;                // Android 13, 14, 15, 16 or newer
    const isReadMediaVideo = permissionsStatus["android.permission.READ_MEDIA_VIDEO"] || false;                // Android 13, 14, 15, 16 or newer
    const isReadMediaImages = permissionsStatus["android.permission.READ_MEDIA_IMAGES"] || false;              // Android 13, 14, 15, 16 or newer
    const isReadExternalStorage = permissionsStatus["android.permission.READ_EXTERNAL_STORAGE"] || false;      // Android 10, 11, 12 
    const isWriteExternalStorage = permissionsStatus["android.permission.WRITE_EXTERNAL_STORAGE"] || false;    // Android 10 or older
    const isRecordAudio = permissionsStatus["android.permission.RECORD_AUDIO"] || false;
    const isModifyAudioSetting = permissionsStatus["android.permission.MODIFY_AUDIO_SETTINGS"] || false;
    const isManageExternalStorage = permissionsStatus["android.permission.MANAGE_EXTERNAL_STORAGE"] || false;  // Android 12 or newer


    console.log("isCamera: " + isCamera);
    console.log("isReadMediaAudio: " + isReadMediaAudio);
    console.log("isReadMediaVideo: " + isReadMediaVideo);
    console.log("isReadMediaImages: " + isReadMediaImages);
    console.log("isReadExternalStorage: " + isReadExternalStorage);
    console.log("isWriteExternalStorage: " + isWriteExternalStorage);
    console.log("isRecordAudio: " + isRecordAudio);
    console.log("isModifyAudioSetting: " + isModifyAudioSetting);
    console.log("isManageExternalStorage: " + isManageExternalStorage);
   // console.log("result: " + JSON.stringify(result));
  
}, (err) => {
    
    console.error(`${err}`);
    
});
```



<li>checkAndroidVersion:</li></ul>
<pre> 

```
cordova.plugin.CordovaSaveBlob.checkAndroidVersion(function (response) {
    switch (response) {
        case ">=14":
            // READ_MEDIA_AUDIO true
            // READ_MEDIA_VIDEO true
            // READ_MEDIA_IMAGES true
           
            break;
        case "13":
            // READ_MEDIA_AUDIO true
            // READ_MEDIA_VIDEO true
            // READ_MEDIA_IMAGES true

            break;
        case "12":
            // READ_MEDIA_AUDIO true
            // READ_MEDIA_VIDEO true
            // READ_MEDIA_IMAGES true
            // READ_EXTERNAL_STORAGE true

            break;
        case "11":
            // READ_EXTERNAL_STORAGE true

            break;
        case "<=10":
            // READ_EXTERNAL_STORAGE true
            // WRITE_EXTERNAL_STORAGE true

            break;

    }

}, function (error) {

});
```

</pre>
</details>




## `selectFiles(options)`

Opens a file picker to allow the user to select one or more files.

<details>
<summary>View description</summary>


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

</details>



<details>
<summary>View code example</summary>
<pre> 

 ```
cordova.plugin.CordovaSaveBlob.selectFiles({
    mime: "audio/*",
    isCopyFileToCache: false, // true response.cachedPath
    isBase64: false           // true  response.base64
}, function (response) {

    console.log("response: " + JSON.stringify(response));

    response.name || "";
    response.mimeType || "";
    response.base64 || "";
    response.uri || "";
    response.traditionalPath || "";
    response.cachedPath || "";
    response.size || 0;
    response.sizeReadable || "";

    ////////////////
    response.duration || 0;        // If mime video | audio
    response.humanDuration || "";  // If mime video | audio
    response.width || 0;           // if mime image
    response.height || 0;          // if mime image

}, function (error) {
    console.error(`${error}`);
});
```

</pre>
</details>



## `selectTargetPath()`

Opens a directory picker to allow the user to select a target directory for saving files using the Storage Access Framework (SAF).

<details>
<summary>View description</summary>

* **Description:** This method leverages Android's Storage Access Framework (SAF) to let the user pick a folder. Once a folder is selected, your app will be granted persistent permissions to read and write within that folder, which is crucial for file management on Android 10+ where direct file system access has become more restricted.
* **Parameters:** None.
* **Response (Success):**
    * `Object`: A JSON object containing information about the selected directory:
        * `uri` (String): The SAF URI (`content://`) of the selected directory.
        * `traditionalPath` (String, optional): The traditional file system path (e.g., `/storage/emulated/0/Download/`) if the selected directory is on primary external storage. This might be `null` if the traditional path cannot be determined or is not applicable.
* **Response (Error):**
    * `String`: An error message if the selection is canceled or fails.

---

</details>


<details>
<summary>View code example</summary>
<pre> 

 ```

cordova.plugin.CordovaSaveBlob.selectTargetPath({
    customNewFolder: "MyAppName", // a new folder will be created inside the folder selected by the user (default "")
}, function (response) {
    
    console.log("response: " + JSON.stringify(response));

    /*
    response.uri || "";             // base SAF
    response.uriCustom || "";       // SAF  If customNewFolder: is specified, not ""
    response.uriCustomPath || "";   // displayPath UI  If customNewFolder: is specified, not ""  ( Example Music/MyAppName )
    response.traditionalPath || ""; // Example /storage/emulated/0/Music/MyAppName
    */

}, function (error) {
    console.error(`${error}`);
});


```

</pre>
</details>




### `conversionSAFUri(uriPath)`
no longer used, replaced with copyFileToCache

<details>
<summary>View description</summary>
   
Converts a content URI (SAF) or an internal data URI to a traditional file system path.

* **Description:** This method attempts to resolve a given URI, which can be either a SAF URI (`content://...`) or an internal app data URI (`/data/user/0/...`), into its corresponding traditional, directly accessible file system path (e.g., `/storage/emulated/0/Music/song.mp3`). This is particularly useful for scenarios where other plugins or native APIs require an absolute file path rather than a content URI.
* **Parameters:**
    * `uriPath` (String): The URI string (`content://` or `/data/user/0/`) to convert.
* **Response (Success):**
    * `String`: The corresponding traditional file system path (e.g., `/storage/emulated/0/Download/filename.pdf`).
* **Response (Error):**
    * `String`: An error message if the conversion fails or the URI cannot be resolved to a traditional path.

---

</details>


## `downloadBlob(options)`

Saves Base64 encoded data as a file on the device.


<details>
<summary>View description</summary>


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

</details>


<details>
<summary>View code example</summary>
<pre> 

   
```
const base64Data = "data:image/png;base64, iVBORw0KGgoA......"; // example
const format = 2; // example png
let mimeType = format === 0 ? "mp3" : format === 1 ? "wav" : format === 2 ? "png" : format === 3 ? "jpg" : "mp4";
let finalFileName = "image_" + new Date().getTime();
const base64 = base64Data.replace(/^data:.*;base64,/, '');

cordova.plugin.CordovaSaveBlob.downloadBlob({
    saveToPath: "base SAF uri or customNewFolder",
    base64Data: base64,
    fileName: finalFileName,
    format: mimeType,
    isBase64: isSaveCreateBase64
}, function (response) {
    
    console.log("response: " + JSON.stringify(response));
    /*
    response.name || "";
    response.mimeType || "";
    response.base64 || "";
    response.uri || "";
    response.traditionalPath || "";
    response.cachedPath || "";
    response.size || 0;
    response.sizeReadable || "";

    ////////////////
    response.duration || 0;        // If mime video | audio
    response.humanDuration || "";  // If mime video | audio
    response.width || 0;           // if mime image
    response.height || 0;          // if mime image
    */

}, function (error) {
    console.error(`${error}`);
});

```

</pre>
</details>




### `downloadFile(options)`

Downloads a file from a remote URL and saves it to the device.


<details>
<summary>View description</summary>

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

</details>



<details>
<summary>View code example</summary>
<pre> 


```


let finalFilesName = "image_" + new Date().getTime();

cordova.plugin.CordovaSaveBlob.downloadFile({
    fileUrl: "url file",
    isBase64: false,
    saveToPath: "target path",
    fileName: finalFilesName
}, function (response) {
    self._Tags = tag;
    if (typeof response === "number") {

        console.log('Progress: ' + response + '%'); // send/update to UI
    }
    else {
        /*
    response.name || "";
    response.mimeType || "";
    response.base64 || "";
    response.uri || "";
    response.traditionalPath || "";
    response.cachedPath || "";
    response.size || 0;
    response.sizeReadable || "";

    ////////////////
    response.duration || 0;        // If mime video | audio
    response.humanDuration || "";  // If mime video | audio
    response.width || 0;           // if mime image
    response.height || 0;          // if mime image
    */
    }

}, function (error) {
    console.error(`${error}`);
});

```

</pre>
</details>


## `fileToBase64(options)`

Converts a local file (specified by its path or URI) to a Base64 encoded string.

<details>
<summary>View description</summary>

   
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

</details>



<details>
<summary>View code example</summary>
<pre> 

```
cordova.plugin.CordovaSaveBlob.fileToBase64({
    filePath: "uri file path"
}, function (response) {
  
    console.log(response.base64);

}, function (error) {
    console.error(`${error}`);
});
```

</pre>
</details>



## `openAppSettings()`

Opens the application's detailed settings page on Android.

<details>
<summary>View description</summary>

* **Description:** This method provides a shortcut for users to directly access your app's detailed settings screen where they can manually adjust app permissions, manage storage usage, or modify other settings if needed (e.g., after an initial permission denial).
* **Parameters:** None.
* **Response (Success):** None (Void).
* **Response (Error):**
    * `String`: An error message if the app settings page cannot be opened.

---

</details>

```
cordova.plugin.CordovaSaveBlob.openAppSettings();
```





### `registerWebRTC()`

Registers a `WebChromeClient` to handle WebRTC permission requests.

<details>
<summary>View description</summary>
   
* **Description:** This method is specifically designed for Android 14+ and ensures that WebRTC media permissions (such as camera and microphone access) are automatically granted when requested by the WebView. This is very helpful for enabling WebRTC functionalities within your Cordova app without manual user intervention.
* **Parameters:** None.
* **Response (Success):** None (Void).
* **Response (Error):** None.

---

</details>




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
  --variable IS_READ_M_AUDIO_PERMISSION=false \
  --variable IS_READ_M_VIDEO_PERMISSION=false \
  --variable IS_READ_M_IMAGES_PERMISSION=false \
  --variable IS_RECORD_AUDIO_PERMISSION=false \
  --variable IS_MODIFY_AUDIO_SET_PERMISSION=false \
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

