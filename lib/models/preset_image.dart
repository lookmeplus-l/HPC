import 'dart:typed_data';

/// 预置图片模型
class PresetImage {
  final String name;
  final String assetPath;
  final Uint8List? bytes;

  PresetImage({
    required this.name,
    required this.assetPath,
    this.bytes,
  });

  /// 从文件名创建
  factory PresetImage.fromFileName(String fileName) {
    return PresetImage(
      name: fileName.replaceAll('.png', '').replaceAll('.jpg', ''),
      assetPath: 'assets/preset_images/$fileName',
    );
  }
}
