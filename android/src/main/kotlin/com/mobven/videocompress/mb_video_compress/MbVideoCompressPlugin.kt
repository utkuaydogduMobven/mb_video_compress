package com.mobven.videocompress.mb_video_compress

import android.content.Context
import android.net.Uri
import android.util.Log
import com.otaliastudios.transcoder.Transcoder
import com.otaliastudios.transcoder.TranscoderListener
import com.otaliastudios.transcoder.source.TrimDataSource
import com.otaliastudios.transcoder.source.UriDataSource
import com.otaliastudios.transcoder.strategy.DefaultAudioStrategy
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategy
import com.otaliastudios.transcoder.strategy.RemoveTrackStrategy
import com.otaliastudios.transcoder.strategy.TrackStrategy
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
import com.otaliastudios.transcoder.internal.utils.Logger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Future

/**
 * MbVideoCompressPlugin
 */
class MbVideoCompressPlugin : MethodCallHandler, FlutterPlugin {


    private var _context: Context? = null
    private var _channel: MethodChannel? = null
    private val TAG = "MbVideoCompressPlugin"
    private val LOG = Logger(TAG)
    private var transcodeFuture:Future<Void>? = null
    var channelName = "video_compress"

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        val context = _context;
        val channel = _channel;

        if (context == null || channel == null) {
            Log.w(TAG, "Calling VideoCompress plugin before initialization")
            return
        }

