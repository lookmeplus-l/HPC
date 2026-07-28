import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';
import 'native_bridge.dart';

/// NCC 模板匹配结果
class MatchResult {
  final String templateName;
  final double nccScore;
  final bool matched;

  MatchResult({
    required this.templateName,
    required this.nccScore,
    required this.matched,
  });
}

/// 预置图片管理服务
/// 管理两类图片：assets 中的静态预置图 / 用户动态上传的图
/// 通过 Android 原生层的 NCC 模板匹配进行图像识别
class ImageMatcher {
  /// 模板名 → 缓存文件路径
  final Map<String, String> _cachedTemplates = {};

  /// 模板名 → 原始字节（UI 预览用）
  final Map<String, Uint8List> _imageBytes = {};

  /// 动态添加的模板名集合
  final Set<String> _dynamicNames = {};

  List<String> get presetImageNames {
    return _cachedTemplates.keys.toList();
  }

  Uint8List? getPresetImageBytes(String name) => _imageBytes[name];
  String? getTemplateCachePath(String name) => _cachedTemplates[name];
  bool isDynamicImage(String name) => _dynamicNames.contains(name);

  /// 初始化：加载静态预置图 + 恢复动态图片
  Future<void> initialize() async {
    await _loadStaticPresets();
    await _loadDynamicPresets();
  }

  /// 加载 assets 中的静态预置图片
  Future<void> _loadStaticPresets() async {
    // 读取 manifest.json
    List<String> imageFiles = [];
    try {
      final manifestStr =
          await rootBundle.loadString('assets/preset_images/manifest.json');
      final manifest = json.decode(manifestStr) as Map<String, dynamic>;
      final images = manifest['images'] as List<dynamic>? ?? [];
      imageFiles = images.map((e) => e.toString()).toList();
    } catch (_) {
      // 回退：扫描 AssetManifest
      try {
        final content = await rootBundle.loadString('AssetManifest.json');
        final map = json.decode(content) as Map<String, dynamic>;
        for (final key in map.keys) {
          if (key.startsWith('assets/preset_images/') &&
              key != 'assets/preset_images/manifest.json') {
            imageFiles.add(key.split('/').last);
          }
        }
      } catch (_) {}
    }

    for (final fileName in imageFiles) {
      final assetKey = 'assets/preset_images/$fileName';
      try {
        final byteData = await rootBundle.load(assetKey);
        final bytes = byteData.buffer.asUint8List();
        _imageBytes[fileName] = bytes;
        await _cacheToDisk(fileName, bytes);
      } catch (_) {}
    }
  }

  /// 恢复之前动态添加的图片
  Future<void> _loadDynamicPresets() async {
    final cacheDir = await getApplicationDocumentsDirectory();
    final templateDir = Directory('${cacheDir.path}/templates');
    final dynamicFile = File('${cacheDir.path}/dynamic_images.json');

    if (!dynamicFile.existsSync()) return;

    try {
      final list =
          (json.decode(dynamicFile.readAsStringSync()) as List<dynamic>)
              .map((e) => e.toString())
              .toList();

      for (final name in list) {
        final file = File('${templateDir.path}/$name');
        if (file.existsSync()) {
          final bytes = file.readAsBytesSync();
          _imageBytes[name] = bytes;
          _cachedTemplates[name] = file.path;
          _dynamicNames.add(name);
        }
      }
    } catch (_) {}
  }

  /// 缓存到磁盘
  Future<void> _cacheToDisk(String name, Uint8List bytes) async {
    final cacheDir = await getApplicationDocumentsDirectory();
    final templateDir = Directory('${cacheDir.path}/templates');
    if (!templateDir.existsSync()) {
      templateDir.createSync(recursive: true);
    }
    final file = File('${templateDir.path}/$name');
    if (!file.existsSync()) {
      file.writeAsBytesSync(bytes);
    }
    _cachedTemplates[name] = file.path;
  }

  /// 动态添加图片
  Future<bool> addImageFromFile(File sourceFile) async {
    final name = sourceFile.uri.pathSegments.last;
    // 避免重名
    final uniqueName = _makeUniqueName(name);

    try {
      final bytes = sourceFile.readAsBytesSync();
      _imageBytes[uniqueName] = bytes;
      await _cacheToDisk(uniqueName, bytes);
      _dynamicNames.add(uniqueName);
      await _saveDynamicList();
      return true;
    } catch (_) {
      return false;
    }
  }

  /// 删除动态图片
  Future<void> removeDynamicImage(String name) async {
    _imageBytes.remove(name);
    _cachedTemplates.remove(name);
    _dynamicNames.remove(name);
    await _saveDynamicList();

    // 删除磁盘文件
    final cacheDir = await getApplicationDocumentsDirectory();
    final file = File('${cacheDir.path}/templates/$name');
    if (file.existsSync()) {
      file.deleteSync();
    }
  }

  /// 持久化动态图片列表
  Future<void> _saveDynamicList() async {
    final cacheDir = await getApplicationDocumentsDirectory();
    final dynamicFile = File('${cacheDir.path}/dynamic_images.json');
    dynamicFile.writeAsStringSync(json.encode(_dynamicNames.toList()));
  }

  String _makeUniqueName(String name) {
    if (!_cachedTemplates.containsKey(name)) return name;
    final dot = name.lastIndexOf('.');
    final base = dot > 0 ? name.substring(0, dot) : name;
    final ext = dot > 0 ? name.substring(dot) : '';
    int i = 1;
    while (_cachedTemplates.containsKey('${base}_$i$ext')) {
      i++;
    }
    return '${base}_$i$ext';
  }

  /// 用截图与所有模板进行 NCC 匹配，返回最佳结果
  Future<MatchResult?> matchScreenshot(String screenshotPath) async {
    MatchResult? bestResult;

    for (final entry in _cachedTemplates.entries) {
      final result = await NativeBridge.matchTemplate(
        screenshotPath: screenshotPath,
        templatePath: entry.value,
      );

      if (result == null) continue;

      final matched = result['matched'] == true;
      final nccScore = (result['nccScore'] as num?)?.toDouble() ?? 0.0;

      if (bestResult == null || nccScore > bestResult.nccScore) {
        bestResult = MatchResult(
          templateName: entry.key,
          nccScore: nccScore,
          matched: matched,
        );
      }
    }

    return bestResult;
  }
}
