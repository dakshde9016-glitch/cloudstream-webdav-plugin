plugins {
    id("com.android.library")
    id("kotlin-android")
    id("com.lagradost.cloudstream3.gradle")
}

cloudstream {
    description = "Streams movies and series directly from private WebDAV server"
    authors = listOf("Daksh")
    version = 1
    status = 2
    tvTypes = listOf("Movie", "TvSeries")
    language = "en"
    iconUrl = "https://raw.githubusercontent.com/recloudstream/cloudstream/master/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png"
}

android {
    namespace = "com.daksh.webdav"
    defaultConfig {
        versionCode = 1
        versionName = "1.0.0"
    }
}
