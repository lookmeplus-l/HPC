import 'dart:io';
import 'package:flutter/material.dart';
import 'package:file_picker/file_picker.dart';
import '../services/image_matcher.dart';
import '../services/native_bridge.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final ImageMatcher _matcher = ImageMatcher();
  bool _isInitialized = false;
  bool _isFloatingWindowActive = false;
  bool _isScanning = false;

  @override
  void initState() {
    super.initState();
    _initialize();
  }

  Future<void> _initialize() async {
    await _matcher.initialize();
    setState(() => _isInitialized = true);
  }

  // ─── 悬浮窗 ────────────────────────────────────────────────

  Future<void> _toggleFloatingWindow() async {
    final hasPermission = await NativeBridge.checkOverlayPermission();
    if (!hasPermission) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('请先授予悬浮窗权限')),
        );
      }
      await NativeBridge.requestOverlayPermission();
      return;
    }

    if (_isFloatingWindowActive) {
      await NativeBridge.stopFloatingWindow();
      setState(() => _isFloatingWindowActive = false);
    } else {
      final success = await NativeBridge.startFloatingWindow();
      if (success) setState(() => _isFloatingWindowActive = true);
    }
  }

  // ─── 截屏识别 ──────────────────────────────────────────────

  Future<void> _startRecognition() async {
    if (_isScanning || _matcher.presetImageNames.isEmpty) return;

    setState(() => _isScanning = true);
    await NativeBridge.updateFloatingWindowStatus('scanning');

    try {
      final screenshotPath = await NativeBridge.requestScreenshot();
      if (screenshotPath == null) {
        await NativeBridge.updateFloatingWindowStatus('idle');
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('截屏失败，请重试')),
          );
        }
        return;
      }

      final result = await _matcher.matchScreenshot(screenshotPath);

      if (result != null && result.matched) {
        final templatePath = _matcher.getTemplateCachePath(result.templateName);
        await NativeBridge.updateFloatingWindowImage(
          imagePath: templatePath,
          matched: true,
        );
        await NativeBridge.updateFloatingWindowStatus('matched');
      } else {
        await NativeBridge.updateFloatingWindowImage(
          imagePath: null,
          matched: false,
        );
        await NativeBridge.updateFloatingWindowStatus('no_match');
      }

      try {
        File(screenshotPath).deleteSync();
      } catch (_) {}
    } catch (e) {
      await NativeBridge.updateFloatingWindowStatus('idle');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('识别出错: $e')),
        );
      }
    } finally {
      setState(() => _isScanning = false);
    }
  }

  // ─── 上传图片 ──────────────────────────────────────────────

  Future<void> _pickImage() async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.image,
    );
    if (result == null || result.files.isEmpty) return;

    final file = File(result.files.single.path!);
    final success = await _matcher.addImageFromFile(file);
    if (mounted) {
      setState(() {});
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(success ? '已添加图片' : '添加失败'),
          duration: const Duration(seconds: 1),
        ),
      );
    }
  }

  // ─── UI ────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('截图识别工具'),
        backgroundColor: Theme.of(context).colorScheme.primaryContainer,
      ),
      body: !_isInitialized
          ? const Center(child: CircularProgressIndicator())
          : Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _buildQuickActions(),
                  const SizedBox(height: 16),
                  Expanded(child: _buildImageList()),
                ],
              ),
            ),
    );
  }

  Widget _buildQuickActions() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 8),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: [
            _QuickActionButton(
              icon: _isFloatingWindowActive
                  ? Icons.visibility_off
                  : Icons.visibility,
              label: _isFloatingWindowActive ? '关闭悬浮窗' : '启动悬浮窗',
              color: _isFloatingWindowActive ? Colors.red : Colors.blue,
              onTap: _toggleFloatingWindow,
            ),
            _QuickActionButton(
              icon: _isScanning ? Icons.hourglass_top : Icons.camera_alt,
              label: _isScanning ? '识别中' : '截屏识别',
              color: Colors.orange,
              onTap: _startRecognition,
            ),
            _QuickActionButton(
              icon: Icons.add_photo_alternate,
              label: '添加图片',
              color: Colors.teal,
              onTap: _pickImage,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildImageList() {
    final names = _matcher.presetImageNames;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.photo_library, size: 20),
                const SizedBox(width: 8),
                Text(
                  '预置图片 (${names.length})',
                  style: const TextStyle(
                      fontSize: 16, fontWeight: FontWeight.bold),
                ),
              ],
            ),
            const SizedBox(height: 8),
            if (names.isEmpty)
              const Expanded(
                child: Center(
                  child: Text(
                    '暂无图片\n点击上方「添加图片」上传',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: Colors.grey),
                  ),
                ),
              )
            else
              Expanded(
                child: ListView.builder(
                  itemCount: names.length,
                  itemBuilder: (context, index) {
                    final name = names[index];
                    final bytes = _matcher.getPresetImageBytes(name);
                    final isDynamic = _matcher.isDynamicImage(name);
                    return Dismissible(
                      key: Key(name),
                      direction: isDynamic
                          ? DismissDirection.endToStart
                          : DismissDirection.none,
                      confirmDismiss: (_) async {
                        if (!isDynamic) return false;
                        return await showDialog<bool>(
                          context: context,
                          builder: (ctx) => AlertDialog(
                            title: const Text('删除图片'),
                            content: Text('确定要删除「$name」吗？'),
                            actions: [
                              TextButton(
                                onPressed: () => Navigator.pop(ctx, false),
                                child: const Text('取消'),
                              ),
                              TextButton(
                                onPressed: () => Navigator.pop(ctx, true),
                                child: const Text('删除',
                                    style: TextStyle(color: Colors.red)),
                              ),
                            ],
                          ),
                        );
                      },
                      onDismissed: (_) {
                        _matcher.removeDynamicImage(name);
                        setState(() {});
                      },
                      background: Container(
                        alignment: Alignment.centerRight,
                        padding: const EdgeInsets.only(right: 20),
                        color: Colors.red,
                        child:
                            const Icon(Icons.delete, color: Colors.white),
                      ),
                      child: ListTile(
                        leading: bytes != null
                            ? ClipRRect(
                                borderRadius: BorderRadius.circular(4),
                                child: Image.memory(bytes,
                                    width: 48, height: 48, fit: BoxFit.cover),
                              )
                            : const Icon(Icons.image, size: 48),
                        title: Text(name, style: const TextStyle(fontSize: 14)),
                        trailing: isDynamic
                            ? const Icon(Icons.swipe_left,
                                size: 16, color: Colors.grey)
                            : null,
                        dense: true,
                      ),
                    );
                  },
                ),
              ),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    if (_isFloatingWindowActive) NativeBridge.stopFloatingWindow();
    super.dispose();
  }
}

class _QuickActionButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback onTap;

  const _QuickActionButton({
    required this.icon,
    required this.label,
    required this.color,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: color.withAlpha(30),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(icon, color: color, size: 26),
            ),
            const SizedBox(height: 4),
            Text(
              label,
              style: TextStyle(fontSize: 12, color: Colors.grey.shade700),
            ),
          ],
        ),
      ),
    );
  }
}
