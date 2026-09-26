package app.netstrip.core

data class UpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
)

fun parseUpdateManifest(json: String): UpdateManifest {
    val versionCode = longField(json, "versionCode")
    val versionName = stringField(json, "versionName")
    val apkUrl = stringField(json, "apkUrl")
    if (!apkUrl.startsWith("https://")) {
        throw IllegalArgumentException("Update address must be https")
    }
    return UpdateManifest(versionCode, versionName, apkUrl)
}

private fun stringField(json: String, name: String): String {
    val match = Regex(""""$name"\s*:\s*"([^"]*)"""").find(json)
        ?: throw IllegalArgumentException("Missing $name")
    return match.groupValues[1]
}

private fun longField(json: String, name: String): Long {
    val match = Regex(""""$name"\s*:\s*(\d+)""").find(json)
        ?: throw IllegalArgumentException("Missing $name")
    return match.groupValues[1].toLong()
}
