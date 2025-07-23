var exec = require('cordova/exec');

exports.registerWebRTC = function (success, error) {
    exec(success, error, 'CordovaSaveBlob', 'registerWebRTC', []);
};

exports.checkAndRequestPermissions = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'checkAndRequestPermissions', [options]);
};

exports.revokeManageStorage = function (success, error) {
    exec(success, error, 'CordovaSaveBlob', 'revokeManageStorage', []);
};

exports.selectFiles = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'selectFiles', [options]);
};

exports.selectTargetPath = function (success, error) {
    exec(success, error, 'CordovaSaveBlob', 'selectTargetPath', []);
};

exports.conversionSAFUri = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'conversionSAFUri', [options]);
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
    exec(success, error, 'CordovaSaveBlob', 'openGallery', [options]);
};

exports.clearAppOldCache = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'clearAppOldCache', [options]);
};

exports.createCategorizedFolder = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'createCategorizedFolder', [options]);
};

exports.getFileMetadata = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'getFileMetadata', [options]);
};

exports.showAudioListNative = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'showAudioListNative', [options]);
};

exports.uploadFile = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'uploadFile', [options]);
};