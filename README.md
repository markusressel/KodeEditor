# KodeEditor
A simple code editor with syntax highlighting and pinch to zoom

 <img src="/screenshot/KodeEditor.png" width="300">

# Build Status

| Master | Dev |
|--------|-----|
| [![Master](https://travis-ci.org/markusressel/KodeEditor.svg?branch=master)](https://travis-ci.org/markusressel/KodeEditor/branches) | [![Master](https://travis-ci.org/markusressel/KutePreferences.svg?branch=dev)](https://travis-ci.org/markusressel/KodeEditor/branches) |
| [![codebeat badge](https://codebeat.co/badges/f7fa8602-1d15-457e-904d-cb585e984952)](https://codebeat.co/projects/github-com-markusressel-kodeeditor-master) | [![codebeat badge](https://codebeat.co/badges/19447977-bc96-4519-90b1-e532139ae1a5)](https://codebeat.co/projects/github-com-markusressel-kodeeditor-dev) |

# Features
* Pinch-To-Zoom
* Line numbers
* Syntax highlighting
  * import languages you need
  * or simply create your own highlighter using **regex** or other techniques
  * themes
* "Minimap" style document overview
* Written entirely in Kotlin

# How to use
Have a look at the demo app (`app`  module) for a complete sample.

## Gradle
To use this library just include it in your dependencies using

    repositories {
        ...
        maven { url "https://jitpack.io" }
    }

in your project build.gradle file and

```
dependencies {
    ...

    def codeEditorVersion = "v2.0.0"
    implementation("com.github.markusressel.KodeEditor:library:${codeEditorVersion}")
}
```

in your desired module ```build.gradle``` file.

## Add to your layout

To use this editor simply add something similar to this to your desired layout xml file:

```
<de.markusressel.kodeeditor.library.view.CodeEditorLayout
        android:id="@+id/codeEditorView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:ke_divider_color="?android:attr/textColorPrimary"
        app:ke_divider_enabled="true"
        app:ke_editor_backgroundColor="?android:attr/windowBackground"
        app:ke_editor_maxZoom="10.0"
        app:ke_lineNumbers_backgroundColor="#ccc"
        app:ke_lineNumbers_textColor="#000"
        app:ke_minimap_enabled="true"
        app:ke_minimap_maxDimension="200dp" 
        />
```

## Syntax highlighting

### Language Autodetection

Currently there is no auto detection for the language used in a document.
You have to manage the syntax highlighter yourself and call the `setSyntaxHighlighter` method when appropriate.

### Integrated syntax highlighters

Have a look at the [KodeHighlighter section about this](https://github.com/markusressel/KodeHighlighter).

### Writing a custom syntax highlighter

Have a look at the [KodeHighlighter section about this](https://github.com/markusressel/KodeHighlighter).

## Styling

KodeEditor can be styled in multiple ways:

1. xml attributes on KodeEditor
1. theme attributes in your custom theme
1. methods on the view object itself

### Theme Attributes

| Name                      | Description                              | Type     | Default                                |
|---------------------------|------------------------------------------|----------|----------------------------------------|
| ke_lineNumbers_textColor | Specifies the text color of line numbers | Color    | `android.R.attr.textColorPrimary`      |
| ke_lineNumbers_backgroundColor | Specifies the background color of the line numbers view | Color | `android.R.attr.windowBackground` |
| ke_divider_enabled | Specifies if a divider should be drawn between line numbers and the actual code editor content | Boolean | `true` |
| ke_divider_color | Specifies the color of the divider (has no effect if `ke_divider_enabled` is set to `false`) | Color | `android.R.attr.textColorPrimary` |
| ke_editor_backgroundColor | Specifies the background color of the code editor view | Color | `android.R.attr.windowBackground` |
| ke_editor_maxZoom | Specifies the maximum zoom level of the editor | Float | `10` |
| ke_minimap_enabled | Enables the minimap | Boolean | `true` |
| ke_minimap_maxDimension | Specifies the maximum dimension of the minimap for both axis | Dimension | `150dp` |

You can either use those attributes directly on the view in your layout like this:

```
<de.markusressel.kodeeditor.library.view.CodeEditorView
    android:id="@+id/codeEditorView"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    [...]
    app:ke_divider_color="?android:attr/textColorPrimary"
    app:ke_divider_enabled="true"
    app:ke_editor_backgroundColor="?android:attr/windowBackground"
    app:ke_editor_maxZoom="10.0"
    app:ke_lineNumbers_backgroundColor="#ccc"
    app:ke_lineNumbers_textColor="#000"
    app:ke_minimap_enabled="true"
    app:ke_minimap_maxDimension="200dp" 
    app:ke_divider="true"
    [...] />
```

or specify them in your application theme (`styles.xml` in dem app) for to apply a style globally:

```
<resources>

    <!-- Base application theme. -->
    <style name="AppTheme" parent="Theme.AppCompat.Light.DarkActionBar">
        <!-- Other theme attributes of your application -->
        <item name="colorPrimary">@color/colorPrimary</item>
        <item name="colorPrimaryDark">@color/colorPrimaryDark</item>
        <item name="colorAccent">@color/colorAccent</item>

        <!-- CodeEditorView -->
        <item name="ke_lineNumbers_backgroundColor">#ccc</item>
        <item name="ke_lineNumbers_textColor">#000</item>
        <item name="ke_divider_enabled">false</item>
        <item name="ke_divider_color">#000</item>
        <item name="ke_editor_backgroundColor">#fff</item>
        <item name="ke_editor_maxZoom">10.0</item>
        <item name="ke_minimap_enabled">true</item>
        <item name="ke_minimap_maxDimension">200dp</item>
         
    </style>

</resources>
```

# Contributing

GitHub is for social coding: if you want to write code, I encourage contributions through pull requests from forks
of this repository. Create GitHub tickets for bugs and new features and comment on the ones that you are interested in.

# License

```
MIT License

Copyright (c) 2018 Markus Ressel

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```