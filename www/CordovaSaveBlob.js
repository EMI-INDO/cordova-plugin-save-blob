var exec = require('cordova/exec');

exports.registerWebRTC = function (success, error) {
    exec(success, error, 'CordovaSaveBlob', 'registerWebRTC', []);
};

exports.checkAndRequestPermissions = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'checkAndRequestPermissions', [options]);
};

exports.hasPersistedPermission = function (success, error) {
    exec(success, error, 'CordovaSaveBlob', 'hasPersistedPermission', []); // v0.0.4
};

exports.revokeManageStorage = function (success, error) {
    exec(success, error, 'CordovaSaveBlob', 'revokeManageStorage', []); // v0.0.4
};

exports.selectFiles = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'selectFiles', [options]);
};

exports.selectTargetPath = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'selectTargetPath', [options]); // Update supports custom
};

exports.copyFileToCache = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'copyFileToCache', [options]); // v0.0.4
};

exports.downloadBlob = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'downloadBlob', [options]);
};

exports.downloadFile = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'downloadFile', [options]);
};

exports.fileToBase64 = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'fileToBase64', [options]);
};

exports.openAppSettings = function (success, error) {
    exec(success, error, 'CordovaSaveBlob', 'openAppSettings', []);
};

exports.openGallery = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'openGallery', [options]); // v0.0.4
};

exports.clearAppOldCache = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'clearAppOldCache', [options]); // v0.0.4
};

exports.createCategorizedFolder = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'createCategorizedFolder', [options]); // v0.0.4
};

exports.getFileMetadata = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'getFileMetadata', [options]); // v0.0.4
};

exports.showAudioListNative = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'showAudioListNative', [options]); // v0.0.4
};

exports.uploadFile = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'uploadFile', [options]); // v0.0.4
};

exports.saveFileContent = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'saveFileContent', [options]); // v0.0.4
};

exports.loadFileContent = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'loadFileContent', [options]); // v0.0.4
};

exports.createNewFile = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'createNewFile', [options]); // v0.0.4
};

exports.checkAndroidVersion = function (success, error) {
    exec(success, error, 'CordovaSaveBlob', 'checkAndroidVersion', []); // v0.0.4
};