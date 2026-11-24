package de.markusressel.kodeeditor.library.compose.fold

import de.markusressel.kodeeditor.library.compose.fold.providers.CFoldProvider
import de.markusressel.kodeeditor.library.compose.fold.providers.CSharpFoldProvider
import de.markusressel.kodeeditor.library.compose.fold.providers.CppFoldProvider
import de.markusressel.kodeeditor.library.compose.fold.providers.CssFoldProvider
import de.markusressel.kodeeditor.library.compose.fold.providers.DartFoldProvider
import de.markusressel.kodeeditor.library.compose.fold.providers.DiffFoldProvider
import de.markusressel.kodeeditor.library.compose.fold.providers.GoFoldProvider
import de.markusressel.kodeeditor.library.compose.fold.providers.JavaFoldProvider
import de.markusressel.kodeeditor.library.compose.fold.providers.JavaScriptFoldProvider
import de.markusressel.kodeeditor.library.compose.fold.providers.JsonFoldProvider
import de.markusressel.kodeeditor.library.compose.fold.providers.KotlinFoldProvider
import de.markusressel.kodeeditor.library.compose.fold.providers.MarkdownFoldProvider
import de.markusressel.kodeeditor.library.compose.fold.providers.OCamlFoldProvider
import de.markusressel.kodeeditor.library.compose.fold.providers.PropertiesFoldProvider
import de.markusressel.kodeeditor.library.compose.fold.providers.PythonFoldProvider
import de.markusressel.kodeeditor.library.compose.fold.providers.RustFoldProvider
import de.markusressel.kodeeditor.library.compose.fold.providers.SwiftFoldProvider
import de.markusressel.kodeeditor.library.compose.fold.providers.TomlFoldProvider
import de.markusressel.kodeeditor.library.compose.fold.providers.XmlFoldProvider
import de.markusressel.kodeeditor.library.compose.fold.providers.YamlFoldProvider
import de.markusressel.kodehighlighter.core.language.Language

/**
 * The built-in [KodeEditorFoldProvider]s by language, with the ids of the languages of KodeHighlighter, which are those
 * of VS Code.
 */
object KodeEditorFoldProviders {
    /** the providers by the id of their language */
    val byLanguageId: Map<String, KodeEditorFoldProvider> = mapOf(
        "c" to CFoldProvider,
        "cpp" to CppFoldProvider,
        "csharp" to CSharpFoldProvider,
        "css" to CssFoldProvider,
        "dart" to DartFoldProvider,
        "diff" to DiffFoldProvider,
        "go" to GoFoldProvider,
        // XmlFoldProvider understands HTML: void elements and the text of script and style
        "html" to XmlFoldProvider,
        "java" to JavaFoldProvider,
        "javascript" to JavaScriptFoldProvider,
        "json" to JsonFoldProvider,
        "kotlin" to KotlinFoldProvider,
        "markdown" to MarkdownFoldProvider,
        "ocaml" to OCamlFoldProvider,
        "properties" to PropertiesFoldProvider,
        "python" to PythonFoldProvider,
        "rust" to RustFoldProvider,
        "swift" to SwiftFoldProvider,
        "toml" to TomlFoldProvider,
        "xml" to XmlFoldProvider,
        "yaml" to YamlFoldProvider,
    )

    /**
     * @param languageId the id of a language, like `kotlin`
     * @return the built-in provider of that language, null if there is none
     */
    fun forLanguageId(languageId: String?): KodeEditorFoldProvider? = languageId?.let { byLanguageId[it.lowercase()] }

    /**
     * @param language a language of KodeHighlighter, like `KotlinLanguage`, or one of your own with the same id
     * @return the built-in provider of that language, null if there is none
     */
    fun forLanguage(language: Language?): KodeEditorFoldProvider? = forLanguageId(language?.id)
}
