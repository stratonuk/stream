package com.strata.tv.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ForeignContentFilterTest {

    @Test
    fun doesNotTreatEnglishAsForeign() {
        assertThat(ForeignContentFilter.countryFor("English")).isNull()
        assertThat(ForeignContentFilter.countryFor("EN | Movies")).isNull()
        assertThat(ForeignContentFilter.shouldExclude("EN | Movies", setOf("UK", "GB"))).isFalse()
    }

    @Test
    fun dropsNamedForeignCategoriesWhenUkOnly() {
        val uk = setOf("UK", "GB")
        assertThat(ForeignContentFilter.shouldExclude("German", uk)).isTrue()
        assertThat(ForeignContentFilter.shouldExclude("Hindi Movies", uk)).isTrue()
        assertThat(ForeignContentFilter.shouldExclude("Drama", uk)).isFalse()
    }
}
