#!/usr/bin/env node

module.exports = function (context) {
    const fs   = require('fs');
    const path = require('path');

    // 1) Ambil flags dari context.opts.variables
    const vars = context.opts.variables || {};
    let addCamera     = String(vars.IS_CAMERA_PERMISSION).toLowerCase() === 'true';
    let addReadVideo  = String(vars.IS_READ_MEDIA_VIDEO).toLowerCase() === 'true';
    let addReadImages = String(vars.IS_READ_MEDIA_IMAGES).toLowerCase() === 'true';

    // 2) Fallback: jika belum terdeteksi, baca platforms/android/android.json
    if (!addCamera || !addReadVideo || !addReadImages) {
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
                    console.log(`[save-blob-plugin] Fallback IS_CAMERA_PERMISSION=${addCamera}`);
                }
                if (!addReadVideo) {
                    addReadVideo = String(me.IS_READ_MEDIA_VIDEO).toLowerCase() === 'true';
                    console.log(`[save-blob-plugin] Fallback IS_READ_MEDIA_VIDEO=${addReadVideo}`);
                }
                if (!addReadImages) {
                    addReadImages = String(me.IS_READ_MEDIA_IMAGES).toLowerCase() === 'true';
                    console.log(`[save-blob-plugin] Fallback IS_READ_MEDIA_IMAGES=${addReadImages}`);
                }
            }
        } catch (e) {
            console.warn('[save-blob-plugin] Fallback read android.json failed:', e);
        }
    } else {
        console.log('[save-blob-plugin] Read flags from context.opts.variables');
    }

    // 3) Path ke AndroidManifest.xml
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

    // 4) Baca isi manifest
    let xml      = fs.readFileSync(manifestPath, 'utf8');
    let modified = false;

    // 5) Tambah namespace tools jika belum ada
    if (!xml.includes('xmlns:tools="http://schemas.android.com/tools"')) {
        xml = xml.replace(
            /<manifest/,
            '<manifest xmlns:tools="http://schemas.android.com/tools"'
        );
        modified = true;
        console.log('[save-blob-plugin] Added xmlns:tools');
    }

    // 6) Definisikan daftar permission dasar + kondisional <uses-permission android:name="android.permission.READ_MEDIA_AUDIO"/>
    const perms = [
        { name: 'android.permission.READ_MEDIA_AUDIO' },
        { name: 'android.permission.RECORD_AUDIO' },
        { name: 'android.permission.MODIFY_AUDIO_SETTINGS' },
        { name: 'android.permission.READ_EXTERNAL_STORAGE', attrs: 'android:maxSdkVersion="32"' },
        { name: 'android.permission.WRITE_EXTERNAL_STORAGE', attrs: 'android:maxSdkVersion="32" tools:ignore="ScopedStorage"' }
    ];
    if (addCamera)     perms.push({ name: 'android.permission.CAMERA' });
    if (addReadVideo)  perms.push({ name: 'android.permission.READ_MEDIA_VIDEO' });
    if (addReadImages) perms.push({ name: 'android.permission.READ_MEDIA_IMAGES' });

    // 7) Bangun tag <uses-permission> yang belum ada
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
            console.log('[save-blob-plugin] Added permissions:\n' + toAdd.join('\n'));
        } else {
            console.error('[save-blob-plugin] <application> tag not found');
        }
    } else {
        console.log('[save-blob-plugin] No new permissions to add');
    }

    // 8) Tambahkan requestLegacyExternalStorage jika belum ada
    const appTagMatch = xml.match(/<application[^>]*>/);
    if (appTagMatch && !appTagMatch[0].includes('requestLegacyExternalStorage')) {
        const newApp = appTagMatch[0].replace(
            '<application',
            '<application android:requestLegacyExternalStorage="true"'
        );
        xml = xml.replace(appTagMatch[0], newApp);
        modified = true;
        console.log('[save-blob-plugin] Added requestLegacyExternalStorage');
    }

    // 9) Simpan perubahan kalau ada
    if (modified) {
        fs.writeFileSync(manifestPath, xml, 'utf8');
        console.log('[save-blob-plugin] AndroidManifest.xml updated');
    } else {
        console.log('[save-blob-plugin] No modifications needed');
    }
};






/*
module.exports = function (context) {
    const fs = require('fs');
    const path = require('path');

    const projectRoot = context.opts.projectRoot;
    const manifestPath = path.join(
        projectRoot,
        'platforms',
        'android',
        'app',
        'src',
        'main',
        'AndroidManifest.xml'
    );

    if (!fs.existsSync(manifestPath)) {
        console.error('AndroidManifest.xml not found at:', manifestPath);
        return;
    }

    let manifest = fs.readFileSync(manifestPath, 'utf8');
    let modified = false;

    if (!/xmlns:tools="http:\/\/schemas\.android\.com\/tools"/.test(manifest)) {
        manifest = manifest.replace(
            /<manifest/,
            '<manifest xmlns:tools="http://schemas.android.com/tools"'
        );
        modified = true;
       // console.log('Added xmlns:tools attribute to <manifest>');
    }

    const permissions = [
        { name: 'android.permission.RECORD_AUDIO' },
        { name: 'android.permission.MODIFY_AUDIO_SETTINGS' },
        { name: 'android.permission.READ_EXTERNAL_STORAGE', attrs: 'android:maxSdkVersion="32"' },
        { name: 'android.permission.WRITE_EXTERNAL_STORAGE', attrs: 'android:maxSdkVersion="32" tools:ignore="ScopedStorage"' },
        { name: 'android.permission.READ_MEDIA_VIDEO' },
        { name: 'android.permission.READ_MEDIA_IMAGES' },
        { name: 'android.permission.READ_MEDIA_VISUAL_USER_SELECTED' },
        { name: 'android.permission.READ_MEDIA_AUDIO' }
    ];

    // Build permission blocks that do not yet exist
    const toAdd = permissions
        .filter(perm => !manifest.includes(perm.name))
        .map(perm => {
            const attrs = perm.attrs ? ' ' + perm.attrs : '';
            return `    <uses-permission android:name="${perm.name}"${attrs}/>`;
        });

    if (toAdd.length) {
        const applicationIndex = manifest.indexOf('<application');
        if (applicationIndex !== -1) {
            const block = '\n' + toAdd.join('\n') + '\n';
            manifest = manifest.slice(0, applicationIndex) + block + manifest.slice(applicationIndex);
            modified = true;
        } else {
            console.error('<application> tag not found in AndroidManifest.xml');
        }
    } else {
        console.log('No new permissions to add');
    }

    const appTagRegex = /<application([^>]*)>/;
    const appMatch = manifest.match(appTagRegex);
    if (appMatch && !/android:requestLegacyExternalStorage="true"/.test(appMatch[0])) {
        const newAppTag = appMatch[0].replace(
            '<application',
            '<application android:requestLegacyExternalStorage="true"'
        );
        manifest = manifest.replace(appTagRegex, newAppTag);
        modified = true;
    }

    if (modified) {
        fs.writeFileSync(manifestPath, manifest, 'utf8');
        console.log('AndroidManifest.xml has been updated.');
    } else {
        console.log('No modifications were necessary for AndroidManifest.xml.');
    }
};


*/