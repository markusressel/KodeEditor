package de.markusressel.kodeeditor.library.view

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import android.os.Build
import android.support.annotation.StringRes
import android.text.Layout
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.jakewharton.rxbinding2.widget.RxTextView
import com.otaliastudios.zoom.ZoomApi
import com.otaliastudios.zoom.ZoomEngine
import com.otaliastudios.zoom.ZoomLayout
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import de.markusressel.kodeeditor.library.R
import de.markusressel.kodeeditor.library.extensions.getColor
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighter
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
private constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        /**
         * The ZoomLayout containing the [CodeEditText].
         */
        internal val codeEditorZoomLayout: CodeEditorView)
    : LinearLayout(context, attrs, defStyleAttr),
        ZoomApi by codeEditorZoomLayout {

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
     * The (optional) divider between [lineNumberZoomLayout] and [codeEditorZoomLayout]
     */
    internal lateinit var dividerView: View

    private var currentLineCount = -1L

    /**
     * Controls whether to follow cursor movements or not.
     */
    var isMoveWithCursorEnabled = false
    private var internalMoveWithCursorEnabled = false

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
            this(context, attrs, defStyleAttr, View.inflate(context, R.layout.view_code_editor__editor, null) as CodeEditorView)

    init {
        orientation = LinearLayout.HORIZONTAL
        inflateViews(LayoutInflater.from(context))
        readParameters(attrs, defStyleAttr)
        setListeners()

        syncLineNumbersWithEditor()
    }

    private fun inflateViews(layoutInflater: LayoutInflater) {
        lineNumberZoomLayout = layoutInflater.inflate(R.layout.view_code_editor__linenumbers, this).findViewById(R.id.cev_linenumbers_zoomLayout)
        lineNumberTextView = lineNumberZoomLayout.findViewById(R.id.cev_linenumbers_textview) as TextView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            lineNumberTextView.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        }

        dividerView = layoutInflater.inflate(R.layout.view_code_editor__divider, this).findViewById(R.id.cev_divider)

        addView(codeEditorZoomLayout)
    }

    private fun readParameters(attrs: AttributeSet?, defStyleAttr: Int) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.CodeEditorView, defStyleAttr, 0)

        val lineNumberTextColor = a.getColor(context, R.styleable.CodeEditorView_cev_lineNumbers_textColor, R.attr.cev_lineNumbers_textColor, android.R.attr.textColorPrimary)
        lineNumberTextView.setTextColor(lineNumberTextColor)

        val lineNumberBackgroundColor = a.getColor(context, R.styleable.CodeEditorView_cev_lineNumbers_backgroundColor, R.attr.cev_lineNumbers_backgroundColor, android.R.attr.windowBackground)
        lineNumberZoomLayout.setBackgroundColor(lineNumberBackgroundColor)


        val dividerEnabled = a.getBoolean(R.styleable.CodeEditorView_cev_divider, true)
        dividerView.visibility = when (dividerEnabled) {
            true -> View.VISIBLE
            else -> View.GONE
        }
        val dividerColor = a.getColor(context, R.styleable.CodeEditorView_cev_divider_color, R.attr.cev_divider_color, android.R.attr.textColorPrimary)
        dividerView.setBackgroundColor(dividerColor)

        val editTextBackgroundColor = a.getColor(context, R.styleable.CodeEditorView_cev_editor_backgroundColor, R.attr.cev_editor_backgroundColor, android.R.attr.windowBackground)
        codeEditorZoomLayout.codeEditText.setBackgroundColor(editTextBackgroundColor)

        val codeEditorMaxZoom = a.getFloat(R.styleable.CodeEditorView_cev_editor_maxZoom, 10F)
        lineNumberZoomLayout.setMaxZoom(codeEditorMaxZoom, ZoomApi.TYPE_REAL_ZOOM)
        codeEditorZoomLayout.setMaxZoom(codeEditorMaxZoom, ZoomApi.TYPE_REAL_ZOOM)

        a.recycle()
    }

    private fun setListeners() {
        // add listener to code editor to keep linenumbers position and zoom in sync
        codeEditorZoomLayout.engine.addListener(object : ZoomEngine.Listener {
            override fun onIdle(engine: ZoomEngine) {
            }

            override fun onUpdate(engine: ZoomEngine, matrix: Matrix) {
                lineNumberZoomLayout.layoutParams = lineNumberZoomLayout.layoutParams.apply {
                    val scaledWidth = lineNumberTextView.width * engine.realZoom
                    val maxWidth = Rect().apply { codeEditorZoomLayout.getLocalVisibleRect(this) }.width() / 3F
                    val targetWidth = Math.min(scaledWidth, maxWidth)
                    width = targetWidth.toInt()
                }

                lineNumberZoomLayout.moveTo(engine.zoom, -engine.computeHorizontalScrollRange().toFloat(), engine.panY, false)
            }
        })

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
                else -> panX
            }

            val newY = when {
                position.y < visibleRect.top || position.y > visibleRect.bottom -> -y
                else -> panY
            }

            moveTo(zoom, newX, newY, true)
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

        return PointF(x * realZoom + panX * realZoom + lineNumberTextView.width * realZoom,
                y * realZoom + panY * realZoom)
    }

    companion object {
        const val MIN_LINES = 1L
        const val DEFAULT_TEXT_SIZE_SP = 12F
        const val LINE_NUMBER_SUFFIX = ":"
    }

}
