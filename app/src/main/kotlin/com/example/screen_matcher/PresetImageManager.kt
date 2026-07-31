package com.example.screen_matcher

import android.content.Context
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

class PresetImageManager(private val context: Context) {

    private val cacheDir = File(context.filesDir, "templates")
    private val cachePaths = mutableMapOf<String, String>()
    private val dynamicNames = mutableSetOf<String>()

    val allNames: List<String> get() = cachePaths.keys.toList()
    fun isDynamic(name: String) = dynamicNames.contains(name)
    fun cachePath(name: String) = cachePaths[name]

    fun init() {
        cacheDir.mkdirs()
        loadStaticPresets()
        loadDynamicPresets()
    }

    private fun loadStaticPresets() {
        val names = listOf(
            "北1门.webp", "左对角门.png", "左Y青蛙房.png", "左Y门.jpg", "左音叉门.jpg",
            "左对T门.png", "左罐子门.png", "左锤灯笼门.png", "左锤子门.jpg", "左倒T门.jpg",
            "右三L门.png", "右左上右下门.jpg", "右骑士门.png", "右双L门.jpg", "右L门.png",
            "南三缺一门.jpg", "右锤子门.jpg", "南L门.jpg", "南十字门.jpg", "南orz门.png",
            "南红门.png", "北T门.jpg", "北红门.jpg", "北凹门.jpg", "北红对角门.jpg",
            "北4门.jpg", "北1门.jpg", "北4安全门.png", "北1沙发门.png"
        )

        for (name in names) {
            try {
                context.assets.open("preset_images/$name").use { input ->
                    val file = File(cacheDir, name)
                    if (!file.exists()) {
                        FileOutputStream(file).use { out -> input.copyTo(out) }
                    }
                    cachePaths[name] = file.absolutePath
                }
            } catch (e: Exception) {
                // skip missing assets
            }
        }
    }

    private fun loadDynamicPresets() {
        val jsonFile = File(context.filesDir, "dynamic_images.json")
        if (!jsonFile.exists()) return

        val names = try {
            jsonFile.readText()
                .removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            return
        }

        for (name in names) {
            val file = File(cacheDir, name)
            if (file.exists()) {
                cachePaths[name] = file.absolutePath
                dynamicNames.add(name)
            }
        }
    }

    fun addImage(sourceFile: File): Boolean {
        val name = uniqueName(sourceFile.name)
        return try {
            val dest = File(cacheDir, name)
            sourceFile.copyTo(dest, overwrite = true)
            cachePaths[name] = dest.absolutePath
            dynamicNames.add(name)
            saveDynamicList()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun removeImage(name: String) {
        cachePaths.remove(name)
        dynamicNames.remove(name)
        File(cacheDir, name).delete()
        saveDynamicList()
    }

    fun loadThumbnail(name: String): android.graphics.Bitmap? {
        val path = cachePaths[name] ?: return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = 4
        }
        return BitmapFactory.decodeFile(path, opts)
    }

    private fun uniqueName(name: String): String {
        if (!cachePaths.containsKey(name)) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (cachePaths.containsKey("${base}_$i$ext")) i++
        return "${base}_$i$ext"
    }

    private fun saveDynamicList() {
        val json = dynamicNames.joinToString(",", "[", "]") { "\"$it\"" }
        File(context.filesDir, "dynamic_images.json").writeText(json)
    }
}
