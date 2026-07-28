package com.example.screen_matcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.sqrt

/**
 * NCC (Normalized Cross-Correlation) 模板匹配引擎
 * 支持多尺度匹配，可以在截图中找到预置图片的部分区域
 */
object TemplateMatcher {

    /** NCC 匹配阈值，超过此值认为匹配成功 */
    private const val NCC_THRESHOLD = 0.45f

    /** 大步长粗扫时的 NCC 筛选阈值 */
    private const val COARSE_THRESHOLD = 0.35f

    /** 截图的缩放最大宽度 */
    private const val MAX_SOURCE_WIDTH = 600

    /** 粗扫步长 */
    private const val COARSE_STRIDE = 6

    /** 多尺度因子 */
    private val SCALES = floatArrayOf(0.35f, 0.50f, 0.65f, 0.80f, 1.0f, 1.2f, 1.5f, 1.8f)

    data class MatchResult(
        val matched: Boolean,
        val nccScore: Float,
        val bestX: Int,
        val bestY: Int,
        val bestScale: Float,
        val templateWidth: Int,
        val templateHeight: Int
    )

    /**
     * 对截图和模板路径进行多尺度 NCC 模板匹配
     */
    fun match(screenshotPath: String, templatePath: String): MatchResult {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val sourceBitmap = BitmapFactory.decodeFile(screenshotPath, options)
            ?: return noMatch()

        val templateBitmap = BitmapFactory.decodeFile(templatePath, options)
            ?: return noMatch()

        // 缩放截图以加速
        val scaledSource = scaleSource(sourceBitmap)

        var bestResult = noMatch()

        for (scale in SCALES) {
            val scaledW = (templateBitmap.width * scale).toInt().coerceAtLeast(8)
            val scaledH = (templateBitmap.height * scale).toInt().coerceAtLeast(8)

            // 模板不能大于截图
            if (scaledW > scaledSource.width || scaledH > scaledSource.height) continue

            val scaledTemplate = Bitmap.createScaledBitmap(
                templateBitmap, scaledW, scaledH, true
            )

            // 第一阶段：大步长粗扫
            val candidates = coarseScan(scaledSource, scaledTemplate, scale)
            if (candidates.isEmpty()) continue

            // 第二阶段：对候选位置做精细匹配
            for (candidate in candidates) {
                val fineX = candidate.first
                val fineY = candidate.second
                val pixelCount = scaledW * scaledH

                val srcPixels = IntArray(pixelCount)
                val tplPixels = IntArray(pixelCount)

                scaledSource.getPixels(srcPixels, 0, scaledW, fineX, fineY, scaledW, scaledH)
                scaledTemplate.getPixels(tplPixels, 0, scaledW, 0, 0, scaledW, scaledH)

                val ncc = computeNCC(srcPixels, tplPixels)

                if (ncc > bestResult.nccScore) {
                    // 将坐标映射回原始截图
                    val scaleRatio = sourceBitmap.width.toFloat() / scaledSource.width
                    bestResult = MatchResult(
                        matched = ncc >= NCC_THRESHOLD,
                        nccScore = ncc,
                        bestX = (fineX * scaleRatio).toInt(),
                        bestY = (fineY * scaleRatio).toInt(),
                        bestScale = scale,
                        templateWidth = (scaledW * scaleRatio).toInt(),
                        templateHeight = (scaledH * scaleRatio).toInt()
                    )
                }
            }

            scaledTemplate.recycle()
        }

        sourceBitmap.recycle()
        scaledSource.recycle()
        templateBitmap.recycle()

        return bestResult
    }

