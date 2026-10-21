package com.personal.bookreader.reader

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.publication.services.coverFitting
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.epub.EpubParser

data class ParsedEpubMetadata(
    val title: String,
    val author: String?,
    val coverPath: String?
)

class EpubImportService {

    suspend fun parseAndExtract(
        context: Context,
        epubFile: File,
        fileHash: String,
        fallbackTitle: String
    ): Result<ParsedEpubMetadata> {
        val assetRetriever = AssetRetriever(context.contentResolver, DefaultHttpClient())
        val assetResult = assetRetriever.retrieve(epubFile)
        val asset = assetResult.getOrNull()
            ?: return Result.failure(IOException("Invalid EPUB: unable to read asset"))

        try {
            val parser = EpubParser()
            val opener = PublicationOpener(publicationParser = parser)
            val pubResult = opener.open(asset, allowUserInteraction = false)
            val publication = pubResult.getOrNull()
                ?: return Result.failure(IOException("Invalid EPUB: failed to open publication"))

            try {
                val extractedTitle = publication.metadata.title?.trim()
                val title = if (!extractedTitle.isNullOrEmpty()) extractedTitle else fallbackTitle
                val author = publication.metadata.authors.firstOrNull()?.name?.trim()?.ifEmpty { null }

                // Downscale cover to ~300px wide, avoid keeping full-size bitmaps in memory
                val coverBitmap = publication.coverFitting(Size(300, 450)) ?: publication.cover()
                var savedCoverPath: String? = null

                if (coverBitmap != null) {
                    val targetWidth = 300
                    val scaledBitmap = if (coverBitmap.width > targetWidth) {
                        val targetHeight = (coverBitmap.height * (targetWidth.toFloat() / coverBitmap.width)).toInt()
                        Bitmap.createScaledBitmap(coverBitmap, targetWidth, targetHeight, true).also {
                            if (it != coverBitmap) coverBitmap.recycle()
                        }
                    } else {
                        coverBitmap
                    }

                    try {
                        val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
                        val coverFile = File(coversDir, "$fileHash.jpg")
                        FileOutputStream(coverFile).use { out ->
                            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        }
                        savedCoverPath = coverFile.absolutePath
                    } finally {
                        scaledBitmap.recycle()
                    }
                }

                return Result.success(
                    ParsedEpubMetadata(
                        title = title,
                        author = author,
                        coverPath = savedCoverPath
                    )
                )
            } finally {
                publication.close()
            }
        } finally {
            asset.close()
        }
    }
}
