package de.markusressel.kodeeditor.library.compose

import de.markusressel.kodehighlighter.core.LanguageRuleBook
import de.markusressel.kodehighlighter.core.language.Language
import de.markusressel.kodehighlighter.core.rule.LanguageRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class KodeEditorFoldProvidersTest {
    @Test
    fun everyLanguageOfKodeHighlighterHasAProvider() {
        val ids = listOf(
            "c", "cpp", "csharp", "dart", "diff", "go", "java", "json", "kotlin", "markdown", "ocaml", "properties",
            "python", "rust", "swift", "toml", "xml", "yaml",
        )
        assertEquals(ids.toSet(), KodeEditorFoldProviders.byLanguageId.keys)
        KodeEditorFoldProviders.byLanguageId.values.forEach { assertEquals(emptyList<FoldRegion>(), it.foldRegions("")) }
    }

    @Test
    fun idsIgnoreCase() {
        assertSame(KotlinFoldProvider, KodeEditorFoldProviders.forLanguageId("Kotlin"))
        assertNull(KodeEditorFoldProviders.forLanguageId("cobol"))
        assertNull(KodeEditorFoldProviders.forLanguageId(null))
    }

    @Test
    fun languagesOfRuleBooks() {
        val kotlin = object : LanguageRuleBook {
            override val language = Language(id = "kotlin", displayName = "Kotlin")
            override fun getRules() = emptyList<LanguageRule>()
        }
        val unknown = object : LanguageRuleBook {
            override fun getRules() = emptyList<LanguageRule>()
        }
        assertSame(KotlinFoldProvider, KodeEditorFoldProviders.forLanguage(kotlin.language))
        assertNull(KodeEditorFoldProviders.forLanguage(unknown.language))
    }
}
