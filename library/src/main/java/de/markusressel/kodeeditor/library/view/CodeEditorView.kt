package de.markusressel.kodeeditor.library.view

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.support.annotation.StringRes
import android.support.v4.view.ViewCompat
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.jakewharton.rxbinding2.widget.RxTextView
import com.otaliastudios.zoom.ZoomLayout
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import de.markusressel.kodeeditor.library.R
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt


class CodeEditorView : ZoomLayout {

    private lateinit var contentLayout: LinearLayout
    lateinit var lineNumberView: TextView
    lateinit var editTextView: CodeEditText

    var moveWithCursorEnabled = false

    private var currentLineCount = -1

    constructor(context: Context) : super(context) {
        initialize(null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initialize(attrs)
    }

    private fun initialize(attrs: AttributeSet?) {
        readParameters(attrs)

        inflateViews(LayoutInflater.from(context))
        addView(contentLayout)

        setListeners()

        editTextView
                .setViewBackgroundWithoutResettingPadding(null)

        editTextView
                .post {
                    editTextView
                            .setSelection(0)
                }
    }

    private fun View.setViewBackgroundWithoutResettingPadding(background: Drawable?) {
        val paddingBottom = this
                .paddingBottom
        val paddingStart = ViewCompat
                .getPaddingStart(this)
        val paddingEnd = ViewCompat
                .getPaddingEnd(this)
        val paddingTop = this
                .paddingTop
        ViewCompat
                .setBackground(this, background)
        ViewCompat
                .setPaddingRelative(this, paddingStart, paddingTop, paddingEnd, paddingBottom)
    }


    private fun readParameters(attrs: AttributeSet?) {

    }

    private fun inflateViews(inflater: LayoutInflater) {
        contentLayout = inflater.inflate(R.layout.view_code_editor__inner_layout, null) as LinearLayout
        lineNumberView = contentLayout.findViewById(R.id.codeLinesView) as TextView
        editTextView = contentLayout.findViewById(R.id.codeEditText) as CodeEditText
    }


    private var initialSizeNotSet = true

    private fun setListeners() {
        addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->

            if (initialSizeNotSet) {
                val displayMetrics = context
                        .resources
                        .displayMetrics

                contentLayout
                        .minimumHeight = displayMetrics
                        .heightPixels
                contentLayout
                        .minimumWidth = displayMetrics.widthPixels + 80

                initialSizeNotSet = false
            }
        }

        setOnTouchListener { view, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_MOVE -> {
                    moveWithCursorEnabled = false
                }
            }
            false
        }

        editTextView
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
                                .e(TAG, "Error moving screen with cursor", e)
                    }
                }, onError = {
                    Log
                            .e(TAG, "Unrecoverable error while moving screen with cursor", it)
                })

        RxTextView
                .textChanges(editTextView)
                .debounce(50, TimeUnit.MILLISECONDS)
                .filter {
                    moveWithCursorEnabled = true
                    editTextView.lineCount != currentLineCount
                }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .bindToLifecycle(this)
                .subscribeBy(onNext = {
                    try {
                        updateLineNumbers(editTextView.lineCount)
                    } catch (e: Throwable) {
                        Log
                                .e(TAG, "Error updating line numbers", e)
                    }
                }, onError = {
                    Log
                            .e(TAG, "Unrecoverable error while updating line numbers", it)
                })
    }

    private fun moveScreenWithCursorIfNecessary() {
        val pos = editTextView
                .selectionStart
        val layout = editTextView
                .layout

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

    private fun updateLineNumbers(lines: Int) {
        currentLineCount = lines

        val linesToDraw = if (lines < MIN_LINES) {
            MIN_LINES
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

    /**
     * @param editable true = user can type, false otherwise
     */
    fun setEditable(editable: Boolean) {
        editTextView
                .isEnabled = editable
    }

    /**
     * Set the text in the editor
     */
    fun setText(text: CharSequence) {
        editTextView
                .setText(text, TextView.BufferType.EDITABLE)
        editTextView
                .refreshSyntaxHighlighting()
    }

    /**
     * Set the text in the editor
     */
    @Suppress("unused")
    fun setText(@StringRes text: Int) {
        editTextView
                .setText(text, TextView.BufferType.EDITABLE)
        editTextView
                .refreshSyntaxHighlighting()
    }

    /**
     * Set the syntax highlighter to use for this CodeEditor
     */
    @Suppress("unused")
    fun setSyntaxHighlighter(syntaxHighlighter: SyntaxHighlighter) {
        editTextView
                .syntaxHighlighter = syntaxHighlighter
    }

    companion object {
        const val TAG = "CodeEditorView"
        const val MIN_LINES = 1
    }

}
