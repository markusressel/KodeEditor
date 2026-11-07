package de.markusressel.kodeeditor.library.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BraceFoldProviderTest {

    private fun KodeEditorFoldProvider.regions(vararg lines: String) =
        foldRegions(lines.joinToString("\n")).sortedBy { it.startLine }.map { it.startLine to it.endLine }

    private val none = emptyList<Pair<Int, Int>>()

    @Test
    fun aBlockHidesItsClosingLine() {
        assertEquals(listOf(0 to 2), KotlinFoldProvider.regions("fun x() {", "    a()", "}"))
    }

    @Test
    fun aBlockWithoutContentHidesItsClosingLine() {
        assertEquals(listOf(0 to 1), KotlinFoldProvider.regions("fun x() {", "}"))
    }

    @Test
    fun aBlockInOneLineHasNoRegion() {
        assertEquals(none, KotlinFoldProvider.regions("fun x() { a() }", "b()"))
    }

    @Test
    fun codeAfterTheClosingBracketKeepsItsLineVisible() {
        assertEquals(
            listOf(0 to 1, 2 to 4),
            JavaFoldProvider.regions("if (a) {", "    b();", "} else {", "    c();", "}"),
        )
    }

    @Test
    fun anElseRightAfterTheOpeningLineHidesNothing() {
        assertEquals(listOf(1 to 3), JavaFoldProvider.regions("if (a) {", "} else {", "    c();", "}"))
    }

    @Test
    fun closingBracketsSemicolonsAndCommasMayFollow() {
        assertEquals(
            listOf(0 to 2, 3 to 5, 6 to 8),
            KotlinFoldProvider.regions("a({", "    b", "})", "c {", "    d", "};", "e {", "    f", "}, // g"),
        )
    }

    @Test
    fun aCallAfterTheClosingBracketKeepsItsLineVisible() {
        assertEquals(listOf(0 to 1), KotlinFoldProvider.regions("list.map {", "    it", "}.filter { it }"))
    }

    @Test
    fun regionsInTheSameLineAreJoined() {
        assertEquals(listOf(0 to 3), KotlinFoldProvider.regions("a(b, {", "    c", "}, d,", "e)"))
    }

    @Test
    fun bracketsInStringsAndCommentsAreIgnored() {
        assertEquals(
            listOf(0 to 5),
            JavaFoldProvider.regions(
                "void a() {",
                "    String b = \"{ \\\" (\";",
                "    char c = '{';",
                "    // }",
                "    /* } */ char d = '\\'';",
                "}",
            ),
        )
    }

    @Test
    fun aStringEndsWithItsLine() {
        assertEquals(listOf(1 to 3), JavaFoldProvider.regions("String a = \"{", "void b() {", "    c();", "}"))
    }

    @Test
    fun aBlockCommentFolds() {
        assertEquals(
            listOf(0 to 3, 4 to 6),
            JavaFoldProvider.regions("/**", " * Docs { ", " * @param a b", " */", "void a() {", "    b();", "}"),
        )
    }

    @Test
    fun codeAfterABlockCommentKeepsItsLineVisible() {
        assertEquals(listOf(0 to 1), CFoldProvider.regions("/* a", "   b", "*/ int c;"))
    }

    @Test
    fun commentsNestOnlyWhereTheLanguageSaysSo() {
        val text = arrayOf("/* a", "/* b */", "{", "*/", "x", "}")
        assertEquals(listOf(0 to 3), KotlinFoldProvider.regions(*text))
        // in Java the comment ends in the second line, so the brackets match
        assertEquals(listOf(0 to 1, 2 to 5), JavaFoldProvider.regions(*text))
    }

    @Test
    fun anUnclosedCommentFoldsNothing() {
        assertEquals(listOf(0 to 1), JavaFoldProvider.regions("void a() {", "}", "", "/* b", "{", "}"))
    }

    @Test
    fun aClosingBracketWithoutItsOpeningOneIsIgnored() {
        assertEquals(listOf(1 to 3), KotlinFoldProvider.regions("}", "fun a() {", "    b())", "}", ")"))
    }

    @Test
    fun anUnclosedBracketFoldsWhatItContains() {
        assertEquals(
            listOf(1 to 3),
            KotlinFoldProvider.regions("class A {", "    fun b() {", "        c()", "    }", ""),
        )
    }

    @Test
    fun aMismatchedBracketClosesWhatIsOpenAboveIt() {
        // the parenthesis is never closed, the bracket of the block still is
        assertEquals(listOf(0 to 3), KotlinFoldProvider.regions("fun a() {", "    b(", "    c", "}"))
    }

    @Test
    fun anEmptyTextHasNoRegions() {
        listOf(
            JavaFoldProvider, KotlinFoldProvider, JsonFoldProvider, CFoldProvider, CppFoldProvider,
            CSharpFoldProvider, GoFoldProvider, RustFoldProvider, SwiftFoldProvider, DartFoldProvider,
        ).forEach { assertEquals(none, it.regions("")) }
    }

    @Test
    fun brokenTextDoesNotCrash() {
        val providers = listOf(
            JavaFoldProvider, KotlinFoldProvider, JsonFoldProvider, CFoldProvider, CppFoldProvider,
            CSharpFoldProvider, GoFoldProvider, RustFoldProvider, SwiftFoldProvider, DartFoldProvider,
        )
        val pieces = listOf(
            "\"", "'", "`", "\\", "/*", "*/", "//", "{", "}", "(", ")", "[", "]", "\n", "r#\"", "\"#", "R\"x(",
            ")x\"", "@\"", "\"\"\"", "'''", "#if", "#endif", "#\"", "import a", "'a", "\uD83D\uDE00",
        )
        val random = java.util.Random(1)
        repeat(300) {
            val text = buildString { repeat(random.nextInt(40)) { append(pieces[random.nextInt(pieces.size)]) } }
            providers.forEach { provider ->
                val lines = text.count { it == '\n' }
                provider.foldRegions(text).forEach {
                    assertTrue("$it in $text", it.startLine < it.endLine && it.endLine <= lines)
                }
            }
        }
        // ends of text in the middle of everything
        providers.forEach { provider -> pieces.forEach { provider.foldRegions(it) } }
    }

    @Test
    fun runsOfImportsFold() {
        assertEquals(
            listOf(2 to 6, 8 to 10),
            KotlinFoldProvider.regions(
                "package a",
                "",
                "import a.B",
                "import a.C",
                "",
                "// comment",
                "import d.E",
                "",
                "class F {",
                "    val g = 1",
                "}",
            ),
        )
    }

    @Test
    fun aSingleImportDoesNotFold() {
        assertEquals(none, JavaFoldProvider.regions("import a.B;", "", "class C {}"))
    }

    @Test
    fun aCommentBetweenImportsEndsTheRun() {
        assertEquals(listOf(1 to 3), JavaFoldProvider.regions("import a.B;", "/*", "import a.C;", "*/"))
    }

    @Test
    fun java() {
        assertEquals(
            listOf(0 to 1, 3 to 6, 8 to 22, 9 to 11, 15 to 21, 16 to 17, 18 to 20),
            JavaFoldProvider.regions(
                "import java.util.List;",
                "import java.util.Map;",
                "",
                "/**",
                " * A class. {@link List}",
                " * @author me",
                " */",
                "@Annotation",
                "public class A {",
                "    private final int[] b = {",
                "        1, 2,",
                "    };",
                "    String c = \"\"\"",
                "        { text block",
                "        \"\"\";",
                "    void d() {",
                "        try {",
                "            e();",
                "        } catch (Exception f) {",
                "            g('}');",
                "        }",
                "    }",
                "}",
            ),
        )
    }

    @Test
    fun javaTextBlocksAreStrings() {
        assertEquals(
            listOf(0 to 4, 1 to 3),
            JavaFoldProvider.regions("void a() {", "    b(\"\"\"", "        } \\\"\"\" {", "        \"\"\");", "}"),
        )
    }

    @Test
    fun kotlin() {
        assertEquals(
            listOf(0 to 1, 3 to 6, 7 to 9, 10 to 19, 11 to 18, 12 to 14, 15 to 17),
            KotlinFoldProvider.regions(
                "import a.B",
                "import a.C",
                "",
                "/**",
                " * Docs /* nested */",
                " * more",
                " */",
                "class D(",
                "    val e: Int,",
                "    val f: String = \"\"\"{\"\"\",",
                ") {",
                "    fun g() = when (e) {",
                "        1 -> {",
                "            '}'",
                "        }",
                "        else -> listOf(",
                "            \"\$f}\",",
                "        )",
                "    }",
                "}",
            ),
        )
    }

    @Test
    fun kotlinRawStringsMayEndWithMoreQuotes() {
        assertEquals(
            listOf(0 to 3),
            KotlinFoldProvider.regions("fun a() {", "    val b = \"\"\"", "    }\"\"\"\"", "}"),
        )
    }

    @Test
    fun kotlinNamesInBackticksAreNoCode() {
        assertEquals(listOf(0 to 2), KotlinFoldProvider.regions("fun `a {`() {", "    b()", "}"))
    }

    @Test
    fun json() {
        assertEquals(
            listOf(0 to 8, 1 to 4, 5 to 7),
            JsonFoldProvider.regions(
                "{",
                "  \"a\": [",
                "    1,",
                "    \"]\"",
                "  ],",
                "  \"b\": {",
                "    \"c\": \"it's {\"",
                "  }",
                "}",
            ),
        )
    }

    @Test
    fun jsonDoesNotFoldParentheses() {
        assertEquals(none, JsonFoldProvider.regions("(", ")"))
    }

    @Test
    fun c() {
        assertEquals(
            listOf(0 to 2, 4 to 6, 7 to 12, 8 to 9),
            CFoldProvider.regions(
                "#include <stdio.h>",
                "#include <stdlib.h>",
                "#include \"a.h\"",
                "",
                "#ifndef A",
                "#define A '{'",
                "#endif",
                "int main(void) {",
                "    if (a) {",
                "        puts(\"}\");",
                "    } else",
                "        b();",
                "}",
            ),
        )
    }

    @Test
    fun preprocessorDirectivesNest() {
        assertEquals(
            listOf(0 to 4, 1 to 3),
            CFoldProvider.regions("#if A", "#  ifdef B", "x", "#  endif", "#endif", "#endif"),
        )
    }

    @Test
    fun cpp() {
        assertEquals(
            listOf(0 to 1, 3 to 9),
            CppFoldProvider.regions(
                "#include <string>",
                "using namespace std;",
                "",
                "namespace a {",
                "    auto b = R\"x(",
                "        )\" } ",
                "    )x\";",
                "    int c = 1'000'000;",
                "    char d = '}';",
                "}",
            ),
        )
    }

    @Test
    fun cppRawStringsWithPrefixes() {
        assertEquals(listOf(0 to 3), CppFoldProvider.regions("void a() {", "    auto b = u8R\"(", "    })\";", "}"))
    }

    @Test
    fun cSharp() {
        assertEquals(
            listOf(0 to 1, 4 to 17, 5 to 6, 8 to 16, 10 to 15),
            CSharpFoldProvider.regions(
                "using System;",
                "using System.IO;",
                "",
                "namespace A",
                "{",
                "    #region Fields",
                "    #endregion",
                "    class B",
                "    {",
                "        void C()",
                "        {",
                "            var d = @\"C:\\{\"\"\";",
                "            var e = \"\"\"\"",
                "                \"\"\"{\"\"\"",
                "                \"\"\"\";",
                "        }",
                "    }",
                "}",
            ),
        )
    }

    @Test
    fun go() {
        assertEquals(
            listOf(0 to 1, 2 to 5, 7 to 14, 11 to 13),
            GoFoldProvider.regions(
                "import \"a\"",
                "import \"b\"",
                "import (",
                "\t\"fmt\"",
                "\t\"os\"",
                ")",
                "",
                "func main() {",
                "\ts := `{",
                "\t`",
                "\tr := '}'",
                "\tif s != \"\" {",
                "\t\tfmt.Println(s, r)",
                "\t}",
                "}",
            ),
        )
    }

    @Test
    fun rust() {
        assertEquals(
            listOf(0 to 1, 3 to 6, 7 to 17, 12 to 13, 14 to 16),
            RustFoldProvider.regions(
                "use std::fmt;",
                "use std::io;",
                "",
                "/* outer",
                "  /* nested } */",
                "  still a comment {",
                "*/",
                "fn longest<'a>(x: &'a str, y: &'a str) -> &'a str {",
                "    let s = r#\"",
                "        \"} still a string",
                "    \"#;",
                "    let c = '{';",
                "    if x.len() > y.len() {",
                "        x",
                "    } else {",
                "        y",
                "    }",
                "}",
            ),
        )
    }

    @Test
    fun anImportThatOpensABracketEndsTheRun() {
        assertEquals(
            listOf(0 to 1, 2 to 5),
            RustFoldProvider.regions("use a;", "use b;", "use c::{", "    d,", "    e,", "};", "use f;"),
        )
    }

    @Test
    fun rustStringsCanGoOverLines() {
        assertEquals(listOf(0 to 3), RustFoldProvider.regions("fn a() {", "    let b = \"{", "    \\\"\";", "}"))
    }

    @Test
    fun swift() {
        assertEquals(
            listOf(0 to 1, 3 to 5, 6 to 20, 7 to 11, 12 to 19, 13 to 18),
            SwiftFoldProvider.regions(
                "import Foundation",
                "import UIKit",
                "",
                "#if DEBUG",
                "let a = #\"\\{\"#",
                "#endif",
                "struct B {",
                "    let c = [",
                "        \"}\",",
                "        \"\"\"",
                "        ]\"\"\"",
                "    ]",
                "    func d() {",
                "        if true {",
                "            /* /* } */ */",
                "            print(\"\\(c)\")",
                "            let e = #\"\"\"",
                "            \"\"\" }\"\"\"#",
                "        }",
                "    }",
                "}",
            ),
        )
    }

    @Test
    fun dart() {
        assertEquals(
            listOf(0 to 1, 3 to 15, 4 to 14, 5 to 13, 6 to 12, 9 to 11),
            DartFoldProvider.regions(
                "import 'package:flutter/material.dart';",
                "import 'a.dart';",
                "",
                "class A extends StatelessWidget {",
                "  Widget build(BuildContext context) {",
                "    return Column(",
                "      children: [",
                "        Text('{'),",
                "        Text(r\"\\\"),",
                "        Text('''",
                "          ] \${'}'}",
                "        '''),",
                "      ],",
                "    );",
                "  }",
                "}",
            ),
        )
    }

    @Test
    fun aCustomLanguage() {
        val provider = BraceFoldProvider(
            lineComments = listOf("#"),
            blockCommentStart = null,
            strings = listOf(BraceStringSyntax.Quoted("'", escape = null)),
            charLiterals = false,
            foldedBrackets = "{}",
            importPrefixes = listOf("require "),
        )
        assertEquals(
            listOf(0 to 1, 2 to 6),
            provider.regions("require 'a'", "require 'b'", "a {", "  # }", "  b('}', [", "  ])", "}"),
        )
    }

    @Test
    fun javaScript() {
        assertEquals(
            listOf(0 to 1, 4 to 7),
            JavaScriptFoldProvider.regions(
                "import a from \"a\";",
                "import { b } from 'b';",
                "const t = `x {",
                "y`;",
                "function f() {",
                "    const s = '}';",
                "    return \"{\";",
                "}",
            ),
        )
    }

    @Test
    fun css() {
        assertEquals(
            listOf(0 to 1, 3 to 5, 6 to 8),
            CssFoldProvider.regions(
                "@import url(\"a.css\");",
                "@import \"b.css\";",
                "/* a { */",
                "a {",
                "    content: \"}\";",
                "}",
                "@media (x) {",
                "    b { c: d; }",
                "}",
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownBracketsAreRejected() {
        BraceFoldProvider(foldedBrackets = "<>")
    }
}
