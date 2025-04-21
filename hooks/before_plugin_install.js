#!/usr/bin/env node

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
