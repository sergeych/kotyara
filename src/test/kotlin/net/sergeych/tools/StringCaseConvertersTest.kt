package net.sergeych.tools

import net.sergeych.kotyara.db.toFieldName
import net.sergeych.kotyara.db.toTableName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class StringCaseConvertersTest {

    @Test
    fun snakeToCamelCase() {
        assertEquals("camelCaseString", "camel_case_string".snakeToLowerCamelCase())
        assertEquals("XCode", "x_code".snakeToUpperCamelCase())
    }

    @Test
    fun toTableNameTest() {
        fun t1(res: String, src: String) {
            assertEquals(res, src.toTableName())
        }
        fun t2(res: String, src: String) {
            assertEquals(res, src.toFieldName())
        }
        t1("recent_orders", "RecentOrder")
        t1("recent_new_orders", "RecentNewOrder")
        t1("orders", "Order")
        t2("recent_order", "RecentOrder")
        t2("recent_new_order", "RecentNewOrder")
        t2("order", "Order")
    }
}