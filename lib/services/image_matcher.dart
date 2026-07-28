import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';
import 'native_bridge.dart';

class MatchResult {
  final String templateName;
  final double nccScore;
  final bool matched;

  const MatchResult({
    required this.templateName,
    required this.nccScore,
    required this.matched,
  });
}

class ImageMatcher {
  final Map<String, String> _cachePaths = {};
  final Map<String, Uint8List> _previews = {};
  final Set<String> _dynamicNames = {};

  List<String> get allNames => _cachePaths.keys.toList();
  int get count => _cachePaths.length;
  Uint8List? preview(String name) => _previews[name];
  String? cachePath(String name) => _cachePaths[name];
  bool isDynamic(String name) => _dynamicNames.contains(name);

  Future<void> initialize() async {
    await _loadStatic();
    await _loadDynamic();
  }

  Future<void> _loadStatic() async {
    List<String> files;
    try {
      final json = await rootBundle.loadString('assets/preset_images/manifest.json');
      final map = jsonDecode(json) as Map<String, dynamic>;
      files = (map['images'] as List<dynamic>).cast<String>();
    } catch (_) {
      try {
        final content = await rootBundle.loadString('AssetManifest.json');
        final map = jsonDecode(content) as Map<String, dynamic>;
        files = map.keys
            .where((k) => k.startsWith('assets/preset_images/') && k != 'assets/preset_images/manifest.json')
            .map((k) => k.split('/').last)
            .toList();
      } catch (_) {
        return;
      }
    }

    for (final name in files) {
      try {
        final data = await rootBundle.load('assets/preset_images/$name');
        final bytes = data.buffer.asUint8List();
        _previews[name] = bytes;
        await _cacheToDisk(name, bytes);
      } catch (_) {}
    }
  }

  Future<void> _loadDynamic() async {
    final cacheDir = await getApplicationDocumentsDirectory();
    final file = File('${cacheDir.path}/dynamic_images.json');
    if (!file.existsSync()) return;

    List<String> names;
    try {
      names = (jsonDecode(file.readAsStringSync()) as List<dynamic>).cast<String>();
    } catch (_) {
      return;
    }

    final dir = Directory('${cacheDir.path}/templates');
    for (final name in names) {
      final f = File('${dir.path}/$name');
      if (f.existsSync()) {
        final bytes = f.readAsBytesSync();
        _previews[name] = bytes;
        _cachePaths[name] = f.path;
        _dynamicNames.add(name);
      }
    }
  }

  Future<void> _cacheToDisk(String name, Uint8List bytes) async {
    final cacheDir = await getApplicationDocumentsDirectory();
    final dir = Directory('${cacheDir.path}/templates');
    if (!dir.existsSync()) dir.createSync(recursive: true);

    final file = File('${dir.path}/$name');
    if (!file.existsSync()) file.writeAsBytesSync(bytes);
    _cachePaths[name] = file.path;
  }

  Future<bool> addImage(File source) async {
    final baseName = source.uri.pathSegments.last;
    final name = _uniqueName(baseName);

    try {
      final bytes = source.readAsBytesSync();
      _previews[name] = bytes;
      await _cacheToDisk(name, bytes);
      _dynamicNames.add(name);
      await _saveDynamic();
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<void> removeImage(String name) async {
    _previews.remove(name);
    _cachePaths.remove(name);
    _dynamicNames.remove(name);
    await _saveDynamic();

    final cacheDir = await getApplicationDocumentsDirectory();
    File('${cacheDir.path}/templates/$name').deleteSync();
  }

  Future<void> _saveDynamic() async {
    final cacheDir = await getApplicationDocumentsDirectory();
    File('${cacheDir.path}/dynamic_images.json')
        .writeAsStringSync(jsonEncode(_dynamicNames.toList()));
  }

  String _uniqueName(String name) {
    if (!_cachePaths.containsKey(name)) return name;
    final dot = name.lastIndexOf('.');
    final base = dot > 0 ? name.substring(0, dot) : name;
    final ext = dot > 0 ? name.substring(dot) : '';
    int i = 1;
    while (_cachePaths.containsKey('${base}_$i$ext')) i++;
    return '${base}_$i$ext';
  }

  Future<MatchResult?> matchScreenshot(String screenshotPath) async {
    MatchResult? best;

    for (final entry in _cachePaths.entries) {
      final result = await NativeBridge.matchTemplate(
        screenshotPath: screenshotPath,
        templatePath: entry.value,
      );
      if (result == null) continue;

      final score = (result['nccScore'] as num).toDouble();
      if (best == null || score > best.nccScore) {
        best = MatchResult(
          templateName: entry.key,
          nccScore: score,
          matched: result['matched'] == true,
        );
      }
    }

    return best;
  }
}
