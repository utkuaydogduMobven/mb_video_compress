package com.mobven.videocompress.mb_video_compress

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject
import java.io.File

class Utility(private val channelName: String) {

    fun isLandscapeImage(orientation: Int) = orientation != 90 && orientation != 270

    fun deleteFile(file: File) {
        if (file.exists()) {
            file.delete()
        }
    }

    fun timeStrToTimestamp(time: String): Long {
        val timeArr = time.split(":")
        val hour = Integer.parseInt(timeArr[0])
        val min = Integer.parseInt(timeArr[1])
        val secArr = timeArr[2].split(".")
        val sec = Integer.parseInt(secArr[0])
        val mSec = Integer.parseInt(secArr[1])

        val timeStamp = (hour * 3600 + min * 60 + sec) * 1000 + mSec
        return timeStamp.toLong()
    }

    /**
     * Reads the media metadata of [path] into the JSON the Dart side expects.
     *
     * `extractMetadata` returns **null** whenever the retriever cannot read a
     * key — a truncated or unfinalised output file, a result with no video
     * track, or simply a container this device's retriever will not parse.
     * Duration / width / height used to go straight into `Long.parseLong`,
     * which then threw `NumberFormatException: Cannot parse null string`. From
     * `onTranscodeCompleted` that runs on the main looper *outside* the method
     * channel's try/catch, so it killed the host app after an otherwise
     * successful transcode — and the Dart future never completed. Every field
     * is parsed defensively now, matching how `title` / `author` / `orientation`
     * were already handled.
     */
    fun getMediaInfoJson(context: Context, path: String): JSONObject {
        val file = File(path)
        val retriever = MediaMetadataRetriever()

        var durationStr: String? = null
        var title = ""
        var author = ""
        var widthStr: String? = null
        var heightStr: String? = null
        var orientation: String? = null
        try {
            retriever.setDataSource(context, Uri.fromFile(file))

            durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
            author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR) ?: ""
            widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            orientation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            } else {
                null
            }
        } finally {
            // In a `finally`: `setDataSource` throws on an unreadable file, and
            // the native retriever would otherwise be leaked.
            retriever.release()
        }

        if (durationStr == null || widthStr == null || heightStr == null) {
            Log.w(
                TAG,
                "getMediaInfoJson: incomplete metadata for $path " +
                        "(duration=$durationStr width=$widthStr height=$heightStr " +
                        "size=${file.length()}B exists=${file.exists()})"
            )
        }

        val duration = durationStr?.toLongOrNull() ?: 0L
        var width = widthStr?.toLongOrNull() ?: 0L
        var height = heightStr?.toLongOrNull() ?: 0L
        val filesize = file.length()
        val ori = orientation?.toIntOrNull()
        if (ori != null && isLandscapeImage(ori)) {
            val tmp = width
            width = height
            height = tmp
        }

        val json = JSONObject()

        json.put("path", path)
        json.put("title", title)
        json.put("author", author)
        json.put("width", width)
        json.put("height", height)
        json.put("duration", duration)
        json.put("filesize", filesize)
        if (ori != null) {
            json.put("orientation", ori)
        }

        return json
    }

    fun getBitmap(path: String, position: Long, result: MethodChannel.Result): Bitmap {
        var bitmap: Bitmap? = null
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(path)
            bitmap = retriever.getFrameAtTime(position, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (ex: IllegalArgumentException) {
            result.error(channelName, "Assume this is a corrupt video file", null)
        } catch (ex: RuntimeException) {
            result.error(channelName, "Assume this is a corrupt video file", null)
        } finally {
            try {
                retriever.release()
            } catch (ex: RuntimeException) {
                result.error(channelName, "Ignore failures while cleaning up", null)
            }
        }

        if (bitmap == null) result.success(emptyArray<Int>())

        val width = bitmap!!.width
        val height = bitmap.height
        val max = Math.max(width, height)
        if (max > 512) {
            val scale = 512f / max
            val w = Math.round(scale * width)
            val h = Math.round(scale * height)
            bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true)
        }

        return bitmap!!
    }

    fun getFileNameWithGifExtension(path: String): String {
        val file = File(path)
        var fileName = ""
        val gifSuffix = "gif"
        val dotGifSuffix = ".$gifSuffix"

        if (file.exists()) {
            val name = file.name
            fileName = name.replaceAfterLast(".", gifSuffix)

            if (!fileName.endsWith(dotGifSuffix)) {
                fileName += dotGifSuffix
            }
        }
        return fileName
    }

    fun deleteAllCache(context: Context, result: MethodChannel.Result) {
        val dir = context.getExternalFilesDir("mb_video_compress")
        result.success(dir?.deleteRecursively())
    }

    companion object {
        private const val TAG = "mb_video_compress"
    }
}