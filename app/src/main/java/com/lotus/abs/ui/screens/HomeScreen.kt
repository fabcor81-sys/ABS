package com.lotus.abs.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.lotus.abs.R
import com.lotus.abs.ui.theme.LotusYellow

@Composable
fun HomeScreen(onNavigate: (String) -> Unit, isConnected: Boolean, connectionType: String) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Racing Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.lotus_cars_logo),
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

        // Hero Image Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141619)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.exige_s2_side),
                contentDescription = "Lotus Exige S2",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .padding(4.dp),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (isConnected) "Adapter connected" else "Adapter disconnected",
                        color = if (isConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isConnected) connectionType else "Connect an adapter to access ABS functions",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { onNavigate("connection") }) {
                    Text(if (isConnected) "Manage" else "Connect")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Navigation Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HomeCard(title = "CONNECTION", icon = Icons.Default.Bluetooth, modifier = Modifier.weight(1f)) { onNavigate("connection") }
            HomeCard(title = "LIVE DATA", icon = Icons.Default.Speed, modifier = Modifier.weight(1f)) { onNavigate("live_data") }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HomeCard(title = "DTC ERRORS", icon = Icons.Default.Warning, modifier = Modifier.weight(1f)) { onNavigate("dtc") }
            HomeCard(title = "ABS BLEEDING", icon = Icons.Default.Build, modifier = Modifier.weight(1f)) { onNavigate("bleed") }
        }

    }
}

@Composable
fun HomeCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isHorizontal: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .then(if (!isHorizontal) Modifier.aspectRatio(1.25f) else Modifier)
            .clickable { onClick() }
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (!isHorizontal) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(32.dp),
                    tint = LotusYellow
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.SansSerif,
                    color = Color.White
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(28.dp),
                    tint = LotusYellow
                )
                Spacer(modifier = Modifier.width(12.dp))
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
}
