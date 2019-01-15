package de.markusressel.kodeeditor.library.extensions

import android.content.Context
import android.content.res.TypedArray
import android.support.annotation.AttrRes
import android.support.annotation.ColorInt
import android.support.annotation.StyleableRes
import android.support.v4.content.ContextCompat
import android.util.TypedValue

/**
 * Get a color from this TypedArray or use the first default that is found
 *
 * @param context view context
 * @param styleableRes styleable resource
 * @param attr theme attribute resource
 */
@ColorInt
fun TypedArray.getColor(context: Context, @StyleableRes styleableRes: Int, @AttrRes vararg attr: Int): Int {
    return getColor(styleableRes, attr.find { context.getThemeAttrColor(it) != null }
            ?: context.getThemeAttrColor(attr.last())!!)
}

/**
 * Get Color from Theme attribute
 *
 * @param context Activity context
 * @param attr    Attribute resource ID
 *
 * @return Color as Int
 */
@ColorInt
fun Context.getThemeAttrColor(@AttrRes attr: Int): Int? {
    val typedValue = TypedValue()
    if (theme.resolveAttribute(attr, typedValue, true)) {
        if (typedValue.type >= TypedValue.TYPE_FIRST_INT && typedValue.type <= TypedValue.TYPE_LAST_INT) {
            return typedValue.data
        } else if (typedValue.type == TypedValue.TYPE_STRING) {
            return ContextCompat.getColor(this, typedValue.resourceId)
        }
    }

    return null
}