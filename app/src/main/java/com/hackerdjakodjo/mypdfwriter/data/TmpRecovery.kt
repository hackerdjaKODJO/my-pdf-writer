package com.hackerdjakodjo.mypdfwriter.data

import java.io.File

/**
 * A complete `.tmp` file is a legitimate recovery source: `save()` writes it
 * fully before the rename/copy, and the copy can fail or the process can die in
 * between. Reading only the target would silently discard the newest edit.
 */
internal fun <T> readWithTmpRecovery(
    target: File,
    parse: (String) -> T?,
): T? {
    val tmp = File(target.path + ".tmp")
    for (file in listOf(target, tmp)) {
        if (!file.isFile) continue
        val value = runCatching { parse(file.readText()) }.getOrNull()
        if (value != null) return value
    }
    return null
}

/** Stable id from either `<id>.json` or `<id>.json.tmp`, or null. */
internal fun sidecarId(file: File): String? {
    val name = file.name
    return when {
        name.endsWith(".json") -> name.removeSuffix(".json")
        name.endsWith(".json.tmp") -> name.removeSuffix(".json.tmp")
        else -> null
    }?.takeIf { it.isNotBlank() }
}
