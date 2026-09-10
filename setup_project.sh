#!/bin/bash
mkdir -p app/src/main/java/com/thibautfihey/trackme
mkdir -p app/src/main/res/layout app/src/main/res/values app/src/main/res/mipmap-xxxhdpi
mkdir -p .github/workflows gradle/wrapper

cat > settings.gradle <<'EOF'
include ':app'
EOF

cat > build.gradle <<'EOF'
buildscript {
    ext.kotlin_version = '1.9.10'
    repositories { google(); mavenCentral() }
    dependencies { classpath 'com.android.tools.build:gradle:8.1.2'; classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version" }
}
allprojects { repositories { google(); mavenCentral() } }
task clean(type: Delete) { delete rootProject.buildDir }
EOF

cat > gradle.properties <<'EOF'
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRResources=true
EOF

cat > gradle/wrapper/gradle-wrapper.properties <<'EOF'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.0-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF

cat > app/build.gradle <<'EOF'
plugins { id 'com.android.application'; id 'org.jetbrains.kotlin.android' }
android {
    namespace 'com.thibautfihey.trackme'
    compileSdk 34
    defaultConfig { applicationId "com.thibautfihey.trackme"; minSdk 24; targetSdk 34; versionCode 5; versionName "5.0-remote" }
    buildTypes { debug { debuggable true } release { minifyEnabled false } }
    compileOptions { sourceCompatibility JavaVersion.VERSION_1_8; targetCompatibility JavaVersion.VERSION_1_8 }
    kotlinOptions { jvmTarget = '1.8' }
}
dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'org.osmdroid:osmdroid-android:6.1.18'
    implementation 'com.google.android.gms:play-services-location:21.0.1'
    implementation 'androidx.localbroadcastmanager:localbroadcastmanager:1.1.0'
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'com.google.code.gson:gson:2.10.1'
}
EOF

cat > app/src/main/AndroidManifest.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.SEND_SMS"/>
    <uses-permission android:name="android.permission.RECEIVE_SMS"/>
    <uses-permission android:name="android.permission.READ_SMS"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION"/>
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
    <application android:allowBackup="true" android:label="TrackMe" android:theme="@style/Theme.Material3.DayNight">
        <activity android:name=".MainActivity" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity>
        <service android:name=".ShareLocationService" android:foregroundServiceType="location"/>
        <receiver android:name=".SmsReceiver" android:exported="true" android:permission="android.permission.BROADCAST_SMS"><intent-filter android:priority="999"><action android:name="android.provider.Telephony.SMS_RECEIVE"/></intent-filter></receiver>
    </application>
</manifest>
EOF

# --- Tous les fichiers Kotlin sont dans le projet que j'ai généré, je les recopie ici via base64 pour éviter les erreurs ---
