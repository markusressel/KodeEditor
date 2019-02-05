package de.markusressel.kodeeditor.library.view

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.support.annotation.ColorInt
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
import de.markusressel.kodeeditor.library.extensions.createSnapshot
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
    lateinit var codeEditorView: CodeEditorView

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
     * The (optional) divider between [lineNumberZoomLayout] and [codeEditorView]
     */
    internal lateinit var dividerView: View

    /**
     * Controls whether to follow cursor movements or not.
     */
    var isMoveWithCursorEnabled = false
    private var internalMoveWithCursorEnabled = false

    /**
     * The currently set text
     */
    var text: String
        set(value) {
            codeEditorView.text = value
            updateLineNumbers()
            updateMinimap()
        }
        get() = codeEditorView.codeEditText.text.toString()

    /**
     * The currently active syntax highlighter (if any)
     */
    var syntaxHighlighter: SyntaxHighlighter?
        get() = codeEditorView.syntaxHighlighter
        set(value) {
            codeEditorView.syntaxHighlighter = value
        }

    /**
     * Set the text in the editor
     *
     * @param text string resource of the new text
     */
    fun setText(@StringRes text: Int) {
        this.text = context.getString(text)
    }

    /**
     * Controls wheter the text is editable or not.
     */
    var editable: Boolean
        set(value) {
            codeEditorView.editable = value
            updateMinimap()
        }
        get() = codeEditorView.editable

    /**
     * Controls whether the divider between line numbers and code editor is visible.
     */
    var showDivider: Boolean
        set(value) {
            dividerView.visibility = if (value) View.VISIBLE else View.GONE
        }
        get() = dividerView.visibility == View.VISIBLE

    /**
     * Indicates if the minimap should be shown or not.
     */
    var showMinimap = DEFAULT_SHOW_MINIMAP
        set(value) {
            field = value
            minimapContainerLayout.visibility = if (value) View.VISIBLE else View.GONE
        }

    /**
     * The width & height limit of the minimap
     */
    var minimapMaxDimension = DEFAULT_MINIMAP_MAX_DIMENSION_DP.dpToPx(context)
        set(value) {
            field = value
            updateMinimap()
        }

    /**
     * The width of the border around the minimap
     */
    var minimapBorderWidth: Number = 2.dpToPx(context)
        set(value) {
            field = value

            val valueAsInt = field.toFloat().roundToInt()
            (minimapZoomLayout.layoutParams as MarginLayoutParams).apply {
                setMargins(valueAsInt, valueAsInt, valueAsInt, valueAsInt)
            }

            minimapContainerLayout.background = GradientDrawable().apply {
                setStroke(valueAsInt, minimapBorderColor)
            }
        }

    /**
     * The color of the minimap border
     */
    @ColorInt
    var minimapBorderColor: Int = 0
        set(value) {
            field = value

            minimapContainerLayout.background = GradientDrawable().apply {
                setStroke(minimapBorderWidth.toFloat().roundToInt(), field)
            }
        }

    /**
     * The color of the minimap indicator
     */
    @ColorInt
    var minimapIndicatorColor: Int = 0
        set(value) {
            field = value

            minimapIndicator.background = GradientDrawable().apply {
                setStroke(minimapBorderWidth.toFloat().roundToInt(), field)
            }
        }

    private var currentDrawnLineCount = -1L

    /**
     * Text size in SP
     */
    private var textSizeSp: Float = DEFAULT_TEXT_SIZE_SP

    /**
     * Text size in PX
     */
    private var textSizePx: Float
        get() = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSizeSp, context.resources.displayMetrics)
        set(value) {
            textSizeSp = value / resources.displayMetrics.scaledDensity
        }

    @ColorInt
    private var editorBackgroundColor: Int = 0

    init {
        inflateViews(LayoutInflater.from(context))
        readParameters(attrs, defStyleAttr)
        setListeners()

        updateLineNumbers()
    }

    private fun inflateViews(layoutInflater: LayoutInflater) {
        layoutInflater.inflate(R.layout.layout_code_editor__main_layout, this)

        lineNumberZoomLayout = findViewById(R.id.cel_linenumbers_zoomLayout)
        lineNumberTextView = findViewById(R.id.cel_linenumbers_textview)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            lineNumberTextView.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        }

        dividerView = findViewById(R.id.cel_divider)
        codeEditorView = findViewById(R.id.cel_codeEditorView)

        minimapContainerLayout = findViewById(R.id.cel_minimap_container)
        minimapZoomLayout = minimapContainerLayout.findViewById(R.id.cel_minimap)
        minimapIndicator = minimapContainerLayout.findViewById(R.id.cel_minimap_indicator)
    }

    private fun readParameters(attrs: AttributeSet?, defStyleAttr: Int) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.CodeEditorLayout, defStyleAttr, 0)

        val lineNumberTextColor = a.getColor(context,
                R.styleable.CodeEditorLayout_ke_lineNumbers_textColor,
                R.attr.ke_lineNumbers_textColor,
                android.R.attr.textColorPrimary)
        lineNumberTextView.setTextColor(lineNumberTextColor)

        val lineNumberBackgroundColor = a.getColor(context,
                R.styleable.CodeEditorLayout_ke_lineNumbers_backgroundColor,
                R.attr.ke_lineNumbers_backgroundColor,
                android.R.attr.windowBackground)
        lineNumberZoomLayout.setBackgroundColor(lineNumberBackgroundColor)


        showDivider = a.getBoolean(R.styleable.CodeEditorLayout_ke_divider_enabled, DEFAULT_SHOW_DIVIDER)

        val dividerColor = a.getColor(context,
                R.styleable.CodeEditorLayout_ke_divider_color,
                R.attr.ke_divider_color,
                android.R.attr.textColorPrimary)
        dividerView.setBackgroundColor(dividerColor)

        editorBackgroundColor = a.getColor(context,
                R.styleable.CodeEditorLayout_ke_editor_backgroundColor,
                R.attr.ke_editor_backgroundColor,
                android.R.attr.windowBackground)
        codeEditorView.setBackgroundColor(editorBackgroundColor)

        val codeEditorMaxZoom = a.getFloat(R.styleable.CodeEditorLayout_ke_editor_maxZoom, CodeEditorView.DEFAULT_MAX_ZOOM)
        lineNumberZoomLayout.setMaxZoom(codeEditorMaxZoom, ZoomApi.TYPE_REAL_ZOOM)
        codeEditorView.setMaxZoom(codeEditorMaxZoom, ZoomApi.TYPE_REAL_ZOOM)

        showMinimap = a.getBoolean(R.styleable.CodeEditorLayout_ke_minimap_enabled, DEFAULT_SHOW_MINIMAP)
        minimapMaxDimension = a.getDimensionPixelSize(R.styleable.CodeEditorLayout_ke_minimap_maxDimension, DEFAULT_MINIMAP_MAX_DIMENSION_DP).toFloat()
        minimapBorderColor = a.getColor(context,
                R.styleable.CodeEditorLayout_ke_minimap_borderColor,
                R.attr.ke_minimap_borderColor,
                android.R.attr.textColorPrimary)

        minimapIndicatorColor = a.getColor(context,
                R.styleable.CodeEditorLayout_ke_minimap_indicatorColor,
                R.attr.ke_minimap_indicatorColor,
                android.R.attr.textColorPrimary)

        a.recycle()
    }

    private fun setListeners() {
        // add listener to code editor to keep linenumbers position and zoom in sync
        codeEditorView.engine.addListener(object : ZoomEngine.Listener {
            override fun onIdle(engine: ZoomEngine) {}

            override fun onUpdate(engine: ZoomEngine, matrix: Matrix) {
                val editorRect = calculateVisibleCodeArea()
                updateLineNumbers(editorRect, updateLineCount = false)

                if (showMinimap) {
                    updateMinimapIndicator(editorRect)
                }
            }
        })


        codeEditorView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (showMinimap) {
                updateMinimap()
            }
        }

        @Suppress("ClickableViewAccessibility")
        minimapZoomLayout.setOnTouchListener { v, event ->
            if (!showMinimap) return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN or MotionEvent.ACTION_MOVE -> {
                    val viewX = event.x - v.left
                    val viewY = event.y - v.top
                    val offsetX = minimapIndicator.width / 2F
                    val offsetY = minimapIndicator.height / 2F
                    val percentageX = (viewX - offsetX) / v.width
                    val percentageY = (viewY - offsetY) / v.height

                    moveEditorToPercentage(percentageX, percentageY)
                    true
                }
                else -> false
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

        codeEditorView.codeEditText.setOnClickListener {
            internalMoveWithCursorEnabled = true
        }

        if (isMoveWithCursorEnabled) {
            val d = Observable.interval(250, TimeUnit.MILLISECONDS)
                    .filter { internalMoveWithCursorEnabled }
                    .bindToLifecycle(this)
                    .subscribeBy(onNext = {
                        try {
                            moveToCursorIfNecessary()
                        } catch (e: Throwable) {
                            Log.e(CodeEditorView.TAG, "Error moving screen with cursor", e)
                        }
                    }, onError = {
                        Log.e(CodeEditorView.TAG, "Unrecoverable error while moving screen with cursor", it)
                    })
        }

        val d = RxTextView.textChanges(codeEditorView.codeEditText)
                .debounce(50, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .bindToLifecycle(this)
                .subscribeBy(onNext = {
                    try {
                        updateLineNumbers()
                    } catch (e: Throwable) {
                        Log.e(CodeEditorView.TAG, "Error updating line numbers", e)
                    }
                }, onError = {
                    Log.e(CodeEditorView.TAG, "Unrecoverable error while updating line numbers", it)
                })
    }

    /**
     * Helper function to move the editor content to a percentage based position
     *
     * @param percentageX x-axis percentage
     * @param percentageY y-axis percentage
     */
    private fun moveEditorToPercentage(percentageX: Float, percentageY: Float) {
        val targetX = -codeEditorView.engine.computeHorizontalScrollRange() / codeEditorView.engine.zoom * percentageX
        val targetY = -codeEditorView.engine.computeVerticalScrollRange() / codeEditorView.engine.zoom * percentageY

        codeEditorView.moveTo(codeEditorView.zoom, targetX, targetY, false)
    }

    /**
     * Updates the minimap
     */
    private fun updateMinimap() {
        updateMinimapImage()
        updateMinimapIndicator()
    }

    /**
     * Renders the current text and applies it to the minimap
     */
    private fun updateMinimapImage() {
        val targetView: View = if (editable) codeEditorView.codeEditText else codeEditorView.codeTextView
        targetView.apply {
            post {
                val minimapSnapshot = createSnapshot(
                        dimensionLimit = minimapMaxDimension,
                        backgroundColor = editorBackgroundColor)
                minimapZoomLayout.setImageBitmap(minimapSnapshot)
            }
        }
    }

    /**
     * Updates the minimap indicator position and size
     *
     * @param editorRect the dimensions of the [codeEditorView]
     */
    private fun updateMinimapIndicator(editorRect: Rect = calculateVisibleCodeArea()) {
        codeEditorView.post {
            val engine = codeEditorView.engine

            // update minimap indicator position and size
            (minimapIndicator.layoutParams as MarginLayoutParams).apply {
                val minimapBorder = minimapBorderWidth.toFloat()
                topMargin = (minimapBorder +
                        (minimapZoomLayout.height *
                                (engine.computeVerticalScrollOffset().toFloat() / engine.computeVerticalScrollRange()))).roundToInt()
                leftMargin = (minimapBorder +
                        (minimapZoomLayout.width *
                                (engine.computeHorizontalScrollOffset().toFloat() / engine.computeHorizontalScrollRange()))).roundToInt()

                width = (minimapZoomLayout.width * (editorRect.width().toFloat() / engine.computeHorizontalScrollRange())).roundToInt()
                height = (minimapZoomLayout.height * (editorRect.height().toFloat() / engine.computeVerticalScrollRange())).roundToInt()
                minimapIndicator.layoutParams = this
            }
        }
    }

    /**
     * Synchronizes zoom & position of [lineNumberTextView] with the [codeEditorView],
     * and updates the line number text.
     *
     * @param updateLineCount true updates line numbers (this is quite expensive), false doesn't
     */
    private fun updateLineNumbers(editorRect: Rect = calculateVisibleCodeArea(),
                                  updateLineCount: Boolean = true) {
        if (updateLineCount) {
            updateLineNumberText()
        }

        // adjust width of line numbers based on zoom
        val engine = codeEditorView.engine
        lineNumberZoomLayout.layoutParams.apply {
            val scaledWidth = lineNumberTextView.width * engine.realZoom
            val maxWidth = editorRect.width() / 3F
            val targetWidth = Math.min(scaledWidth, maxWidth)
            width = targetWidth.roundToInt()

            lineNumberZoomLayout.layoutParams = this
        }

        // synchronize zoom and vertical pan to match code editor
        lineNumberZoomLayout.moveTo(
                engine.zoom,
                -engine.computeHorizontalScrollRange().toFloat(),
                engine.panY,
                false)
    }

    /**
     * Updates the text of the [lineNumberTextView] to match the line count in the [codeEditorView]
     *
     * @param lineCount the amount of lines to show
     */
    private fun updateLineNumberText(lineCount: Long = codeEditorView.getLineCount()) {
        val linesToDraw = Math.max(MIN_LINES_DRAWN, lineCount)
        if (linesToDraw == currentDrawnLineCount) {
            return
        }

        currentDrawnLineCount = linesToDraw
        lineNumberTextView.text = createLineNumberText(linesToDraw)

        codeEditorView.post {
            // linenumbers always have to be the exact same size as the content
            lineNumberTextView.height = codeEditorView.engine.computeVerticalScrollRange()
        }
    }

    /**
     * Creates the text that is used on [lineNumberTextView] to show line numbers.
     *
     * @param lines the amount of lines
     * @return the text to show for the given amount of lines
     */
    private fun createLineNumberText(lines: Long): String {
        return (1..lines).joinToString(separator = "$LINE_NUMBER_SUFFIX\n",
                postfix = LINE_NUMBER_SUFFIX)
    }

    /**
     * Moves the screen so that the cursor is visible.
     */
    private fun moveToCursorIfNecessary() {
        val position = calculateCursorPosition()
        val visibleArea = calculateVisibleCodeArea()

        if (!visibleArea.contains(position.x.roundToInt(), position.y.roundToInt())) {
            val newX = when {
                position.x < visibleArea.left || position.x > visibleArea.right -> -x
                else -> codeEditorView.panX
            }

            val newY = when {
                position.y < visibleArea.top || position.y > visibleArea.bottom -> -y
                else -> codeEditorView.panY
            }

            codeEditorView.moveTo(codeEditorView.zoom, newX, newY, true)
        }
    }

    /**
     * @return the currently visible area of the [codeEditorView]
     */
    private fun calculateVisibleCodeArea() = Rect().apply {
        codeEditorView.getLocalVisibleRect(this)
    }

    /**
     * @return the position of the cursor in relation to the [codeEditorView] content.
     */
    private fun calculateCursorPosition(): PointF {
        val pos = codeEditorView.codeEditText.selectionStart
        val layout = codeEditorView.codeEditText.layout

        val line = layout.getLineForOffset(pos)
        val baseline = layout.getLineBaseline(line)
        val ascent = layout.getLineAscent(line)
        val x = layout.getPrimaryHorizontal(pos)
        val y = (baseline + ascent).toFloat()

        return PointF(x * codeEditorView.realZoom + codeEditorView.panX * codeEditorView.realZoom + lineNumberTextView.width * codeEditorView.realZoom,
                y * codeEditorView.realZoom + codeEditorView.panY * codeEditorView.realZoom)
    }

    companion object {
        const val MIN_LINES_DRAWN = 1L
        const val DEFAULT_TEXT_SIZE_SP = 12F
        const val LINE_NUMBER_SUFFIX = ":"

        const val DEFAULT_SHOW_DIVIDER = true
        const val DEFAULT_SHOW_MINIMAP = true
        const val DEFAULT_MINIMAP_MAX_DIMENSION_DP = 150
    }

}
