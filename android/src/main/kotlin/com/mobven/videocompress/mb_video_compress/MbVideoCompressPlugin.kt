package com.mobven.videocompress.mb_video_compress

import androidx.annotation.NonNull

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

/** MbVideoCompressPlugin */
class MbVideoCompressPlugin: FlutterPlugin, FlutterPlugin {
  private var _context: Context? = null
    private var _channel: MethodChannel? = null
    private val TAG = "MbVideoCompressPlugin"
    private val LOG = Logger(TAG)
    private var transcodeFuture:Future<Void>? = null
    var channelName = "mb_video_compress"

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        val context = _context;
        val channel = _channel;

        if (context == null || channel == null) {
            Log.w(TAG, "Calling MbVideoCompress plugin before initialization")
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
                }

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
                            override fun onTranscodeCompleted(successCode: Int) {
                                channel.invokeMethod("updateProgress", 100.00)
                                val json = Utility(channelName).getMediaInfoJson(context, destPath)
                                json.put("isCancel", false)
                                result.success(json.toString())
                                if (deleteOrigin) {
                                    File(path).delete()
                                }
                            }

                            override fun onTranscodeCanceled() {
                                result.success(null)
                            }

                            override fun onTranscodeFailed(exception: Throwable) {
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

        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val instance = MbVideoCompressPlugin()
            instance.init(registrar.context(), registrar.messenger())
        }
    }
}
