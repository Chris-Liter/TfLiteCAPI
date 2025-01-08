package com.logic.tflitecapi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import org.tensorflow.lite.task.vision.detector.Detection

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: List<Any> = emptyList()
    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()

    private var scaleFactorX: Float = 1f
    private var scaleFactorY: Float = 1f

    private var bounds = Rect()

    init {
        initPaints()
    }

    fun clear() {
        textPaint.reset()
        textBackgroundPaint.reset()
        boxPaint.reset()
        invalidate()
        initPaints()
    }

    private val colors = listOf(
        R.color.purple_200,
        R.color.purple_500,
        R.color.purple_700,
        R.color.black,
        R.color.teal_200,
        R.color.teal_700,
        R.color.white
    )
    private val randomColor = colors.random()

    private fun initPaints() {
        textBackgroundPaint.color = Color.BLACK
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = 50f

        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 50f

        boxPaint.color = ContextCompat.getColor(context!!, R.color.teal_200)
        boxPaint.strokeWidth = 8F
        boxPaint.style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        for (result in results) {
            when (result) {
                is Detection -> drawDetection(canvas, result)
                is Results -> drawResults(canvas, result)
                else -> throw IllegalArgumentException("Unsupported type in results")
            }
        }
    }

    private fun drawDetection(canvas: Canvas, detection: Detection) {
        val boundingBox = detection.boundingBox

        val top = boundingBox.top * scaleFactorY
        val bottom = boundingBox.bottom * scaleFactorY
        val left = boundingBox.left * scaleFactorX
        val right = boundingBox.right * scaleFactorX

        val drawableRect = RectF(left, top, right, bottom)
        canvas.drawRect(drawableRect, boxPaint)

        val drawableText =
            detection.categories[0].label + " " +
                    String.format("%.2f", detection.categories[0].score)

        textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length, bounds)
        val textWidth = bounds.width()
        val textHeight = bounds.height()
        canvas.drawRect(
            left,
            top,
            left + textWidth + Companion.BOUNDING_RECT_TEXT_PADDING,
            top + textHeight + Companion.BOUNDING_RECT_TEXT_PADDING,
            textBackgroundPaint
        )

        canvas.drawText(drawableText, left, top + bounds.height(), textPaint)
    }

    private fun drawResults(canvas: Canvas, result: Results) {
        val boundingBox = result.boundingBoxes

        val top = boundingBox.top * scaleFactorY
        val bottom = boundingBox.bottom * scaleFactorY
        val left = boundingBox.left * scaleFactorX
        val right = boundingBox.right * scaleFactorX

        val drawableRect = RectF(left, top, right, bottom)
        canvas.drawRect(drawableRect, boxPaint)

        val drawableText =
            result.categories[0].label + " " +
                    String.format("%.2f", result.categories[0].score)

        textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length, bounds)
        val textWidth = bounds.width()
        val textHeight = bounds.height()
        canvas.drawRect(
            left,
            top,
            left + textWidth + Companion.BOUNDING_RECT_TEXT_PADDING,
            top + textHeight + Companion.BOUNDING_RECT_TEXT_PADDING,
            textBackgroundPaint
        )

        canvas.drawText(drawableText, left, top + bounds.height(), textPaint)
    }

    fun setResults(
        detectionResults: MutableList<Detection>,
        imageHeight: Int,
        imageWidth: Int,
    ) {
        results = detectionResults
        updateScaleFactors(imageHeight, imageWidth)
    }

    fun setResultsC(
        detectionResults: MutableList<Results>,
        imageHeight: Int,
        imageWidth: Int,
    ) {
        results = detectionResults
        updateScaleFactors(imageHeight, imageWidth)
    }

    private fun updateScaleFactors(imageHeight: Int, imageWidth: Int) {
        scaleFactorX = width * 1f / imageWidth
        scaleFactorY = height * 1f / imageHeight
        invalidate()
    }

    companion object {
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}
