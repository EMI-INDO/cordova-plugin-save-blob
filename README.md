
# Plugin Variable permissions

A Cordova Java plugin to automatically and dynamically add audio/video permissions, minimizing unnecessary permissions so you don't have to fill out the declaration form when uploading your APK/AAB to the Google Play Console.

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
