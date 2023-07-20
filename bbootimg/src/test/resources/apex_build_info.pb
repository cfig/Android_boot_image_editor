s(/.*)?           u:object_r:adbd_exec:s0

/apex_manifest\.pb u:object_r:system_file:s0
/ u:object_r:system_file:s0
/ 1000 1000 0755
/apex_manifest.json 1000 1000 0644
/apex_manifest.pb 1000 1000 0644
/bin 0 2000 0755
/bin/lazybox 0 2000 0755
"Å<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
  package="cc.cfig.lazybox" android:versionCode="2">
  <!-- APEX does not have classes.dex -->
  <application android:hasCode="false" />
</manifest>
*31231Rext4