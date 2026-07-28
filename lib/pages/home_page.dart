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
  final _matcher = ImageMatcher();
  bool _ready = false;
  bool _floatingActive = false;
  bool _scanning = false;

  @override
  void initState() {
    super.initState();
    _matcher.initialize().then((_) {
      if (mounted) setState(() => _ready = true);
    });
  }

  Future<void> _toggleFloating() async {
    final ok = await NativeBridge.checkOverlayPermission();
    if (!ok) {
      if (mounted) _snack('请先授予悬浮窗权限');
      await NativeBridge.requestOverlayPermission();
      return;
    }

    if (_floatingActive) {
      await NativeBridge.stopFloatingWindow();
      setState(() => _floatingActive = false);
    } else {
      final success = await NativeBridge.startFloatingWindow();
      if (success) setState(() => _floatingActive = true);
    }
  }

  Future<void> _startRecognition() async {
    if (_scanning || _matcher.count == 0) return;
    setState(() => _scanning = true);
    await NativeBridge.setFloatingWindowStatus('scanning');

    try {
      final path = await NativeBridge.requestScreenshot();
      if (path == null) {
        await NativeBridge.setFloatingWindowStatus('idle');
        if (mounted) _snack('截屏失败，请重试');
        return;
      }

      final result = await _matcher.matchScreenshot(path);

      if (result != null && result.matched) {
        await NativeBridge.showFloatingWindowImage(
          imagePath: _matcher.cachePath(result.templateName),
          matched: true,
        );
        await NativeBridge.setFloatingWindowStatus('matched');
      } else {
        await NativeBridge.showFloatingWindowImage(imagePath: null, matched: false);
        await NativeBridge.setFloatingWindowStatus('no_match');
      }

      try { File(path).deleteSync(); } catch (_) {}
    } catch (e) {
      await NativeBridge.setFloatingWindowStatus('idle');
      if (mounted) _snack('识别出错: $e');
    } finally {
      setState(() => _scanning = false);
    }
  }

  Future<void> _pickImage() async {
    final result = await FilePicker.platform.pickFiles(type: FileType.image);
    if (result == null || result.files.isEmpty) return;

    final ok = await _matcher.addImage(File(result.files.single.path!));
    if (mounted) {
      setState(() {});
      _snack(ok ? '已添加图片' : '添加失败');
    }
  }

  void _snack(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(msg), duration: const Duration(seconds: 1)),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('截图识别工具'),
        backgroundColor: Theme.of(context).colorScheme.primaryContainer,
      ),
      body: !_ready
          ? const Center(child: CircularProgressIndicator())
          : Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _buildActions(),
                  const SizedBox(height: 16),
                  Expanded(child: _buildList()),
                ],
              ),
            ),
    );
  }

  Widget _buildActions() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 8),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: [
            _ActionBtn(
              icon: _floatingActive ? Icons.visibility_off : Icons.visibility,
              label: _floatingActive ? '关闭悬浮窗' : '启动悬浮窗',
              color: _floatingActive ? Colors.red : Colors.blue,
              onTap: _toggleFloating,
            ),
            _ActionBtn(
              icon: _scanning ? Icons.hourglass_top : Icons.camera_alt,
              label: _scanning ? '识别中' : '截屏识别',
              color: Colors.orange,
              onTap: _startRecognition,
            ),
            _ActionBtn(
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

  Widget _buildList() {
    final names = _matcher.allNames;

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
                  style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
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
                  itemBuilder: (_, i) {
                    final name = names[i];
                    final preview = _matcher.preview(name);
                    final dynamic_ = _matcher.isDynamic(name);

                    return Dismissible(
                      key: Key(name),
                      direction:
                          dynamic_ ? DismissDirection.endToStart : DismissDirection.none,
                      confirmDismiss: (_) async {
                        if (!dynamic_) return false;
                        final confirm = await showDialog<bool>(
                          context: context,
                          builder: (ctx) => AlertDialog(
                            title: const Text('删除图片'),
                            content: Text('确定删除「$name」吗？'),
                            actions: [
                              TextButton(
                                child: const Text('取消'),
                                onPressed: () => Navigator.pop(ctx, false),
                              ),
                              TextButton(
                                child:
                                    const Text('删除', style: TextStyle(color: Colors.red)),
                                onPressed: () => Navigator.pop(ctx, true),
                              ),
                            ],
                          ),
                        );
                        return confirm ?? false;
                      },
                      onDismissed: (_) {
                        _matcher.removeImage(name);
                        setState(() {});
                      },
                      background: Container(
                        alignment: Alignment.centerRight,
                        padding: const EdgeInsets.only(right: 20),
                        color: Colors.red,
                        child: const Icon(Icons.delete, color: Colors.white),
                      ),
                      child: ListTile(
                        leading: preview != null
                            ? ClipRRect(
                                borderRadius: BorderRadius.circular(4),
                                child: Image.memory(
                                  preview,
                                  width: 48,
                                  height: 48,
                                  fit: BoxFit.cover,
                                ),
                              )
                            : const Icon(Icons.image, size: 48),
                        title:
                            Text(name, style: const TextStyle(fontSize: 14)),
                        trailing: dynamic_
                            ? const Icon(Icons.swipe_left, size: 16, color: Colors.grey)
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
    if (_floatingActive) NativeBridge.stopFloatingWindow();
    super.dispose();
  }
}

class _ActionBtn extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback onTap;

  const _ActionBtn({
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
            Text(label, style: TextStyle(fontSize: 12, color: Colors.grey.shade700)),
          ],
        ),
      ),
    );
  }
}
