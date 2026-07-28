import 'package:flutter/services.dart';

class NativeBridge {
  static const _channel = MethodChannel('com.example.screen_matcher/native');

  static Future<bool> startFloatingWindow() => _invoke('startFloatingWindow', false);
  static Future<bool> stopFloatingWindow() => _invoke('stopFloatingWindow', false);
  static Future<bool> checkOverlayPermission() => _invoke('checkOverlayPermission', false);
  static Future<void> requestOverlayPermission() => _invoke('requestOverlayPermission', null);

  static Future<String?> requestScreenshot() async {
    try {
      final result = await _channel.invokeMethod('requestScreenshot');
      return result as String?;
    } catch (_) {
      return null;
    }
  }

  static Future<Map<String, dynamic>?> matchTemplate({
    required String screenshotPath,
    required String templatePath,
  }) async {
    try {
      final result = await _channel.invokeMethod('matchTemplate', {
        'screenshotPath': screenshotPath,
        'templatePath': templatePath,
      });
      if (result is Map) return Map<String, dynamic>.from(result);
      return null;
    } catch (_) {
      return null;
    }
  }

  static Future<void> showFloatingWindowImage({
    required String? imagePath,
    required bool matched,
  }) => _invoke('showFloatingWindowImage', null, {
    'imagePath': imagePath,
    'matched': matched,
  });

  static Future<void> setFloatingWindowStatus(String status) =>
      _invoke('setFloatingWindowStatus', null, {'status': status});

  static Future<T> _invoke<T>(String method, T defaultValue, [Map<String, dynamic>? args]) async {
    try {
      final result = args != null
          ? await _channel.invokeMethod(method, args)
          : await _channel.invokeMethod(method);
      return (result is T) ? result : defaultValue;
    } catch (_) {
      return defaultValue;
    }
  }
}
