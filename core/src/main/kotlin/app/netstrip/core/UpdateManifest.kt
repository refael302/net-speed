package app.netstrip.core

data class UpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val notes: String = "",
)

fun parseUpdateManifest(json: String): UpdateManifest {
    val versionCode = longField(json, "versionCode")
    val versionName = stringField(json, "versionName")
    val apkUrl = stringField(json, "apkUrl")
    if (!apkUrl.startsWith("https://")) {
        throw IllegalArgumentException("Update address must be https")
    }
    val notes = optionalStringField(json, "notes").orEmpty().replace("\\n", "\n")
    return UpdateManifest(versionCode, versionName, apkUrl, notes)
}

private fun stringField(json: String, name: String): String {
    return optionalStringField(json, name) ?: throw IllegalArgumentException("Missing $name")
}

private fun optionalStringField(json: String, name: String): String? {
    val match = Regex(""""$name"\s*:\s*"([^"]*)"""").find(json) ?: return null
    return match.groupValues[1]
}

private fun longField(json: String, name: String): Long {
    val match = Regex(""""$name"\s*:\s*(\d+)""").find(json)
        ?: throw IllegalArgumentException("Missing $name")
    return match.groupValues[1].toLong()
}
