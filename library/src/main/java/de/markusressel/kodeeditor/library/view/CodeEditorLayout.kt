package de.markusressel.kodeeditor.library.view

import android.content.Context
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
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import de.markusressel.kodeeditor.library.R
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
private constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, private val codeEditorView: CodeEditorView)
    : LinearLayout(context, attrs, defStyleAttr), ZoomApi by codeEditorView {


    /**
     * The view displaying line numbers
     */
    internal lateinit var lineNumberView: TextView

    /**
     * The divider between line numbers and text editor
     */
    internal lateinit var dividerView: View

    private var currentLineCount = -1
    private var moveWithCursorEnabled = false

    init {
        initialize(attrs, defStyleAttr)
    }

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
            this(context, attrs, defStyleAttr, View.inflate(context, R.layout.view_code_editor__editor, null) as CodeEditorView)

    private fun initialize(attrs: AttributeSet?, defStyleAttr: Int) {
        inflateViews(LayoutInflater.from(context))
        readParameters(attrs, defStyleAttr)
        setListeners()
    }

    private fun inflateViews(layoutInflater: LayoutInflater) {
        lineNumberView = View.inflate(context, R.layout.view_code_editor__linenumbers, this) as TextView
        dividerView = View.inflate(context, R.layout.view_code_editor__divider, this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            lineNumberView.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        }

        addView(lineNumberView)
        addView(dividerView)
        addView(codeEditorView)
    }

    private fun readParameters(attrs: AttributeSet?, defStyleAttr: Int) {
        val a = context
                .obtainStyledAttributes(attrs, R.styleable.CodeEditorView, defStyleAttr, 0)

        val lineNumberTextColor = a
                .getColor(R.styleable.CodeEditorView_cev_lineNumbers_textColor, R.attr.cev_lineNumbers_textColor, android.R.attr.textColorPrimary)
        lineNumberView
                .setTextColor(lineNumberTextColor)

        val lineNumberBackgroundColor = a
                .getColor(R.styleable.CodeEditorView_cev_lineNumbers_backgroundColor, R.attr.cev_lineNumbers_backgroundColor, android.R.attr.windowBackground)
        lineNumberView
                .setBackgroundColor(lineNumberBackgroundColor)


        val dividerEnabled = a
                .getBoolean(R.styleable.CodeEditorView_cev_divider, true)
        dividerView
                .visibility = when (dividerEnabled) {
            true -> View.VISIBLE
            else -> View.GONE
        }
        val dividerColor = a
                .getColor(R.styleable.CodeEditorView_cev_divider_color, R.attr.cev_divider_color, android.R.attr.textColorPrimary)
        dividerView
                .setBackgroundColor(dividerColor)

        val editTextBackgroundColor = a
                .getColor(R.styleable.CodeEditorView_cev_editor_backgroundColor, R.attr.cev_editor_backgroundColor, android.R.attr.windowBackground)
        codeEditorView.editTextView.setBackgroundColor(editTextBackgroundColor)

        a
                .recycle()
    }

    private fun setListeners() {
        addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            // TODO: only update when CodeEditor finishes layout phase
            updateLineNumbers(codeEditorView.editTextView.lineCount)
        }

        setOnTouchListener { view, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_MOVE -> {
                    moveWithCursorEnabled = false
                }
            }
            false
        }

        codeEditorView.editTextView
                .setOnClickListener {
                    moveWithCursorEnabled = true
                }

        Observable
                .interval(250, TimeUnit.MILLISECONDS)
                .filter { moveWithCursorEnabled }
                .bindToLifecycle(this)
                .subscribeBy(onNext = {
                    try {
                        moveScreenWithCursorIfNecessary()
                    } catch (e: Throwable) {
                        Log
                                .e(CodeEditorView.TAG, "Error moving screen with cursor", e)
                    }
                }, onError = {
                    Log
                            .e(CodeEditorView.TAG, "Unrecoverable error while moving screen with cursor", it)
                })

        RxTextView
                .textChanges(codeEditorView.editTextView)
                .debounce(50, TimeUnit.MILLISECONDS)
                .filter {
                    moveWithCursorEnabled = true
                    codeEditorView.editTextView.lineCount != currentLineCount
                }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .bindToLifecycle(this)
                .subscribeBy(onNext = {
                    try {
                        updateLineNumbers(codeEditorView.editTextView.lineCount)
                    } catch (e: Throwable) {
                        Log
                                .e(CodeEditorView.TAG, "Error updating line numbers", e)
                    }
                }, onError = {
                    Log
                            .e(CodeEditorView.TAG, "Unrecoverable error while updating line numbers", it)
                })
    }

    /**
     * @param editable true = user can type, false otherwise
     */
    fun setEditable(editable: Boolean) {
        codeEditorView.setEditable(editable)
    }

    /**
     * Set the text in the editor
     */
    fun setText(text: CharSequence) {
        codeEditorView.setText(text)
        updateLineNumbers(codeEditorView.editTextView.lineCount)
    }

    /**
     * Set the text in the editor
     */
    @Suppress("unused")
    fun setText(@StringRes text: Int) {
        codeEditorView.setText(text)
    }

    /**
     * Set the syntax highlighter to use for this CodeEditor
     */
    @Suppress("unused")
    fun setSyntaxHighlighter(syntaxHighlighter: SyntaxHighlighter) {
        codeEditorView.setSyntaxHighlighter(syntaxHighlighter)
    }

    private fun updateLineNumbers(lines: Int) {
        currentLineCount = lines

        val linesToDraw = if (lines < CodeEditorView.MIN_LINES) {
            CodeEditorView.MIN_LINES
        } else {
            lines
        }

        val sb = StringBuilder()
        for (i in 1..linesToDraw) {
            sb
                    .append("$i:\n")
        }
        lineNumberView
                .text = sb
                .toString()
    }

    private fun moveScreenWithCursorIfNecessary() {
        val pos = codeEditorView.editTextView.selectionStart
        val layout = codeEditorView.editTextView.layout

        if (layout != null) {
            val line = layout
                    .getLineForOffset(pos)
            val baseline = layout
                    .getLineBaseline(line)
            val ascent = layout
                    .getLineAscent(line)
            val x = layout
                    .getPrimaryHorizontal(pos)
            val y = (baseline + ascent)
                    .toFloat()

            val zoomLayoutRect = Rect()
            getLocalVisibleRect(zoomLayoutRect)

            val transformedX = x * realZoom + panX * realZoom + lineNumberView.width * realZoom
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

}
