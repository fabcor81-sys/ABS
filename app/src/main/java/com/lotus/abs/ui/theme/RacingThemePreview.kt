package com.lotus.abs.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val RacingYellowColor = Color(0xFFFFCC00)
val RacingBgColor = Color(0xFF0A0A0A)
val RacingCardColor = Color(0xFF141414)
val RacingBorder = Color(0xFF2A2A2A)

@Composable
fun RacingHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mock Lotus Logo
        Box(
            modifier = Modifier.size(56.dp).background(RacingYellowColor, RoundedCornerShape(28.dp))
                .border(2.dp, Color.White, RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("C B", color = Color(0xFF00331A), fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "LOTUS ",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Normal
                )
                Text(
                    "ABS",
                    color = RacingYellowColor,
                    fontSize = 28.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic
                )
            }
            Text(
                "EBC430 Diagnostic & Service Tool",
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

@Composable
fun HeroImagePlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .border(1.dp, RacingBorder, RoundedCornerShape(12.dp))
            .background(Color(0xFF111111), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🏎️", fontSize = 56.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "[ Insert Yellow Exige S2 Image Here ]",
                color = RacingYellowColor,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun RacingGridCard(title: String, icon: ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(1.2f),
        colors = CardDefaults.cardColors(containerColor = RacingCardColor),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, RacingBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp), tint = RacingYellowColor)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

@Preview(widthDp = 400, heightDp = 800, showBackground = true, backgroundColor = 0xFF0A0A0A)
@Composable
fun LotusRacingHomePreview() {
    Column(
        modifier = Modifier.fillMaxSize().background(RacingBgColor).padding(20.dp)
    ) {
        RacingHeader()
        
        HeroImagePlaceholder()
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(modifier = Modifier.weight(1f)) { RacingGridCard("DEVICE\nCONNECTION", Icons.Default.Bluetooth) }
            Box(modifier = Modifier.weight(1f)) { RacingGridCard("LIVE\nDATA", Icons.Default.Speed) }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(modifier = Modifier.weight(1f)) { RacingGridCard("DTC\nERRORS", Icons.Default.Warning) }
            Box(modifier = Modifier.weight(1f)) { RacingGridCard("BLEED\nSYSTEM", Icons.Default.Build) }
        }
    }
}