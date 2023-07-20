package com.android.apex

import com.android.apex.ApexBuildInfoOuterClass.ApexBuildInfo
import com.google.protobuf.ByteString
import java.nio.charset.Charset

/*
  string apexer_command_line = 1;
  bytes file_contexts = 2;
  bytes canned_fs_config = 3;
  bytes android_manifest = 4;
  string min_sdk_version = 5;
  string target_sdk_version = 6;
  bool no_hashtree = 7;
  string override_apk_package_name = 8;
  string logging_parent = 9;
  string payload_fs_type = 10;
}

 */
data class ApexBuildInfoDataClass(
    var apexer_command_line: String = "", //string
    var file_contexts: String = "", //bytes
    var canned_fs_config: String = "",//bytes
    var android_manifest: String = "",//bytes
    var min_sdk_version: String = "",//string
    var target_sdk_version: String = "",//string
    var no_hashtree: Boolean = false,
    var override_apk_package_name: String = "",//string
    var logging_parent: String = "",//string
    var payload_fs_type: String = "",//string
) {
    fun toPb(): ApexBuildInfo {
       return ApexBuildInfo.newBuilder()
           .setApexerCommandLine(this.apexer_command_line)
           .setFileContexts(ByteString.copyFrom(this.file_contexts, Charset.defaultCharset()))
           .setCannedFsConfig(ByteString.copyFrom(this.canned_fs_config, Charset.defaultCharset()))
           .setAndroidManifest(ByteString.copyFrom(this.android_manifest, Charset.defaultCharset()))
           .setMinSdkVersion(this.min_sdk_version)
           .setTargetSdkVersion(this.target_sdk_version)
           .setNoHashtree(this.no_hashtree)
           .setOverrideApkPackageName(this.override_apk_package_name)
           .setLoggingParent(this.logging_parent)
           .setPayloadFsType(this.payload_fs_type)
           .build()
    }
    companion object {
        fun fromOuterClass(pb: ApexBuildInfoOuterClass.ApexBuildInfo): ApexBuildInfoDataClass? {
            return ApexBuildInfoDataClass(
                pb.apexerCommandLine,
                pb.fileContexts.toStringUtf8(),
                pb.cannedFsConfig.toStringUtf8(),
                pb.androidManifest.toStringUtf8(),
                pb.minSdkVersion,
                pb.targetSdkVersion,
                pb.noHashtree,
                pb.overrideApkPackageName,
                pb.loggingParent,
                pb.payloadFsType
            )
        }
    }
}
