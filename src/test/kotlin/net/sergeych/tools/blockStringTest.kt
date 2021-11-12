package net.sergeych.tools

import net.sergeych.kotyara.db.parseBlocks
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

internal class blockStringTest {

    @Test
    fun noBlocks() {
        val src = """
            foo
            
            bar
            baz
        """.trimIndent()
        assertEquals(src, parseBlocks(src)[0])
    }

    @Test
    fun oneBlock() {
        var bb = parseBlocks(
            """
                foo
                -- begin block --    
                bar
                baz
                -- end block --
                tail
            """.trimIndent()
        )
        assertEquals("foo", bb[0])
        assertEquals("bar\nbaz", bb[1])
        assertEquals("tail", bb[2])
        assertEquals(3, bb.size)

        bb = parseBlocks(
            """
                -- begin block --    
                bar
                baz
                -- end block --
                tail
            """.trimIndent()
        )
        println(bb)
        assertEquals("bar\nbaz", bb[0])
        assertEquals("tail", bb[1])
        assertEquals(2, bb.size)

        bb = parseBlocks(
            """
                first1
                first2
                -- begin block --    
                bar
                baz
                -- end block --
            """.trimIndent()
        )
        assertEquals("first1\nfirst2", bb[0])
        assertEquals("bar\nbaz", bb[1])
        assertEquals(2, bb.size)
    }
    @Test
    fun manyBlock1() {
        var bb = parseBlocks(
            """
                foo
                -- begin block --    
                bar
                baz
                -- end block --
                tail1
                
                -- begin block --
                foobar
                42
                -- end block --
            """.trimIndent()
        )
        assertEquals("foo", bb[0])
        assertEquals("bar\nbaz", bb[1])
        assertEquals("tail1", bb[2])
        assertEquals("foobar\n42", bb[3])
        assertEquals(4, bb.size)

        bb = parseBlocks(
            """
                foo
                -- begin block --    
                bar
                baz
                -- end block --
                tail1
                
                -- begin block --
                foobar
                42
                -- end block --
                
                tail2
            """.trimIndent()
        )
        assertEquals("foo", bb[0])
        assertEquals("bar\nbaz", bb[1])
        assertEquals("tail1", bb[2])
        assertEquals("foobar\n42", bb[3])
        assertEquals("tail2", bb[3])
        assertEquals(5, bb.size)

    }
}