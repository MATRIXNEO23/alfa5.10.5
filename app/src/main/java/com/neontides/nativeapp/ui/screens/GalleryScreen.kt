package com.neontides.nativeapp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.neontides.nativeapp.R
import com.neontides.nativeapp.data.GameData

private data class GalleryTier(val id: String, val title: String, val requirement: String)

private val galleryTiers = listOf(
    GalleryTier("profile", "Primo incontro", "Completa una conversazione"),
    GalleryTier("casual", "Momento personale", "❤️10 · 🤝8"),
    GalleryTier("flirt", "Complicità", "❤️20 · 🔥25"),
    GalleryTier("date", "Appuntamento privato", "❤️35 · 🔥45 · 🤝30"),
    GalleryTier("intimate", "Intimità", "Raggiungi Appuntamenti o Relazione")
)

@Composable
fun GalleryScreen(unlocked: Set<String>, onBack: () -> Unit) {
    var selectedCharacterId by remember { mutableStateOf(GameData.characters.first().id) }
    val selectedCharacter = GameData.characters.first { it.id == selectedCharacterId }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFF090B16)),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Galleria", style = MaterialTheme.typography.headlineLarge)
            Text("Le immagini restano sbloccate anche se la relazione diminuisce.")
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(GameData.characters) { character ->
                    FilterChip(
                        selected = character.id == selectedCharacterId,
                        onClick = { selectedCharacterId = character.id },
                        label = {
                            val count = galleryTiers.count { "${character.id}_${it.id}" in unlocked }
                            Text("${character.name} $count/5")
                        }
                    )
                }
            }
        }
        item {
            val character = selectedCharacter
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(character.name, style = MaterialTheme.typography.titleLarge)
                    galleryTiers.forEach { tier ->
                        val isUnlocked = "${character.id}_${tier.id}" in unlocked
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isUnlocked) Color(0xFF171A29) else Color(0xFF11121A),
                            modifier = Modifier.fillMaxWidth().height(360.dp)
                        ) {
                            Box {
                                if (isUnlocked) {
                                    Image(
                                        painter = painterResource(galleryCharacterDrawable(character.id, tier.id)),
                                        contentDescription = tier.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize().background(Color(0xDD080912)))
                                    Text("🔒", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.align(Alignment.Center))
                                }
                                Surface(
                                    color = Color(0xCC080912),
                                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text(tier.title)
                                        Text(
                                            if (isUnlocked) "Sbloccata" else tier.requirement,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Indietro") } }
    }
}

fun galleryCharacterDrawable(characterId: String, tierId: String): Int = when ("${characterId}_${tierId}") {
    "sofia_profile" -> R.drawable.gallery_sofia_profile
    "sofia_casual" -> R.drawable.gallery_sofia_casual
    "sofia_flirt" -> R.drawable.gallery_sofia_flirt
    "sofia_date" -> R.drawable.gallery_sofia_date
    "sofia_intimate" -> R.drawable.gallery_sofia_intimate
    "maya_profile" -> R.drawable.gallery_maya_profile
    "maya_casual" -> R.drawable.gallery_maya_casual
    "maya_flirt" -> R.drawable.gallery_maya_flirt
    "maya_date" -> R.drawable.gallery_maya_date
    "maya_intimate" -> R.drawable.gallery_maya_intimate
    "elena_profile" -> R.drawable.gallery_elena_profile
    "elena_casual" -> R.drawable.gallery_elena_casual
    "elena_flirt" -> R.drawable.gallery_elena_flirt
    "elena_date" -> R.drawable.gallery_elena_date
    "elena_intimate" -> R.drawable.gallery_elena_intimate
    "luna_profile" -> R.drawable.gallery_luna_profile
    "luna_casual" -> R.drawable.gallery_luna_casual
    "luna_flirt" -> R.drawable.gallery_luna_flirt
    "luna_date" -> R.drawable.gallery_luna_date
    "luna_intimate" -> R.drawable.gallery_luna_intimate
    "chiara_profile" -> R.drawable.gallery_chiara_profile
    "chiara_casual" -> R.drawable.gallery_chiara_casual
    "chiara_flirt" -> R.drawable.gallery_chiara_flirt
    "chiara_date" -> R.drawable.gallery_chiara_date
    "chiara_intimate" -> R.drawable.gallery_chiara_intimate
    "nadia_profile" -> R.drawable.gallery_nadia_profile
    "nadia_casual" -> R.drawable.gallery_nadia_casual
    "nadia_flirt" -> R.drawable.gallery_nadia_flirt
    "nadia_date" -> R.drawable.gallery_nadia_date
    "nadia_intimate" -> R.drawable.gallery_nadia_intimate
    "luca_profile" -> R.drawable.gallery_luca_profile
    "luca_casual" -> R.drawable.gallery_luca_casual
    "luca_flirt" -> R.drawable.gallery_luca_flirt
    "luca_date" -> R.drawable.gallery_luca_date
    "luca_intimate" -> R.drawable.gallery_luca_intimate
    "matteo_profile" -> R.drawable.gallery_matteo_profile
    "matteo_casual" -> R.drawable.gallery_matteo_casual
    "matteo_flirt" -> R.drawable.gallery_matteo_flirt
    "matteo_date" -> R.drawable.gallery_matteo_date
    "matteo_intimate" -> R.drawable.gallery_matteo_intimate
    "kenji_profile" -> R.drawable.gallery_kenji_profile
    "kenji_casual" -> R.drawable.gallery_kenji_casual
    "kenji_flirt" -> R.drawable.gallery_kenji_flirt
    "kenji_date" -> R.drawable.gallery_kenji_date
    "kenji_intimate" -> R.drawable.gallery_kenji_intimate
    else -> R.drawable.character_sofia
}

fun galleryTierTitle(tierId: String): String = galleryTiers.firstOrNull { it.id == tierId }?.title ?: "Nuova immagine"
