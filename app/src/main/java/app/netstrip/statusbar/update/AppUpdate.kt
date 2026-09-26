package app.netstrip.statusbar.update

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import app.netstrip.core.UpdateManifest
import app.netstrip.core.parseUpdateManifest
import app.netstrip.statusbar.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val manifest: UpdateManifest) : UpdateState
    data object Downloading : UpdateState
    data object Failed : UpdateState
}

object AppUpdate {
    fun fetchManifest(): UpdateManifest {
        val connection = get(BuildConfig.UPDATE_MANIFEST_URL)
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        return parseUpdateManifest(body)
    }

    fun downloadApk(context: Context, apkUrl: String): File {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(directory, "netstrip-update.apk")
        val connection = get(apkUrl)
        try {
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_APK_BYTES) {
                            file.delete()
                            throw IllegalStateException("Update file is too large")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        return file
    }

    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun installedVersionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }

    private fun get(address: String): HttpURLConnection {
        if (!address.startsWith("https://")) throw IllegalArgumentException("Update address must be https")
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "NetStrip")
        }
        connection.connect()
        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            throw IllegalStateException("Update request failed")
        }
        return connection
    }

    private const val MAX_APK_BYTES = 40L * 1024L * 1024L
}
