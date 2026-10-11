package com.lotus.abs.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ThemeMockup(
    bgColor: Color,
    surfaceColor: Color,
    primaryColor: Color,
    onSurfaceColor: Color,
    titleFontFamily: FontFamily,
    titleFontWeight: FontWeight,
    dataFontFamily: FontFamily,
    buttonShape: RoundedCornerShape,
    useBorders: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "LOTUS ABS",
            color = primaryColor,
            fontSize = 32.sp,
            fontFamily = titleFontFamily,
            fontWeight = titleFontWeight
        )
        
        Card(
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            shape = RoundedCornerShape(12.dp),
            modifier = if (useBorders) Modifier.border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp)) else Modifier
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Front Left Wheel", color = onSurfaceColor, fontFamily = titleFontFamily)
                Text("124 km/h", color = primaryColor, fontWeight = FontWeight.Bold, fontFamily = dataFontFamily, fontSize = 20.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = bgColor),
                shape = buttonShape,
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("CONNECT", fontWeight = FontWeight.Bold, fontFamily = titleFontFamily, fontSize = 14.sp)
            }
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = surfaceColor, contentColor = primaryColor),
                shape = buttonShape,
                modifier = Modifier.weight(1f).height(48.dp).let {
                    if (useBorders) it.border(1.dp, primaryColor, buttonShape) else it
                }
            ) {
                Text("READ DTC", fontWeight = FontWeight.Bold, fontFamily = titleFontFamily, fontSize = 14.sp)
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 300, showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun Option1_ClassicJPS() {
    ThemeMockup(
        bgColor = Color(0xFF000000), // Pure black
        surfaceColor = Color(0xFF1A1A1A), // Dark grey
        primaryColor = Color(0xFFD4AF37), // Metallic Gold/Yellow
        onSurfaceColor = Color(0xFFFFFFFF),
        titleFontFamily = FontFamily.Serif,
        titleFontWeight = FontWeight.Bold,
        dataFontFamily = FontFamily.Monospace,
        buttonShape = RoundedCornerShape(4.dp),
        useBorders = true
    )
}

@Preview(widthDp = 360, heightDp = 300, showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun Option2_ModernSport() {
    ThemeMockup(
        bgColor = Color(0xFF121212), // Off-black
        surfaceColor = Color(0xFF242424), // Lighter grey
        primaryColor = Color(0xFFFFEA00), // Racing Yellow
        onSurfaceColor = Color(0xFFE0E0E0),
        titleFontFamily = FontFamily.SansSerif,
        titleFontWeight = FontWeight.Black,
        dataFontFamily = FontFamily.SansSerif,
        buttonShape = RoundedCornerShape(24.dp),
        useBorders = false
    )
}

@Preview(widthDp = 360, heightDp = 300, showBackground = true, backgroundColor = 0xFF050505)
@Composable
fun Option3_TrackTelemetry() {
    ThemeMockup(
        bgColor = Color(0xFF050505), // Deep dark
        surfaceColor = Color(0xFF000000), // Pure black card
        primaryColor = Color(0xFFE5FF00), // Neon Yellow/Green
        onSurfaceColor = Color(0xFFAAAAAA),
        titleFontFamily = FontFamily.Monospace,
        titleFontWeight = FontWeight.Bold,
        dataFontFamily = FontFamily.Monospace,
        buttonShape = RoundedCornerShape(0.dp),
        useBorders = true
    )
}
