package net.sergeych.tools

import net.sergeych.kotyara.db.ScriptBlock
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
        assertEquals(ScriptBlock(src, false), parseBlocks(src)[0])
    }

    @Test
    fun oneBlock() {
        val blocks = parseBlocks(
            """
                foo
                -- begin block --    
                bar
                baz
                -- end block --
                tail
            """.trimIndent()
        )
        assertEquals(ScriptBlock("foo",false), blocks[0])
        assertEquals(ScriptBlock("bar\nbaz", true), blocks[1])
        assertEquals(ScriptBlock("tail",false), blocks[2])
        assertEquals(3, blocks.size)

        var bb = parseBlocks(
            """
                -- begin block --    
                bar
                baz
                -- end block --
                tail
            """.trimIndent()
        ).map{it.value}
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
        ).map{it.value}
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
        ).map{it.value}
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
        ).map{it.value}
        assertEquals("foo", bb[0])
        assertEquals("bar\nbaz", bb[1])
        assertEquals("tail1", bb[2])
        assertEquals("foobar\n42", bb[3])
        assertEquals("tail2", bb[4])
        assertEquals(5, bb.size)
    }
}