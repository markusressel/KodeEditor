package de.markusressel.kodeeditor.library.view

import android.content.Context
import android.graphics.*
import android.os.Build
import android.support.annotation.StringRes
import android.text.Layout
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.jakewharton.rxbinding2.widget.RxTextView
import com.otaliastudios.zoom.ZoomApi
import com.otaliastudios.zoom.ZoomEngine
import com.otaliastudios.zoom.ZoomImageView
import com.otaliastudios.zoom.ZoomLayout
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import de.markusressel.kodeeditor.library.R
import de.markusressel.kodeeditor.library.extensions.dpToPx
import de.markusressel.kodeeditor.library.extensions.getColor
import de.markusressel.kodehighlighter.core.SyntaxHighlighter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt


/**
 * Code Editor that allows pinch-to-zoom, line numbers etc.
 */
open class CodeEditorLayout
@JvmOverloads
constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr) {

    /**
     * The ZoomLayout containing the [CodeEditText].
     */
    lateinit var codeEditorZoomLayout: CodeEditorView

    /**
     * Text size in SP
     */
    private var textSizeSp: Float = DEFAULT_TEXT_SIZE_SP

    /**
     * Text size in PX
     */
    private var textSizePx: Float
        get() {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSizeSp, context.resources.displayMetrics)
        }
        set(value) {
            textSizeSp = value / resources.displayMetrics.scaledDensity
        }

    /**
     * The view displaying line numbers.
     * This is also a [ZoomLayout] so the line numbers can be scaled and panned according to the
     * code editor's zoom and pan.
     */
    internal lateinit var lineNumberZoomLayout: ZoomLayout

    /**
     * The [TextView] with the actual line numbers as a multiline text.
     */
    internal lateinit var lineNumberTextView: TextView

    /**
     * The container layout for the minimap.
     */
    internal lateinit var minimapContainerLayout: ViewGroup

    /**
     * The [ZoomLayout] used for the minimap.
     */
    internal lateinit var minimapZoomLayout: ZoomImageView

    /**
     * The rectangle on the minimap indicating the currently visible area.
     */
    internal lateinit var minimapIndicator: View

    /**
     * The (optional) divider between [lineNumberZoomLayout] and [codeEditorZoomLayout]
     */
    internal lateinit var dividerView: View

    private var currentLineCount = -1L

    /**
     * Controls whether to follow cursor movements or not.
     */
    var isMoveWithCursorEnabled = false
    private var internalMoveWithCursorEnabled = false

    /**
     * Indicates if the minimap should be shown or not.
     */
    var showMinimap = DEFAULT_SHOW_MINIMAP
        set(value) {
            field = value
            minimapContainerLayout.visibility = if (value) View.VISIBLE else View.GONE
        }

    init {
        inflateViews(LayoutInflater.from(context))
        readParameters(attrs, defStyleAttr)
        setListeners()

        syncLineNumbersWithEditor()
    }

    private fun inflateViews(layoutInflater: LayoutInflater) {
        layoutInflater.inflate(R.layout.layout_code_editor__main_layout, this)

        lineNumberZoomLayout = findViewById(R.id.cev_linenumbers_zoomLayout)
        lineNumberTextView = findViewById(R.id.cev_linenumbers_textview)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            lineNumberTextView.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        }

        dividerView = findViewById(R.id.cev_divider)
        codeEditorZoomLayout = findViewById(R.id.cev_codeEditorView)

        minimapContainerLayout = findViewById(R.id.cev_minimap_container)
        minimapZoomLayout = minimapContainerLayout.findViewById(R.id.cev_minimap)
        minimapIndicator = minimapContainerLayout.findViewById(R.id.cev_minimap_indicator)
    }

    private fun readParameters(attrs: AttributeSet?, defStyleAttr: Int) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.CodeEditorView, defStyleAttr, 0)

        val lineNumberTextColor = a.getColor(context,
                R.styleable.CodeEditorView_cev_lineNumbers_textColor,
                R.attr.cev_lineNumbers_textColor,
                android.R.attr.textColorPrimary)
        lineNumberTextView.setTextColor(lineNumberTextColor)

        val lineNumberBackgroundColor = a.getColor(context,
                R.styleable.CodeEditorView_cev_lineNumbers_backgroundColor,
                R.attr.cev_lineNumbers_backgroundColor,
                android.R.attr.windowBackground)
        lineNumberZoomLayout.setBackgroundColor(lineNumberBackgroundColor)


        val dividerEnabled = a.getBoolean(R.styleable.CodeEditorView_cev_divider, DEFAULT_SHOW_DIVIDER)
        dividerView.visibility = if (dividerEnabled) View.VISIBLE else View.GONE

        val dividerColor = a.getColor(context,
                R.styleable.CodeEditorView_cev_divider_color,
                R.attr.cev_divider_color,
                android.R.attr.textColorPrimary)
        dividerView.setBackgroundColor(dividerColor)

        val editTextBackgroundColor = a.getColor(context,
                R.styleable.CodeEditorView_cev_editor_backgroundColor,
                R.attr.cev_editor_backgroundColor,
                android.R.attr.windowBackground)
        codeEditorZoomLayout.codeEditText.setBackgroundColor(editTextBackgroundColor)

        val codeEditorMaxZoom = a.getFloat(R.styleable.CodeEditorView_cev_editor_maxZoom, DEFAULT_MAX_ZOOM)
        lineNumberZoomLayout.setMaxZoom(codeEditorMaxZoom, ZoomApi.TYPE_REAL_ZOOM)
        codeEditorZoomLayout.setMaxZoom(codeEditorMaxZoom, ZoomApi.TYPE_REAL_ZOOM)

        showMinimap = a.getBoolean(R.styleable.CodeEditorView_cev_editor_showMinimap, DEFAULT_SHOW_MINIMAP)

        a.recycle()
    }

    private fun setListeners() {
        // add listener to code editor to keep linenumbers position and zoom in sync
        codeEditorZoomLayout.engine.addListener(object : ZoomEngine.Listener {
            override fun onIdle(engine: ZoomEngine) {
            }

            override fun onUpdate(engine: ZoomEngine, matrix: Matrix) {
                val editorRect = Rect().apply {
                    codeEditorZoomLayout.getLocalVisibleRect(this)
                }

                lineNumberZoomLayout.layoutParams.apply {
                    val scaledWidth = lineNumberTextView.width * engine.realZoom
                    val maxWidth = editorRect.width() / 3F
                    val targetWidth = Math.min(scaledWidth, maxWidth)
                    width = targetWidth.toInt()

                    lineNumberZoomLayout.layoutParams = this
                }

                lineNumberZoomLayout.moveTo(
                        engine.zoom,
                        -engine.computeHorizontalScrollRange().toFloat(),
                        engine.panY,
                        false)

                if (showMinimap) {
                    updateMinimapIndicator(engine, editorRect)
                }
            }
        })


        codeEditorZoomLayout.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (showMinimap) {
                val previewImage = getBitmapFromView(
                        codeEditorZoomLayout.codeEditText,
                        dimensionLimit = 200.dpToPx(context))
                minimapZoomLayout.setImageBitmap(previewImage)
            }
        }

        setOnTouchListener { view, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_MOVE -> {
                    internalMoveWithCursorEnabled = false
                }
            }
            false
        }

        codeEditorZoomLayout.codeEditText.setOnClickListener {
            internalMoveWithCursorEnabled = true
        }

        if (isMoveWithCursorEnabled) {
            Observable.interval(250, TimeUnit.MILLISECONDS)
                    .filter { internalMoveWithCursorEnabled }
                    .bindToLifecycle(this)
                    .subscribeBy(onNext = {
                        try {
                            moveScreenWithCursorIfNecessary()
                        } catch (e: Throwable) {
                            Log.e(CodeEditorView.TAG, "Error moving screen with cursor", e)
                        }
                    }, onError = {
                        Log.e(CodeEditorView.TAG, "Unrecoverable error while moving screen with cursor", it)
                    })
        }

        RxTextView.textChanges(codeEditorZoomLayout.codeEditText)
                .debounce(50, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .bindToLifecycle(this)
                .subscribeBy(onNext = {
                    try {
                        syncLineNumbersWithEditor()
                    } catch (e: Throwable) {
                        Log.e(CodeEditorView.TAG, "Error updating line numbers", e)
                    }
                }, onError = {
                    Log.e(CodeEditorView.TAG, "Unrecoverable error while updating line numbers", it)
                })
    }

    private fun updateMinimapIndicator(engine: ZoomEngine, editorRect: Rect) {
        // update minimap indicator position
        (minimapIndicator.layoutParams as MarginLayoutParams).apply {
            val minimapBorder = resources.getDimensionPixelSize(R.dimen.cev_minimap_border_size)
            topMargin = minimapBorder + (minimapZoomLayout.height * (engine.computeVerticalScrollOffset().toFloat() / engine.computeVerticalScrollRange())).toInt()
            leftMargin = minimapBorder + (minimapZoomLayout.width * (engine.computeHorizontalScrollOffset().toFloat() / engine.computeHorizontalScrollRange())).toInt()

            width = (minimapZoomLayout.width * (editorRect.width().toFloat() / engine.computeHorizontalScrollRange())).toInt()
            height = (minimapZoomLayout.height * (editorRect.height().toFloat() / engine.computeVerticalScrollRange())).toInt()
            minimapIndicator.layoutParams = this
        }
    }

    private fun syncLineNumbersWithEditor() {
        codeEditorZoomLayout.post {
            // linenumbers always have to be the exact same size as the content
            lineNumberTextView.height = codeEditorZoomLayout.engine.computeVerticalScrollRange()

            val lineCount = codeEditorZoomLayout.getLineCount()
            if (lineCount != currentLineCount) {
                updateLineNumberText(lineCount)
            }
        }
    }

    /**
     * Renders a view to a bitmap
     *
     * @param view the view to render
     * @param dimensionLimit the maximum image dimension
     * @param backgroundColor background color in case the view doesn't have one
     * @return the rendered image or null if the view has no measured dimensions (yet)
     */
    fun getBitmapFromView(view: View, dimensionLimit: Float = Float.NaN, backgroundColor: Int = Color.TRANSPARENT): Bitmap? {
        if (view.measuredWidth == 0 || view.measuredHeight == 0) {
            return null
        }

        val scaleFactor = if (dimensionLimit.isNaN()) {
            1F // do not scale
        } else {
            // select smaller scaling factor to match dimensionLimit
            Math.min(
                    Math.min(dimensionLimit / view.measuredWidth,
                            dimensionLimit / view.measuredHeight),
                    1F)
        }

        // Define a bitmap with the target dimensions
        val returnedBitmap = Bitmap.createBitmap(
                (view.measuredWidth * scaleFactor).toInt(),
                (view.measuredHeight * scaleFactor).toInt(),
                Bitmap.Config.ARGB_8888)

        // bind a canvas to the bitmap
        Canvas(returnedBitmap).apply {
            scale(scaleFactor, scaleFactor)
            drawColor(backgroundColor)
            view.background?.draw(this)
            view.draw(this)
        }

        return returnedBitmap
    }

    /**
     * @return true if editable, false otherwise
     */
    fun isEditable() = codeEditorZoomLayout.isEditable()

    /**
     * @param editable true = user can type, false otherwise
     */
    fun setEditable(editable: Boolean) = codeEditorZoomLayout.setEditable(editable)

    /**
     * Set the text in the editor
     *
     * @param text the new text
     */
    fun setText(text: CharSequence) {
        codeEditorZoomLayout.setText(text)
        updateLineNumberText(codeEditorZoomLayout.getLineCount())
    }

    /**
     * Set the text in the editor
     *
     * @param text string resource of the new text
     */
    fun setText(@StringRes text: Int) = setText(context.getString(text))

    /**
     * Set the syntax highlighter to use for this CodeEditor
     *
     * @param syntaxHighlighter
     */
    @Suppress("unused")
    fun setSyntaxHighlighter(syntaxHighlighter: SyntaxHighlighter?) {
        codeEditorZoomLayout.setSyntaxHighlighter(syntaxHighlighter)
    }

    private fun updateLineNumberText(lines: Long) {
        currentLineCount = lines
        val linesToDraw = Math.max(MIN_LINES, lines)
        lineNumberTextView.text = createLineNumberText(linesToDraw)
    }

    private fun createLineNumberText(lines: Long): String {
        return (1..lines).joinToString(separator = "$LINE_NUMBER_SUFFIX\n",
                postfix = LINE_NUMBER_SUFFIX)
    }

    private fun moveScreenWithCursorIfNecessary() {
        val position = calculateCursorPosition()
        val visibleRect = calculateVisibleCodeArea()

        if (!visibleRect.contains(position.x.roundToInt(), position.y.roundToInt())) {
            val newX = when {
                position.x < visibleRect.left || position.x > visibleRect.right -> -x
                else -> codeEditorZoomLayout.panX
            }

            val newY = when {
                position.y < visibleRect.top || position.y > visibleRect.bottom -> -y
                else -> codeEditorZoomLayout.panY
            }

            codeEditorZoomLayout.moveTo(codeEditorZoomLayout.zoom, newX, newY, true)
        }
    }

    private fun calculateVisibleCodeArea(): Rect {
        return Rect().apply { codeEditorZoomLayout.getLocalVisibleRect(this) }
    }

    private fun calculateCursorPosition(): PointF {
        val pos = codeEditorZoomLayout.codeEditText.selectionStart
        val layout = codeEditorZoomLayout.codeEditText.layout

        val line = layout.getLineForOffset(pos)
        val baseline = layout.getLineBaseline(line)
        val ascent = layout.getLineAscent(line)
        val x = layout.getPrimaryHorizontal(pos)
        val y = (baseline + ascent).toFloat()

        return PointF(x * codeEditorZoomLayout.realZoom + codeEditorZoomLayout.panX * codeEditorZoomLayout.realZoom + lineNumberTextView.width * codeEditorZoomLayout.realZoom,
                y * codeEditorZoomLayout.realZoom + codeEditorZoomLayout.panY * codeEditorZoomLayout.realZoom)
    }

    companion object {
        const val MIN_LINES = 1L
        const val DEFAULT_TEXT_SIZE_SP = 12F
        const val LINE_NUMBER_SUFFIX = ":"

        const val DEFAULT_SHOW_DIVIDER = true
        const val DEFAULT_SHOW_MINIMAP = true
        const val DEFAULT_MAX_ZOOM = 10F
    }

}
