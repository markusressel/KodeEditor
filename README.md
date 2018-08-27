# KodeEditor
A simple code editor with syntax highlighting and pinch to zoom

 <img src="/screenshot/KodeEditor.png" width="300">

# Build Status

| Master | Dev |
|--------|-----|
| [![Master](https://travis-ci.org/markusressel/KodeEditor.svg?branch=master)](https://travis-ci.org/markusressel/KutePreferences/branches) | [![Master](https://travis-ci.org/markusressel/KutePreferences.svg?branch=dev)](https://travis-ci.org/markusressel/KodeEditor/branches) |
| [![codebeat badge](https://codebeat.co/badges/f7fa8602-1d15-457e-904d-cb585e984952)](https://codebeat.co/projects/github-com-markusressel-kodeeditor-master) | [![codebeat badge](https://codebeat.co/badges/19447977-bc96-4519-90b1-e532139ae1a5)](https://codebeat.co/projects/github-com-markusressel-kodeeditor-dev) |

# Features
* Pinch-To-Zoom
* Line numbers
* Syntax highlighting
  * import languages you need
  * or simply create your own highlighter using **regex** or other techniques
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

    dependencies {
        implementation("com.github.markusressel.KodeEditor:library:+") {
            transitive = true
        }
    }

in your desired module ```build.gradle``` file.

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