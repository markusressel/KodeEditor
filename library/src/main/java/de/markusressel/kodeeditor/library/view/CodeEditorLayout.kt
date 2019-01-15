package de.markusressel.kodeeditor.library.view

import android.content.Context
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Build
import android.support.annotation.StringRes
import android.text.Layout
import android.util.AttributeSet
import android.util.Log
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
private constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, private val codeEditorZoomLayout: CodeEditorView)
    : LinearLayout(context, attrs, defStyleAttr), ZoomApi by codeEditorZoomLayout {

    /**
     * The view displaying line numbers
     */
    internal lateinit var lineNumberZoomLayout: ZoomLayout
    internal lateinit var lineNumberTextView: TextView

    /**
     * The divider between line numbers and text editor
     */
    internal lateinit var dividerView: View

    private var currentLineCount = -1

    var mMoveWithCursorEnabled = false
    private var moveWithCursorEnabled = false

    private var internalMoveWithCursorEnabled = false

    init {
        initialize(attrs, defStyleAttr)
    }

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
            this(context, attrs, defStyleAttr, View.inflate(context, R.layout.view_code_editor__editor, null) as CodeEditorView)

    private fun initialize(attrs: AttributeSet?, defStyleAttr: Int) {
        orientation = LinearLayout.HORIZONTAL
        inflateViews(LayoutInflater.from(context))
        readParameters(attrs, defStyleAttr)
        setListeners()
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
        codeEditorZoomLayout.editTextView.setBackgroundColor(editTextBackgroundColor)

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
                    // TODO: the base line number width depends on how wide the longest line number text is,
                    // so this should not be a hardcoded value but rather computed based on currentLineCount
                    val defaultWidth = resources.getDimensionPixelSize(R.dimen.cev_linenumber_width)
                    width = (defaultWidth * engine.realZoom).toInt()
                }

                lineNumberZoomLayout.moveTo(engine.zoom, -engine.computeHorizontalScrollRange().toFloat(), engine.panY, false)
            }
        })

        addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            // TODO: only update when CodeEditor finishes layout phase
            updateLineNumbers(codeEditorZoomLayout.editTextView.lineCount)
        }

        setOnTouchListener { view, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_MOVE -> {
                    moveWithCursorEnabled = false
                }
            }
            false
        }

        codeEditorZoomLayout.editTextView.setOnClickListener {
            moveWithCursorEnabled = true
        }

        if (mMoveWithCursorEnabled) {
            Observable.interval(250, TimeUnit.MILLISECONDS)
                    .filter { moveWithCursorEnabled }
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

        RxTextView.textChanges(codeEditorZoomLayout.editTextView)
                .debounce(50, TimeUnit.MILLISECONDS)
                .filter {
                    moveWithCursorEnabled = true
                    codeEditorZoomLayout.editTextView.lineCount != currentLineCount
                }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .bindToLifecycle(this)
                .subscribeBy(onNext = {
                    try {
                        updateLineNumbers(codeEditorZoomLayout.editTextView.lineCount)
                    } catch (e: Throwable) {
                        Log.e(CodeEditorView.TAG, "Error updating line numbers", e)
                    }
                }, onError = {
                    Log.e(CodeEditorView.TAG, "Unrecoverable error while updating line numbers", it)
                })
    }

    /**
     * @param editable true = user can type, false otherwise
     */
    fun setEditable(editable: Boolean) {
        codeEditorZoomLayout.setEditable(editable)
    }

    /**
     * Set the text in the editor
     */
    fun setText(text: CharSequence) {
        codeEditorZoomLayout.setText(text)
        updateLineNumbers(codeEditorZoomLayout.editTextView.lineCount)
    }

    /**
     * Set the text in the editor
     */
    @Suppress("unused")
    fun setText(@StringRes text: Int) {
        codeEditorZoomLayout.setText(text)
    }

    /**
     * Set the syntax highlighter to use for this CodeEditor
     */
    @Suppress("unused")
    fun setSyntaxHighlighter(syntaxHighlighter: SyntaxHighlighter) {
        codeEditorZoomLayout.setSyntaxHighlighter(syntaxHighlighter)
    }

    private fun updateLineNumbers(lines: Int) {
        currentLineCount = lines

        val linesToDraw = Math.max(MIN_LINES, lines)

        val sb = StringBuilder()
        for (i in 1..linesToDraw) {
            sb.append("$i:\n")
        }

        lineNumberTextView.text = sb.toString()
    }

    private fun moveScreenWithCursorIfNecessary() {
        val pos = codeEditorZoomLayout.editTextView.selectionStart
        val layout = codeEditorZoomLayout.editTextView.layout

        if (layout != null) {
            val line = layout.getLineForOffset(pos)
            val baseline = layout.getLineBaseline(line)
            val ascent = layout.getLineAscent(line)
            val x = layout.getPrimaryHorizontal(pos)
            val y = (baseline + ascent).toFloat()

            val zoomLayoutRect = Rect()
            getLocalVisibleRect(zoomLayoutRect)

            val transformedX = x * realZoom + panX * realZoom + lineNumberTextView.width * realZoom
            val transformedY = y * realZoom + panY * realZoom

            if (!zoomLayoutRect.contains(transformedX.roundToInt(), transformedY.roundToInt())) {

                var newX = panX
                var newY = panY

                if (transformedX < zoomLayoutRect.left || transformedX > zoomLayoutRect.right) {
                    newX = -x
                }

                if (transformedY < zoomLayoutRect.top || transformedY > zoomLayoutRect.bottom) {
                    newY = -y
                }

                moveTo(zoom, newX, newY, false)
            }
        }
    }

    companion object {
        const val MIN_LINES = 1
    }

}
