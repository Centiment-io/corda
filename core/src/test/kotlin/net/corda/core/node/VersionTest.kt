package net.corda.core.node

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class VersionTest {
    @Test
    fun `parse valid non-SNAPSHOT string`() {
        assertThat(Version.parse("1.2")).isEqualTo(Version(1, 2, false))
    }

    @Test
    fun `parse valid SNAPSHOT string`() {
        assertThat(Version.parse("2.23-SNAPSHOT")).isEqualTo(Version(2, 23, true))
    }

    @Test
    fun `parse string with just major number`() {
        assertThatThrownBy {
            Version.parse("2")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `parse string with unknown qualifier`() {
        assertThatThrownBy {
            Version.parse("2.3-TEST")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `version 0 compatibility`() {
        assertThat(Version(0, 1, false).isCompatible(Version(0, 1, true))).isFalse()
        assertThat(Version(0, 2, false).isCompatible(Version(0, 1, false))).isFalse()
        assertThat(Version(0, 1, true).isCompatible(Version(0, 1, true))).isTrue()
    }

    @Test
    fun `version non-0 compatibility`() {
        assertThat(Version(1, 1, false).isCompatible(Version(1, 1, true))).isFalse()
        assertThat(Version(1, 2, false).isCompatible(Version(1, 1, false))).isTrue()
        assertThat(Version(1, 2, true).isCompatible(Version(1, 1, true))).isTrue()
        assertThat(Version(1, 1, false).isCompatible(Version(2, 1, false))).isFalse()
    }
}