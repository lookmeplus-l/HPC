import 'package:flutter/material.dart';
import 'pages/home_page.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ScreenMatcherApp());
}

class ScreenMatcherApp extends StatelessWidget {
  const ScreenMatcherApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '截图识别工具',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      home: const HomePage(),
    );
  }
}