        when (call.method) {
            "getByteThumbnail" -> {
                val path = call.argument<String>("path")
                val quality = call.argument<Int>("quality")!!
                val position = call.argument<Int>("position")!! // to long
                ThumbnailUtility(channelName).getByteThumbnail(path!!, quality, position.toLong(), result)
            }
            "getFileThumbnail" -> {
                val path = call.argument<String>("path")
                val quality = call.argument<Int>("quality")!!
                val position = call.argument<Int>("position")!! // to long
                ThumbnailUtility("mb_video_compress").getFileThumbnail(context, path!!, quality,
                        position.toLong(), result)
            }
            "getMediaInfo" -> {
                val path = call.argument<String>("path")
                result.success(Utility(channelName).getMediaInfoJson(context, path!!).toString())
            }
            "deleteAllCache" -> {
                result.success(Utility(channelName).deleteAllCache(context, result));
            }
            "setLogLevel" -> {
                val logLevel = call.argument<Int>("logLevel")!!
                Logger.setLogLevel(logLevel)
                result.success(true);
            }
            "cancelCompression" -> {
                transcodeFuture?.cancel(true)
                result.success(false);
            }
            "compressVideo" -> {
                val path = call.argument<String>("path")!!
                val quality = call.argument<Int>("quality")!!
                val deleteOrigin = call.argument<Boolean>("deleteOrigin")!!
                val startTime = call.argument<Int>("startTime")
                val duration = call.argument<Int>("duration")
                val includeAudio = call.argument<Boolean>("includeAudio") ?: true
                val frameRate = if (call.argument<Int>("frameRate")==null) 30 else call.argument<Int>("frameRate")

                val tempDir: String = context.getExternalFilesDir("mb_video_compress")!!.absolutePath
                val out = SimpleDateFormat("yyyy-MM-dd hh-mm-ss").format(Date())
                val destPath: String = tempDir + File.separator + "VID_" + out + path.hashCode() + ".mp4"

                var videoTrackStrategy: TrackStrategy = DefaultVideoStrategy.atMost(340).build();
                val audioTrackStrategy: TrackStrategy

                when (quality) {

                    0 -> {
                      videoTrackStrategy = DefaultVideoStrategy.atMost(720).build()
                    }

                    1 -> {
                        videoTrackStrategy = DefaultVideoStrategy.atMost(360).build()
                    }
                    2 -> {
                        videoTrackStrategy = DefaultVideoStrategy.atMost(640).build()
                    }
                    3 -> {

                        assert(value = frameRate != null)
                        videoTrackStrategy = DefaultVideoStrategy.Builder()
                                .keyFrameInterval(3f)
                                .bitRate(1280 * 720 * 4.toLong())
                                .frameRate(frameRate!!) // will be capped to the input frameRate
                                .build()
                    }
                    4 -> {
                        videoTrackStrategy = DefaultVideoStrategy.atMost(480, 640).build()
                    }
                    5 -> {
                        videoTrackStrategy = DefaultVideoStrategy.atMost(540, 960).build()
                    }
                    6 -> {
                        videoTrackStrategy = DefaultVideoStrategy.atMost(720, 1280).build()
                    }
                    7 -> {
                        videoTrackStrategy = DefaultVideoStrategy.atMost(1080, 1920).build()
                    }
                    8 -> {
                        videoTrackStrategy = DefaultVideoStrategy.atMost(2160, 3840).build()
                    }
                }
                Log.w(TAG, videoTrackStrategy.toString())

                audioTrackStrategy = if (includeAudio) {
                    val sampleRate = DefaultAudioStrategy.SAMPLE_RATE_AS_INPUT
                    val channels = DefaultAudioStrategy.CHANNELS_AS_INPUT

                    DefaultAudioStrategy.builder()
                        .channels(channels)
                        .sampleRate(sampleRate)
                        .build()
                } else {
                    RemoveTrackStrategy()
                }

                val dataSource = if (startTime != null || duration != null){
                    val source = UriDataSource(context, Uri.parse(path))
                    TrimDataSource(source, (1000 * 1000 * (startTime ?: 0)).toLong(), (1000 * 1000 * (duration ?: 0)).toLong())
                }else{
                    UriDataSource(context, Uri.parse(path))
                }


                transcodeFuture = Transcoder.into(destPath!!)
                        .addDataSource(dataSource)
                        .setAudioTrackStrategy(audioTrackStrategy)
                        .setVideoTrackStrategy(videoTrackStrategy)
                        .setListener(object : TranscoderListener {
                            override fun onTranscodeProgress(progress: Double) {
                                channel.invokeMethod("updateProgress", progress * 100.00)
                            }
                            // These callbacks run on the main looper via the
                            // transcoder's Handler — i.e. OUTSIDE the method
                            // channel's try/catch — so anything that escapes
                            // here is an uncaught exception that kills the host
                            // app AND leaves the Dart future hanging forever.
                            // Every path must therefore answer `result` exactly
                            // once and never throw.
                            override fun onTranscodeCompleted(successCode: Int) {
                                channel.invokeMethod("updateProgress", 100.00)
                                // Build the whole payload inside the try, then
                                // answer `result` exactly once. The transcode
                                // itself succeeded here; a metadata failure is
                                // reported as `null` rather than crashing, so
                                // the caller can surface its own error.
                                val payload: String? = try {
                                    val json = Utility(channelName)
                                            .getMediaInfoJson(context, destPath)
                                    json.put("isCancel", false)
                                    json.toString()
                                } catch (t: Throwable) {
                                    Log.e(TAG, "getMediaInfoJson failed for $destPath", t)
                                    null
                                }
                                result.success(payload)
                                // Only drop the source once a usable result
                                // exists — deleting it after a failed read would
                                // destroy the input for nothing.
                                if (payload != null && deleteOrigin) {
                                    File(path).delete()
                                }
                            }

                            override fun onTranscodeCanceled() {
                                Log.w(TAG, "transcode canceled for $path")
                                result.success(null)
                            }

                            override fun onTranscodeFailed(exception: Throwable) {
                                // Was discarded, which made every failure
                                // indistinguishable from a cancel on the Dart
                                // side. Keep the cause.
                                Log.e(TAG, "transcode failed for $path", exception)
                                result.success(null)
                            }
                        }).transcode()
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        init(binding.applicationContext, binding.binaryMessenger)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        _channel?.setMethodCallHandler(null)
        _context = null
        _channel = null
    }

    private fun init(context: Context, messenger: BinaryMessenger) {
        val channel = MethodChannel(messenger, channelName)
        channel.setMethodCallHandler(this)
        _context = context
        _channel = channel
    }

    companion object {
        private const val TAG = "mb_video_compress"
    }

}
