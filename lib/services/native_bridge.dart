import 'package:flutter/services.dart';

/// Native Bridge - 通过 MethodChannel 与 Android 原生代码通信
class NativeBridge {
  static const _channel = MethodChannel('com.example.screen_matcher/native');

  /// 启动悬浮窗服务
  static Future<bool> startFloatingWindow() async {
    try {
      return await _channel.invokeMethod('startFloatingWindow') ?? false;
    } catch (e) {
      return false;
    }
  }

  /// 停止悬浮窗服务
  static Future<bool> stopFloatingWindow() async {
    try {
      return await _channel.invokeMethod('stopFloatingWindow') ?? false;
    } catch (e) {
      return false;
    }
  }

  /// 检查悬浮窗权限
  static Future<bool> checkOverlayPermission() async {
    try {
      return await _channel.invokeMethod('checkOverlayPermission') ?? false;
    } catch (e) {
      return false;
    }
  }

  /// 请求悬浮窗权限
  static Future<void> requestOverlayPermission() async {
    try {
      await _channel.invokeMethod('requestOverlayPermission');
    } catch (e) {
      // ignore
    }
  }

  /// 请求截屏并返回截图文件路径
  static Future<String?> requestScreenshot() async {
    try {
      final path = await _channel.invokeMethod('requestScreenshot');
      return path as String?;
    } catch (e) {
      return null;
    }
  }

  /// NCC 模板匹配：在截图中查找模板图片
  /// 返回 Map 包含 matched, nccScore, bestX, bestY, bestScale 等字段
  static Future<Map<String, dynamic>?> matchTemplate({
    required String screenshotPath,
    required String templatePath,
  }) async {
    try {
      final result = await _channel.invokeMethod('matchTemplate', {
        'screenshotPath': screenshotPath,
        'templatePath': templatePath,
      });
      if (result is Map) {
        return Map<String, dynamic>.from(result);
      }
      return null;
    } catch (e) {
      return null;
    }
  }

  /// 更新悬浮窗显示的图片
  static Future<void> updateFloatingWindowImage({
    required String? imagePath,
    required bool matched,
  }) async {
    try {
      await _channel.invokeMethod('updateFloatingWindowImage', {
        'imagePath': imagePath,
        'matched': matched,
      });
    } catch (e) {
      // ignore
    }
  }

  /// 更新悬浮窗状态文字
  static Future<void> updateFloatingWindowStatus(String status) async {
    try {
      await _channel.invokeMethod('updateFloatingWindowStatus', {
        'status': status,
      });
    } catch (e) {
      // ignore
    }
  }
}
