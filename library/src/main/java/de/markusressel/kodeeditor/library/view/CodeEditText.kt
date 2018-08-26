package de.markusressel.kodeeditor.library.view

import android.content.Context
import android.support.v7.widget.AppCompatEditText
import android.util.AttributeSet
import android.util.Log
import com.jakewharton.rxbinding2.widget.RxTextView
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import de.markusressel.kodeeditor.library.syntaxhighlighter.SyntaxHighlighter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

/**
 * EditText modified for longer texts and support for syntax highlighting
 */
class CodeEditText : AppCompatEditText {

    /**
     * The syntax highlighter that currently in use
     */
    var syntaxHighlighter: SyntaxHighlighter? = null
        set(value) {
            field = value
            initSyntaxHighlighter()
        }


    private var highlightingTimeout = 50L to TimeUnit.MILLISECONDS
    private var highlightingDisposable: Disposable? = null

    constructor(context: Context) : super(context) {
        reinit()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        reinit()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        reinit()
    }

    private fun reinit() {
        initSyntaxHighlighter()
    }

    private fun initSyntaxHighlighter() {
        highlightingDisposable
                ?.dispose()

        if (syntaxHighlighter != null) {
            highlightingDisposable = RxTextView
                    .afterTextChangeEvents(this)
                    .debounce(highlightingTimeout.first, highlightingTimeout.second)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .bindToLifecycle(this)
                    .subscribeBy(onNext = {
                        // syntax highlighting
                        refreshSyntaxHighlighting()
                    }, onError = {
                        Log
                                .e(TAG, "Error while refreshing syntax highlighting", it)
                    })
        }
    }

    /**
     * Set the timeout before new text is highlighted after the user has stopped typing
     *
     * @param timeout arbitrary value
     * @param timeUnit the timeunit to use
     */
    @Suppress("unused")
    fun setHighlightingTimeout(timeout: Long, timeUnit: TimeUnit) {
        highlightingTimeout = timeout to timeUnit
        reinit()
    }

    /**
     * Get the current timeout in milliseconds
     */
    @Suppress("unused")
    fun getHighlightingTimeout(): Long {
        return highlightingTimeout
                .second
                .toMillis(highlightingTimeout.first)
    }

    @Synchronized
    fun refreshSyntaxHighlighting() {
        if (syntaxHighlighter == null) {
            Log
                    .w(TAG, "No syntax highlighter is set!")
        }

        syntaxHighlighter
                ?.highlight(text)
    }

    companion object {
        const val TAG = "CodeEditText"
    }

}