#import "MbVideoCompressPlugin.h"
#import <mb_video_compress/mb_video_compress-Swift.h>

@implementation MbVideoCompressPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar> *)registrar {
  [SwiftMbVideoCompressPlugin registerWithRegistrar:registrar];
}
@end
