package com.neontides.nativeapp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun GalleryUnlockDialog(
    characterName: String,
    tierTitle: String,
    drawable: Int,
    onContinue: () -> Unit,
    onOpenGallery: () -> Unit
) {
    Dialog(
        onDismissRequest = onContinue,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF080912), Color(0xFF241126), Color(0xFF080912)))
            )
        ) {
            Image(
                painter = painterResource(drawable),
                contentDescription = "$characterName · $tierTitle",
                modifier = Modifier.fillMaxSize().padding(bottom = 150.dp),
                contentScale = ContentScale.Fit
            )
            Surface(
                color = Color(0xE6121320),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            ) {
                Column(
                    Modifier.navigationBarsPadding().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("NUOVA IMMAGINE SBLOCCATA", color = Color(0xFFFF3D9A), fontWeight = FontWeight.Black)
                    Text(characterName, style = MaterialTheme.typography.headlineSmall)
                    Text(tierTitle, color = Color(0xFF6EE7FF))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onContinue, modifier = Modifier.weight(1f)) { Text("Continua") }
                        Button(onClick = onOpenGallery, modifier = Modifier.weight(1f)) { Text("Apri galleria") }
                    }
                }
            }
        }
    }
}
