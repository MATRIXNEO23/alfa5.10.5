package com.neontides.nativeapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlayerSetupScreen(
    onStart: (String, Int, String, String, String) -> Unit,
    onBack: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var ageText by rememberSaveable { mutableStateOf("25") }
    var gender by rememberSaveable { mutableStateOf("Maschio") }
    var appearanceId by rememberSaveable { mutableStateOf("male_1") }
    var style by rememberSaveable { mutableStateOf("Naturale") }
    val styles = listOf("Naturale", "Romantico", "Ironico", "Sicuro", "Riservato", "Ribelle")
    val age = ageText.toIntOrNull()
    val valid = name.isNotBlank() && age != null && age in 18..80

    BackHandler(onBack = onBack)
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF080814), Color(0xFF1D0E2B), Color(0xFF071827)))
        ).padding(22.dp)
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center
        ) {
            Text("CREA IL TUO PROFILO", fontSize = 34.sp, fontWeight = FontWeight.Black)
            Text("I personaggi useranno questi dati durante le conversazioni.", color = Color(0xFFCFC9E8))
            Spacer(Modifier.height(22.dp))
            Surface(color = Color(0xC2171324), shape = RoundedCornerShape(26.dp)) {
                Column(Modifier.padding(20.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 24) name = it },
                        label = { Text("Il tuo nome") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = ageText,
                        onValueChange = { ageText = it.filter(Char::isDigit).take(2) },
                        label = { Text("Età (minimo 18)") },
                        singleLine = true,
                        isError = ageText.isNotBlank() && (age == null || age !in 18..80),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(20.dp))
                    Text("Il tuo genere", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("Maschio", "Femmina").forEach { item ->
                            val selected = gender == item
                            Surface(
                                color = if (selected) Color(0xFFFF3D9A) else Color(0xFF2B2736),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f).clickable {
                                    gender = item
                                    appearanceId = if (item == "Femmina") "female_1" else "male_1"
                                }
                            ) {
                                Text(item, Modifier.padding(13.dp), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("Scegli il tuo aspetto", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text("Scorri per vedere tutte e 5 le opzioni", color = Color(0xFFCFC9E8), fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        playerAppearanceOptions(gender).forEachIndexed { index, option ->
                            val selected = appearanceId == option.id
                            Surface(
                                color = if (selected) Color(0xFF32182B) else Color(0xFF171522),
                                border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) Color(0xFFFF3D9A) else Color(0xFF514A61)),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.width(128.dp).height(220.dp).clickable { appearanceId = option.id }
                            ) {
                                Box {
                                    Image(
                                        painter = painterResource(option.drawable),
                                        contentDescription = "Aspetto ${index + 1}",
                                        modifier = Modifier.fillMaxSize().padding(6.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                    Text(
                                        "${index + 1}",
                                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                                        color = Color.White,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("Il tuo stile", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Spacer(Modifier.height(10.dp))
                    styles.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { item ->
                                val selected = style == item
                                Surface(
                                    color = if (selected) Color(0xFFFF3D9A) else Color(0xFF2B2736),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.weight(1f).clickable { style = item }
                                ) {
                                    Text(item, Modifier.padding(13.dp), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { onStart(name.trim(), age ?: 25, gender, appearanceId, style) },
                        enabled = valid,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D9A)),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text("INIZIA LA PARTITA", fontWeight = FontWeight.Bold) }
                    TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Indietro")
                    }
                }
            }
        }
    }
}
