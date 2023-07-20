package com.android.apex

import org.slf4j.LoggerFactory

/*
message ApexManifest {
  string name = 1;
  int64 version = 2;
  string preInstallHook = 3;
  string postInstallHook = 4;
  string versionName = 5;
  bool noCode = 6;
  repeated string provideNativeLibs = 7;
  repeated string requireNativeLibs = 8;
  repeated string jniLibs = 9;
  repeated string requireSharedApexLibs = 10;
  bool provideSharedApexLibs = 11;
  message CompressedApexMetadata {
    string originalApexDigest = 1;
  }
  CompressedApexMetadata capexMetadata = 12;
  bool supportsRebootlessUpdate = 13;
}

 */
data class ApexManifestDataClass(
    /* 1 */ var name: String = "",
    /* 2 */ var version: Long = 0,
    /* 3 */ var preInstallHook: String = "",
    /* 4 */ var postInstallHook: String = "",
    /* 5 */ var versionName: String = "",
    /* 6 */ var noCode: Boolean = false,
    /* 7 */ var provideNativeLibs: MutableList<String> = mutableListOf(),
    /* 8 */ var requireNativeLibs: MutableList<String> = mutableListOf(),
    /* 9 */ var jniLibs: MutableList<String> = mutableListOf(),
    /* 10 */ var requireSharedApexLibs: MutableList<String> = mutableListOf(),
    /* 11 */ var provideSharedApexLibs: Boolean = false,
    /* 12 */ var capexMetadata: CompressedApexMetadata = CompressedApexMetadata(),
    /* 13 */ var supportsRebootlessUpdate: Boolean = false,
) {
    data class CompressedApexMetadata(var originalApexDigest: String = "") {
        fun toPb(): ApexManifestOuterClass.ApexManifest.CompressedApexMetadata {
           return ApexManifestOuterClass.ApexManifest.CompressedApexMetadata.newBuilder()
               .setOriginalApexDigest(this.originalApexDigest)
               .build()
        }
        companion object {
            fun fromOuterClass(pb: ApexManifestOuterClass.ApexManifest.CompressedApexMetadata): CompressedApexMetadata {
                return CompressedApexMetadata(pb.originalApexDigest)
            }
        }
    }

    fun toPb(): ApexManifestOuterClass.ApexManifest {
        return ApexManifestOuterClass.ApexManifest.newBuilder()
            .setName(this.name)
            .setVersion(this.version)
            .setPreInstallHook(this.preInstallHook)
            .setPostInstallHook(this.postInstallHook)
            .setVersionName(this.versionName)
            .setNoCode(this.noCode)
            .setProvideSharedApexLibs(this.provideSharedApexLibs)
            .setCapexMetadata(this.capexMetadata.toPb())
            .setSupportsRebootlessUpdate(this.supportsRebootlessUpdate)
            .also {
                this.provideNativeLibs.forEachIndexed { _, s ->
                    it.addProvideNativeLibs(s)
                }
            }
            .also {
                this.requireNativeLibs.forEachIndexed { _, s ->
                    it.addRequireNativeLibs(s)
                }
            }
            .also {
                this.jniLibs.forEachIndexed { _, s ->
                    it.addJniLibs(s)
                }
            }
            .also {
                this.requireSharedApexLibs.forEachIndexed { _, s ->
                    it.addRequireSharedApexLibs(s)
                }
            }
            .build()
    }

    companion object {
        private val log = LoggerFactory.getLogger(ApexManifestDataClass::class.java)
        fun fromOuterClass(pb: ApexManifestOuterClass.ApexManifest): ApexManifestDataClass? {
            return ApexManifestDataClass(
                pb.name,
                pb.version,
                pb.preInstallHook,
                pb.postInstallHook,
                pb.versionName,
                pb.noCode,
                pb.provideNativeLibsList,
                pb.requireNativeLibsList,
                pb.jniLibsList,
                pb.requireSharedApexLibsList,
                pb.provideSharedApexLibs,
                CompressedApexMetadata.fromOuterClass(pb.capexMetadata),
                pb.supportsRebootlessUpdate
            )
        }
    }
}
