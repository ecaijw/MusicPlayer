package com.localplayer.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.localplayer.app.util.isSupportedAudio
import java.text.Collator

class LibraryRepository(private val context: Context) {
    private val collator = Collator.getInstance().apply {
        strength = Collator.PRIMARY
    }

    fun persistTreePermission(treeUri: Uri) {
        val resolver = context.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        resolver.takePersistableUriPermission(treeUri, flags)
        resolver.persistedUriPermissions
            .filter { it.isReadPermission && it.uri != treeUri }
            .forEach { permission ->
                try {
                    resolver.releasePersistableUriPermission(
                        permission.uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // 旧目录可能已经被系统收回
                }
            }
    }

    fun isTreeAccessible(treeUri: Uri): Boolean {
        val hasPermission = context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission
        }
        if (!hasPermission) return false
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        return tree.exists() && tree.isDirectory
    }

    fun directoryName(treeUri: Uri): String {
        return DocumentFile.fromTreeUri(context, treeUri)?.name ?: "已选择的目录"
    }

    fun loadFromTree(treeUri: Uri): List<AudioFile> {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        if (!tree.exists() || !tree.isDirectory) return emptyList()
        return tree.listFiles()
            .mapNotNull { file ->
                if (!file.isFile) return@mapNotNull null
                val name = file.name ?: return@mapNotNull null
                if (!isSupportedAudio(name, file.type)) return@mapNotNull null
                AudioFile(
                    documentUri = file.uri,
                    displayName = name,
                    mimeType = file.type
                )
            }
            .sortedWith { a, b -> collator.compare(a.displayName, b.displayName) }
    }

    /**
     * v2 预留：从文件管理器打开的单文件加载同目录列表。
     * v1 不调用。
     */
    @Suppress("unused")
    fun loadFromOpenedFile(@Suppress("UNUSED_PARAMETER") fileUri: Uri): List<AudioFile> {
        return emptyList()
    }
}
