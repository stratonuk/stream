package com.strata.tv.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChannelCategorizerTest {

    @Test
    fun `strips EN language prefix without treating as UK alone`() {
        assertThat(ChannelCategorizer.normalizeGroupTitle("EN | Football"))
            .isEqualTo("Football")
        // EN alone is language — UK requires extra evidence
        assertThat(ChannelCategorizer.isLikelyUk("Some Channel", "EN | Entertainment"))
            .isFalse()
    }

    @Test
    fun `bbc one maps to UK and BBC`() {
        val cats = ChannelCategorizer.categoriesFor("BBC One HD", "EN | UK Entertainment")
        assertThat(cats).containsAtLeast("BBC", "UK")
    }

    @Test
    fun `sky sports maps to UK Sky Football Sports`() {
        val cats = ChannelCategorizer.categoriesFor("Sky Sports Main Event", "UK | Sky Sports")
        assertThat(cats).containsAtLeast("Sky", "Sports", "Football", "UK")
    }

    @Test
    fun `adult categories are excluded`() {
        val cats = ChannelCategorizer.categoriesFor("Adult Channel", "XXX | Adult")
        assertThat(cats).isEmpty()
        assertThat(ChannelCategorizer.isAdult("Foo", "Adult")).isTrue()
    }

    @Test
    fun `primary categorise prefers broadcaster bucket`() {
        assertThat(ChannelCategorizer.categorise("ITV1", "UK Entertainment"))
            .isEqualTo("ITV")
    }
}