    /** 大步长粗扫，返回候选位置列表 */
    private fun coarseScan(
        source: Bitmap,
        template: Bitmap,
        scale: Float
    ): List<Pair<Int, Int>> {
        val candidates = mutableListOf<Pair<Int, Int>>()
        val srcW = source.width
        val srcH = source.height
        val tplW = template.width
        val tplH = template.height

        val pixelCount = tplW * tplH
        val tplPixels = IntArray(pixelCount)
        template.getPixels(tplPixels, 0, tplW, 0, 0, tplW, tplH)

        // 预计算模板统计量
        val tplMean = tplPixels.fold(0L) { acc, p -> acc + grayValue(p) } / pixelCount.toFloat()
        var tplVariance = 0f
        for (p in tplPixels) {
            val diff = grayValue(p) - tplMean
            tplVariance += diff * diff
        }
        val tplStd = sqrt(tplVariance / pixelCount)

        for (y in 0..(srcH - tplH) step COARSE_STRIDE) {
            for (x in 0..(srcW - tplW) step COARSE_STRIDE) {
                val srcPixels = IntArray(pixelCount)
                source.getPixels(srcPixels, 0, tplW, x, y, tplW, tplH)

                val ncc = computeNCCFast(srcPixels, tplPixels, tplMean, tplStd)
                if (ncc > COARSE_THRESHOLD) {
                    candidates.add(Pair(x, y))
                }
            }
        }

        // 按 NCC 得分排序，只保留前 10 个
        return candidates
            .sortedByDescending { computeNCCAt(source, template, it.first, it.second) }
            .take(10)
    }

    /** 在指定精确位置计算 NCC */
    private fun computeNCCAt(source: Bitmap, template: Bitmap, x: Int, y: Int): Float {
        val tplW = template.width
        val tplH = template.height
        val pixelCount = tplW * tplH

        val srcPixels = IntArray(pixelCount)
        val tplPixels = IntArray(pixelCount)
        source.getPixels(srcPixels, 0, tplW, x, y, tplW, tplH)
        template.getPixels(tplPixels, 0, tplW, 0, 0, tplW, tplH)

        return computeNCC(srcPixels, tplPixels)
    }

    /** NCC 归一化互相关计算 */
    private fun computeNCC(srcPixels: IntArray, tplPixels: IntArray): Float {
        val n = srcPixels.size

        var srcMean = 0f
        var tplMean = 0f
        for (i in 0 until n) {
            srcMean += grayValue(srcPixels[i])
            tplMean += grayValue(tplPixels[i])
        }
        srcMean /= n
        tplMean /= n

        var numerator = 0f
        var denomSrc = 0f
        var denomTpl = 0f
        for (i in 0 until n) {
            val srcDiff = grayValue(srcPixels[i]) - srcMean
            val tplDiff = grayValue(tplPixels[i]) - tplMean
            numerator += srcDiff * tplDiff
            denomSrc += srcDiff * srcDiff
            denomTpl += tplDiff * tplDiff
        }

        val denominator = sqrt(denomSrc * denomTpl)
        return if (denominator < 0.0001f) 0f else numerator / denominator
    }

    /** 快速 NCC 计算，使用预计算的模板统计量 */
    private fun computeNCCFast(
        srcPixels: IntArray,
        tplPixels: IntArray,
        tplMean: Float,
        tplStd: Float
    ): Float {
        val n = srcPixels.size

        var srcMean = 0f
        for (p in srcPixels) {
            srcMean += grayValue(p)
        }
        srcMean /= n

        var numerator = 0f
        var denomSrc = 0f
        for (i in 0 until n) {
            val srcDiff = grayValue(srcPixels[i]) - srcMean
            val tplDiff = grayValue(tplPixels[i]) - tplMean
            numerator += srcDiff * tplDiff
            denomSrc += srcDiff * srcDiff
        }

        val denomTpl = tplStd * tplStd * n
        val denominator = sqrt(denomSrc * denomTpl)
        return if (denominator < 0.0001f) 0f else numerator / denominator
    }

    /** 提取灰度值 */
    private fun grayValue(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    /** 缩放截图到合理大小 */
    private fun scaleSource(source: Bitmap): Bitmap {
        val srcW = source.width
        val srcH = source.height

        return if (srcW <= MAX_SOURCE_WIDTH) {
            Bitmap.createBitmap(source)
        } else {
            val ratio = MAX_SOURCE_WIDTH.toFloat() / srcW
            val newW = MAX_SOURCE_WIDTH
            val newH = (srcH * ratio).toInt()
            Bitmap.createScaledBitmap(source, newW, newH, true)
        }
    }

    private fun noMatch(): MatchResult = MatchResult(
        matched = false,
        nccScore = 0f,
        bestX = 0,
        bestY = 0,
        bestScale = 1f,
        templateWidth = 0,
        templateHeight = 0
    )
}
