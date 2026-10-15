package com.strata.tv.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CategoryRegionTest {

    @Test
    fun prefixOf_readsEnAndUk() {
        assertThat(CategoryRegion.prefixOf("EN | BBC")).isEqualTo("EN")
        assertThat(CategoryRegion.prefixOf("UK | Football")).isEqualTo("UK")
        assertThat(CategoryRegion.prefixOf("English | Movies")).isEqualTo("ENGLISH")
        assertThat(CategoryRegion.prefixOf("United Kingdom | News")).isEqualTo("UNITED KINGDOM")
        assertThat(CategoryRegion.prefixOf("Drama")).isEmpty()
        assertThat(CategoryRegion.prefixOf("")).isEmpty()
    }

    @Test
    fun displayName_stripsRegionPrefix() {
        assertThat(CategoryRegion.displayName("EN | BBC")).isEqualTo("BBC")
        assertThat(CategoryRegion.displayName("UK | Football")).isEqualTo("Football")
        assertThat(CategoryRegion.displayName("EN | Movies")).isEqualTo("Movies")
        assertThat(CategoryRegion.displayName("Drama")).isEqualTo("Drama")
    }

    @Test
    fun ukWhitelist_keepsEnPacks() {
        val uk = setOf("UK", "GB")
        assertThat(CategoryRegion.matchesRegion("EN | BBC", uk)).isTrue()
        assertThat(CategoryRegion.matchesRegion("UK | Sky", uk)).isTrue()
        assertThat(CategoryRegion.matchesRegion("GB | ITV", uk)).isTrue()
        assertThat(CategoryRegion.matchesRegion("US | ABC", uk)).isFalse()
        assertThat(CategoryRegion.matchesRegion("DE | RTL", uk)).isFalse()
    }

    @Test
    fun emptyWhitelist_keepsEverything() {
        assertThat(CategoryRegion.matchesRegion("US | ABC", emptySet())).isTrue()
        assertThat(CategoryRegion.matchesRegion("Hindi Movies", emptySet())).isTrue()
    }

    @Test
    fun mixedCatalogue_unprefixedForeignHiddenWhenUkSelected() {
        val uk = setOf("UK")
        assertThat(CategoryRegion.matchesRegion("German", uk)).isFalse()
        assertThat(CategoryRegion.matchesRegion("Hindi Movies", uk)).isFalse()
        assertThat(CategoryRegion.matchesRegion("Entertainment", uk)).isTrue()
    }

    @Test
    fun excludedCategories_matchKeywords() {
        val excluded = setOf("XXX", "Adult")
        assertThat(CategoryRegion.matchesExcludedCategory("EN | XXX", excluded)).isTrue()
        assertThat(CategoryRegion.matchesExcludedCategory("UK | Sports", excluded)).isFalse()
    }

    @Test
    fun enAndUk_areNotTheSameToken_butAffinityLinksThem() {
        assertThat(CategoryRegion.prefixOf("EN | A")).isEqualTo("EN")
        assertThat(CategoryRegion.prefixOf("UK | A")).isEqualTo("UK")
        assertThat(CategoryRegion.prefixOf("EN | A")).isNotEqualTo(CategoryRegion.prefixOf("UK | A"))
        val expanded = CategoryRegion.expandWhitelist(setOf("UK"))
        assertThat(expanded).containsAtLeast("UK", "EN", "GB")
    }

    @Test
    fun preferredEnglishUk_sortHelper() {
        assertThat(CategoryRegion.isPreferredEnglishUk("EN | Movies")).isTrue()
        assertThat(CategoryRegion.isPreferredEnglishUk("UK | BBC")).isTrue()
        assertThat(CategoryRegion.isPreferredEnglishUk("US | NBC")).isFalse()
    }
}
