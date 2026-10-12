package com.lotus.abs.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lotus.abs.R
import com.lotus.abs.ui.theme.LotusYellow

@Composable
fun HomeScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Racing Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.lotus_logo),
                contentDescription = "Lotus Logo",
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "LOTUS ",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "ABS",
                        color = LotusYellow,
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

        // Hero Image
        Image(
            painter = painterResource(id = R.drawable.exige_s2),
            contentDescription = "Lotus Exige S2",
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Navigation Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HomeCard(title = "DEVICE\nCONNECTION", icon = Icons.Default.Bluetooth, modifier = Modifier.weight(1f)) { navController.navigate("connection") }
            HomeCard(title = "LIVE\nDATA", icon = Icons.Default.Speed, modifier = Modifier.weight(1f)) { navController.navigate("live_data") }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HomeCard(title = "DTC\nERRORS", icon = Icons.Default.Warning, modifier = Modifier.weight(1f)) { navController.navigate("dtc") }
            HomeCard(title = "BLEED\nSYSTEM", icon = Icons.Default.Build, modifier = Modifier.weight(1f)) { navController.navigate("bleed") }
        }
    }
}

@Composable
fun HomeCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .aspectRatio(1.2f)
            .clickable { onClick() }
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(36.dp),
                tint = LotusYellow
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.SansSerif,
                color = Color.White
            )
        }
    }
}
