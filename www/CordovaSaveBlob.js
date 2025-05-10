var exec = require('cordova/exec');

exports.registerWebRTC = function (success, error) {
    exec(success, error, 'CordovaSaveBlob', 'registerWebRTC', []);
};

exports.checkAndRequestPermissions = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'checkAndRequestPermissions', [options]);
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

exports.goToFileLocation = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'goToFileLocation', [options]);
};

exports.isSupportExternalStorage = function (success, error) {
    exec(success, error, 'CordovaSaveBlob', 'isSupportExternalStorage', []);
};

exports.fileToBase64 = function (options, success, error) {
    exec(success, error, 'CordovaSaveBlob', 'fileToBase64', [options]);
};