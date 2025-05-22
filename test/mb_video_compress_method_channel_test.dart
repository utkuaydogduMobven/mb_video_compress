import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mb_video_compress/mb_video_compress_method_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  MethodChannelMbVideoCompress platform = MethodChannelMbVideoCompress();
  const MethodChannel channel = MethodChannel('mb_video_compress');

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      channel,
      (MethodCall methodCall) async {
        return '42';
      },
    );
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);
  });

  test('getPlatformVersion', () async {
    expect(await platform.getPlatformVersion(), '42');
  });
}
