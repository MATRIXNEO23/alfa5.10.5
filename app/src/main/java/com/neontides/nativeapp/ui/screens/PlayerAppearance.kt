package com.neontides.nativeapp.ui.screens

import com.neontides.nativeapp.R

data class PlayerAppearanceOption(val id: String, val drawable: Int)

fun playerAppearanceOptions(gender: String): List<PlayerAppearanceOption> =
    if (gender == "Femmina") listOf(
        PlayerAppearanceOption("female_1", R.drawable.player_female_1),
        PlayerAppearanceOption("female_2", R.drawable.player_female_2),
        PlayerAppearanceOption("female_3", R.drawable.player_female_3),
        PlayerAppearanceOption("female_4", R.drawable.player_female_4),
        PlayerAppearanceOption("female_5", R.drawable.player_female_5)
    ) else listOf(
        PlayerAppearanceOption("male_1", R.drawable.player_male_1),
        PlayerAppearanceOption("male_2", R.drawable.player_male_2),
        PlayerAppearanceOption("male_3", R.drawable.player_male_3),
        PlayerAppearanceOption("male_4", R.drawable.player_male_4),
        PlayerAppearanceOption("male_5", R.drawable.player_male_5)
    )

fun playerAppearanceDrawable(id: String): Int =
    (playerAppearanceOptions(if (id.startsWith("female_")) "Femmina" else "Maschio")
        .firstOrNull { it.id == id } ?: playerAppearanceOptions("Maschio").first()).drawable

fun playerAppearanceFaceDrawable(id: String): Int = when (id) {
    "female_1" -> R.drawable.phone_face_player_female_1
    "female_2" -> R.drawable.phone_face_player_female_2
    "female_3" -> R.drawable.phone_face_player_female_3
    "female_4" -> R.drawable.phone_face_player_female_4
    "female_5" -> R.drawable.phone_face_player_female_5
    "male_1" -> R.drawable.phone_face_player_male_1
    "male_2" -> R.drawable.phone_face_player_male_2
    "male_3" -> R.drawable.phone_face_player_male_3
    "male_4" -> R.drawable.phone_face_player_male_4
    "male_5" -> R.drawable.phone_face_player_male_5
    else -> R.drawable.phone_face_player_male_1
}
