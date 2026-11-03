package com.motorsiklo.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.motorsiklo.app.ui.theme.*

@Composable
fun RoleSelectScreen(
    onEnforcerClick: () -> Unit,
    onAdminClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        MotorSikloCard {
            // ----- Header -----
            Text(
                text  = "Select your role".uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text  = "Who are you?",
                style = MaterialTheme.typography.headlineLarge,
                color = colorScheme.onSurface
            )

            Spacer(Modifier.height(32.dp))

            // ----- Role cards -----
            RoleCard(
                title    = "Traffic Enforcer",
                subtitle = "Field officer access",
                icon     = { BadgeIcon(size = 40f) },
                onClick  = onEnforcerClick
            )

            Spacer(Modifier.height(12.dp))

            RoleCard(
                title    = "Administrator",
                subtitle = "System management access",
                icon     = { AdminIcon(size = 40f) },
                onClick  = onAdminClick
            )

            Spacer(Modifier.height(32.dp))

            // ----- Back -----
            GhostButton(text = "← Back", onClick = onBackClick)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RoleSelectScreenPreview() {
    MotorSikloTheme {
        RoleSelectScreen(onEnforcerClick = {}, onAdminClick = {}, onBackClick = {})
    }
}