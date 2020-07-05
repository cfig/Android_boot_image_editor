#! /bin/sh
# speed up with mirrors within the biggest LAN

git apply - <<EOF

diff --git a/aosp/boot_signer/build.gradle.kts b/aosp/boot_signer/build.gradle.kts
index 0472c6e..788257d 100644
--- a/aosp/boot_signer/build.gradle.kts
+++ b/aosp/boot_signer/build.gradle.kts
@@ -5,7 +5,8 @@ plugins {
 }
 
 repositories {
-    jcenter()
+    maven { url = uri("http://maven.aliyun.com/nexus/content/groups/public/") }
+    maven { url = uri("http://maven.aliyun.com/nexus/content/repositories/jcenter") }
 }
 
 dependencies {
diff --git a/bbootimg/build.gradle.kts b/bbootimg/build.gradle.kts
index 3362dd7..9906c00 100644
--- a/bbootimg/build.gradle.kts
+++ b/bbootimg/build.gradle.kts
@@ -6,7 +6,8 @@ plugins {
 }
 
 repositories {
-    jcenter()
+    maven { url = uri("http://maven.aliyun.com/nexus/content/groups/public/") }
+    maven { url = uri("http://maven.aliyun.com/nexus/content/repositories/jcenter") }
 }
 
 dependencies {
diff --git a/build.gradle.kts b/build.gradle.kts
index bf076be..973c84a 100644
--- a/build.gradle.kts
+++ b/build.gradle.kts
@@ -14,7 +14,8 @@ if (parseGradleVersion(gradle.gradleVersion) < 5) {
 
 buildscript {
     repositories {
-        jcenter()
+        maven { url = uri("http://maven.aliyun.com/nexus/content/groups/public/") }
+        maven { url = uri("http://maven.aliyun.com/nexus/content/repositories/jcenter") }
     }
     dependencies {
         classpath("org.apache.commons:commons-exec:1.3")

EOF

