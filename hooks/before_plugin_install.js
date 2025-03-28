#!/usr/bin/env node

module.exports = function (context) {
    const fs = require('fs');
    const path = require('path');

    console.log('Running before_plugin_install hook to modify AndroidManifest.xml...');

    const projectRoot = context.opts.projectRoot;
    const manifestPath = path.join(projectRoot, 'platforms', 'android', 'app', 'src', 'main', 'AndroidManifest.xml');

    if (!fs.existsSync(manifestPath)) {
        console.error('AndroidManifest.xml not found at:', manifestPath);
        return;
    }

    let manifest = fs.readFileSync(manifestPath, 'utf8');
    let modified = false;

    // 1. Tambahkan atribut xmlns:tools pada tag <manifest> jika belum ada
    if (!manifest.match(/xmlns:tools="http:\/\/schemas\.android\.com\/tools"/)) {
        manifest = manifest.replace(/<manifest/, '<manifest xmlns:tools="http://schemas.android.com/tools"');
        modified = true;
        console.log('Added xmlns:tools attribute to <manifest>');
    }

    // 2. Siapkan blok permission yang ingin disisipkan
    const permissionBlock = `
    <uses-permission android:name="android.permission.CAMERA"/>
    <uses-permission android:name="android.permission.RECORD_AUDIO"/>
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS"/>
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" tools:ignore="ManifestOrder,ScopedStorage" tools:remove="android:maxSdkVersion"/>
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" tools:remove="android:maxSdkVersion"/>
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO"/>
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES"/>
    <uses-permission android:name="android.permission.READ_MEDIA_VISUAL_USER_SELECTED"/>
    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO"/>
    `;

    // 3. Cek apakah blok permission sudah ada (berdasarkan salah satu permission)
    if (!manifest.includes('android.permission.CAMERA')) {
        const applicationIndex = manifest.indexOf('<application');
        if (applicationIndex !== -1) {
            // Sisipkan blok permission tepat di atas tag <application>
            manifest = manifest.slice(0, applicationIndex) + permissionBlock + '\n' + manifest.slice(applicationIndex);
            modified = true;
            console.log('Added permission block above <application> tag');
        } else {
            console.error('<application> tag not found in AndroidManifest.xml');
        }
    }

    // 4. Tambahkan atribut android:requestLegacyExternalStorage="true" pada <application> jika belum ada
    const applicationTagRegex = /<application([^>]*)>/;
    const appMatch = manifest.match(applicationTagRegex);
    if (appMatch && !appMatch[0].includes('android:requestLegacyExternalStorage="true"')) {
        const newAppTag = appMatch[0].replace('<application', '<application android:requestLegacyExternalStorage="true"');
        manifest = manifest.replace(applicationTagRegex, newAppTag);
        modified = true;
        console.log('Added android:requestLegacyExternalStorage attribute to <application>');
    }

    if (modified) {
        fs.writeFileSync(manifestPath, manifest, 'utf8');
        console.log('AndroidManifest.xml has been updated.');
    } else {
        console.log('No modifications were necessary for AndroidManifest.xml.');
    }
};
