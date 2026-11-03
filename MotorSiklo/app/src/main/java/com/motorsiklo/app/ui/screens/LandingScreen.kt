package com.motorsiklo.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motorsiklo.app.ui.theme.*

@Composable
fun LandingScreen(
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    // Column-based layout ensures that the Header and Content stay separated,
    // preventing the theme button from being blocked by the CardView.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // --- Header Section ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(onClick = { ThemeManager.toggleTheme(context) }) {
                Icon(
                    imageVector = if (ThemeManager.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Toggle Theme",
                    tint = colorScheme.primary
                )
            }
        }

        // --- Main Content Area ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            MotorSikloCard(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                // Removing the negative offset to ensure the card doesn't slide
                // up under the theme toggle button.
            ) {
                // Brand Identity Section
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    // Logo - Size set to 105f to maintain a "big enough" appearance 
                    // while ensuring it doesn't inflate the card too much.
                    MotorSikloLogo(size = 105f)

                    Spacer(Modifier.height(20.dp))

                    Text(
                        text = "MOTORSIKLO",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 3.sp
                        ),
                        color = colorScheme.primary,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "Violation Detection System",
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 1.2.sp
                        ),
                        color = colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                // Tagline
                Text(
                    text = "AI-powered motorcycle monitoring and automated ticketing for safer roads.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                // Action Buttons
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PrimaryButton(text = "Sign In", onClick = onLoginClick)
                    OutlineButton(text = "Register Enforcer", onClick = onRegisterClick)
                }

                Spacer(Modifier.height(32.dp))
                HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.05f))
                Spacer(Modifier.height(24.dp))

                // Footer
                Text(
                    text = "HELLOWORLD TECH.",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    letterSpacing = 1.5.sp
                )
            }
        }
    }
}