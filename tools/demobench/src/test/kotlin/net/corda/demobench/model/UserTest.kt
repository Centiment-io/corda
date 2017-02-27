package net.corda.demobench.model

import org.junit.Test
import kotlin.test.assertEquals

class UserTest {

    @Test
    fun createFromEmptyMap() {
        val user = toUser(emptyMap())
        assertEquals("none", user.user)
        assertEquals("none", user.password)
        assertEquals(emptyList<String>(), user.permissions)
    }

    @Test
    fun createFromMap() {
        val map = mapOf(
            "user" to "MyName",
            "password" to "MyPassword",
            "permissions" to listOf("Flow.MyFlow")
        )
        val user = toUser(map)
        assertEquals("MyName", user.user)
        assertEquals("MyPassword", user.password)
        assertEquals(listOf("Flow.MyFlow"), user.permissions)
    }

    @Test
    fun userToMap() {
        val user = User("MyName", "MyPassword", listOf("Flow.MyFlow"))
        val map = user.toMap()
        assertEquals("MyName", map["user"])
        assertEquals("MyPassword", map["password"])
        assertEquals(listOf("Flow.MyFlow"), map["permissions"])
    }

    @Test
    fun `adding extra permissions`() {
        val user = User("MyName", "MyPassword", listOf("Flow.MyFlow"))
        user.extendPermissions(listOf("Flow.MyFlow", "Flow.MyNewFlow"))
        assertEquals(listOf("Flow.MyFlow", "Flow.MyNewFlow"), user.permissions)
    }

    @Test
    fun `default user`() {
        val user = user("guest")
        assertEquals("guest", user.user)
        assertEquals("letmein", user.password)
        assertEquals(listOf("StartFlow.net.corda.flows.CashFlow"), user.permissions)
    }

}