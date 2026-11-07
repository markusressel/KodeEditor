package de.markusressel.kodeeditor.library.compose

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
        "dart" to DartFoldProvider,
        "diff" to DiffFoldProvider,
        "go" to GoFoldProvider,
        "java" to JavaFoldProvider,
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
