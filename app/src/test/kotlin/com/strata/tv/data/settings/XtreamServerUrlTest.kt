package com.strata.tv.data.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class XtreamServerUrlTest {

    @Test
    fun `preserves http and custom port`() {
        val result = XtreamServerUrl.normalize("http://panel.example:8080/")
        assertThat(result.getOrNull()).isEqualTo("http://panel.example:8080")
        assertThat(XtreamServerUrl.isHttp(result.getOrNull()!!)).isTrue()
    }

    @Test
    fun `does not upgrade http to https`() {
        val result = XtreamServerUrl.normalize("http://panel.example")
        assertThat(result.getOrNull()).isEqualTo("http://panel.example")
    }

    @Test
    fun `strips player_api path`() {
        val result = XtreamServerUrl.normalize(
            "https://panel.example:25461/player_api.php?username=x",
        )
        assertThat(result.getOrNull()).isEqualTo("https://panel.example:25461")
    }

    @Test
    fun `adds http when scheme missing`() {
        val result = XtreamServerUrl.normalize("panel.example:8080")
        assertThat(result.getOrNull()).isEqualTo("http://panel.example:8080")
    }
}
