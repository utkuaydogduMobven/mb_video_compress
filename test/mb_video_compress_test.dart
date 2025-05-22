import 'package:flutter_test/flutter_test.dart';
import 'package:mb_video_compress/mb_video_compress.dart';
import 'package:mb_video_compress/mb_video_compress_platform_interface.dart';
import 'package:mb_video_compress/mb_video_compress_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockMbVideoCompressPlatform
    with MockPlatformInterfaceMixin
    implements MbVideoCompressPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final MbVideoCompressPlatform initialPlatform = MbVideoCompressPlatform.instance;

  test('$MethodChannelMbVideoCompress is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelMbVideoCompress>());
  });

  test('getPlatformVersion', () async {
    MbVideoCompress mbVideoCompressPlugin = MbVideoCompress();
    MockMbVideoCompressPlatform fakePlatform = MockMbVideoCompressPlatform();
    MbVideoCompressPlatform.instance = fakePlatform;

    expect(await mbVideoCompressPlugin.getPlatformVersion(), '42');
  });
}
