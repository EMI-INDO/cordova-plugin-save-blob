#!/usr/bin/env node

module.exports = function (context) {
    const fs   = require('fs');
    const path = require('path');

    const vars = context.opts.variables || {};
    let addCamera         = String(vars.IS_CAMERA_PERMISSION).toLowerCase() === 'true';
    let addReadVideo      = String(vars.IS_READ_M_VIDEO_PERMISSION).toLowerCase() === 'true';
    let addReadImages     = String(vars.IS_READ_M_IMAGES_PERMISSION).toLowerCase() === 'true';
    let addManageStorage  = String(vars.IS_MANAGE_STORAGE_PERMISSION).toLowerCase() === 'true';

    if (!addCamera || !addReadVideo || !addReadImages || !addManageStorage) {
        try {
            const androidJsonPath = path.join(
                context.opts.projectRoot,
                'platforms', 'android',
                'android.json'
            );
            if (fs.existsSync(androidJsonPath)) {
                const androidJson = JSON.parse(fs.readFileSync(androidJsonPath, 'utf8'));
                const installed   = androidJson.installed_plugins || {};
                const me          = installed['cordova-plugin-save-blob'] || {};

                if (!addCamera) {
                    addCamera = String(me.IS_CAMERA_PERMISSION).toLowerCase() === 'true';
                }
                if (!addReadVideo) {
                    addReadVideo = String(me.IS_READ_M_VIDEO_PERMISSION).toLowerCase() === 'true';
                }
                if (!addReadImages) {
                    addReadImages = String(me.IS_READ_M_IMAGES_PERMISSION).toLowerCase() === 'true';
                }
                if (!addManageStorage) {
                    addManageStorage = String(me.IS_MANAGE_STORAGE_PERMISSION).toLowerCase() === 'true';
                }
            }
        } catch (e) {
            console.warn('[save-blob-plugin] Fallback read android.json failed:', e);
        }
    } else {
       // console.log('[save-blob-plugin] Read flags from context.opts.variables');
    }

    const manifestPath = path.join(
        context.opts.projectRoot,
        'platforms', 'android',
        'app', 'src', 'main',
        'AndroidManifest.xml'
    );
    if (!fs.existsSync(manifestPath)) {
        console.error('[save-blob-plugin] AndroidManifest.xml not found:', manifestPath);
        return;
    }

    let xml      = fs.readFileSync(manifestPath, 'utf8');
    let modified = false;

    // Add the tools namespace if it doesn't already exist.
    if (!xml.includes('xmlns:tools="http://schemas.android.com/tools"')) {
        xml = xml.replace(
            /<manifest/,
            '<manifest xmlns:tools="http://schemas.android.com/tools"'
        );
        modified = true;
        console.log('[save-blob-plugin] Added xmlns:tools');
    }

    // plugin's built-in permissions, no form is required when uploading the app to the play store console.
    const perms = [
        { name: 'android.permission.READ_MEDIA_AUDIO' },
        { name: 'android.permission.RECORD_AUDIO' },
        { name: 'android.permission.MODIFY_AUDIO_SETTINGS' },
        { name: 'android.permission.READ_EXTERNAL_STORAGE', attrs: 'android:maxSdkVersion="32" tools:ignore="ScopedStorage"' },
        { name: 'android.permission.WRITE_EXTERNAL_STORAGE', attrs: 'android:maxSdkVersion="32" tools:ignore="ScopedStorage"' }
    ];

    // permissions based on plugin variables, requires a form when uploading the app to the play store console.
    if (addCamera)        perms.push({ name: 'android.permission.CAMERA' });
    if (addReadVideo)     perms.push({ name: 'android.permission.READ_MEDIA_VIDEO', attrs: 'tools:ignore="SelectedPhotoAccess"' }); // <-- PERUBAHAN DI SINI
    if (addReadImages)    perms.push({ name: 'android.permission.READ_MEDIA_IMAGES', attrs: 'tools:ignore="SelectedPhotoAccess"' });
    if (addManageStorage) perms.push({ name: 'android.permission.MANAGE_EXTERNAL_STORAGE', attrs: 'tools:ignore="ScopedStorage"' });

    const toAdd = perms
        .filter(p => !xml.includes(p.name))
        .map(p => {
            const attrs = p.attrs ? ' ' + p.attrs : '';
            return `    <uses-permission android:name="${p.name}"${attrs} />`;
        });

    if (toAdd.length) {
        const idx = xml.indexOf('<application');
        if (idx !== -1) {
            xml = xml.slice(0, idx)
                + '\n' + toAdd.join('\n') + '\n'
                + xml.slice(idx);
            modified = true;
        } else {
            console.error('[save-blob-plugin] <application> tag not found');
        }
    } else {
        console.log('[save-blob-plugin] No new permissions to add');
    }

    //  Add requestLegacyExternalStorage if it doesn't exist
    const appTagMatch = xml.match(/<application[^>]*>/);
    if (appTagMatch && !appTagMatch[0].includes('requestLegacyExternalStorage')) {
        const newApp = appTagMatch[0].replace(
            '<application',
            '<application android:requestLegacyExternalStorage="true"'
        );
        xml = xml.replace(appTagMatch[0], newApp);
        modified = true;
    }

    if (modified) {
        fs.writeFileSync(manifestPath, xml, 'utf8');
        console.log('[save-blob-plugin] AndroidManifest.xml updated');
    } else {
        console.log('[save-blob-plugin] No modifications needed');
    }
};