package com.pydroidx.app

/** Dark, legible interpretations of the presets supplied with the redesign source. */
data class AppearancePreset(
    val name: String,
    val accent: String,
    val background: String,
    val toolbar: String,
    val tabBar: String,
    val userBubble: String,
    val helperBubble: String,
    val editorText: String,
    val comment: String,
    val string: String,
    val number: String,
    val keyword: String,
    val function: String,
    val variable: String
)

object AppearancePresets {
    val all = listOf(
        AppearancePreset("PY4U Dark", "#78ADFF", "#0D1118", "#171D28", "#171D28", "#283950", "#171D28", "#E6EDF6", "#849A89", "#DBA487", "#B7CAA2", "#B9A8DC", "#E6CA8D", "#A4CAE8"),
        AppearancePreset("OLED", "#A8B9D1", "#000000", "#090B0E", "#090B0E", "#1D232B", "#101318", "#F3F5F7", "#929A9B", "#E1B49C", "#B9CEA6", "#C8B3DB", "#E7D19B", "#AFD1E6"),
        AppearancePreset("Midnight", "#A5B4E9", "#0C1021", "#151B30", "#151B30", "#283354", "#182039", "#E7EAF7", "#8995B2", "#D5A8BC", "#ACC4E3", "#BCB1DF", "#E8CDA2", "#AED0E4"),
        AppearancePreset("Forest", "#91C4A4", "#0E1714", "#19251F", "#19251F", "#274235", "#1B2A22", "#E2EEE6", "#88A592", "#D7B69B", "#BBD1A8", "#A8CDB3", "#E1D09B", "#A9D0C0"),
        AppearancePreset("Coffee", "#DEB390", "#1A1411", "#291F1A", "#291F1A", "#493226", "#2B211C", "#F2E6DC", "#A99386", "#D5A188", "#C9BE9C", "#D4AEB3", "#E5C69C", "#DDC2AB"),
        AppearancePreset("High Contrast", "#E6D983", "#000000", "#101010", "#101010", "#292618", "#171717", "#FFFFFF", "#BBD4BE", "#F7C3AD", "#D5E5A8", "#E2C9FF", "#FFE3A4", "#BBE1F2")
    )
}
