package com.example.airecorder.ui.rainbow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun RainbowLoginScreen(
    uiState: RainbowAuthUiState,
    onLoginChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSignIn: () -> Unit,
    onRequestPhonePermission: () -> Unit,
    onDismissError: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2F80FF), Color(0xFFEFF4FF)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Rainbow sign in", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Sign in to Rainbow before using Sonic Note.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6E7B91),
                )

                if (!uiState.hasPhonePermission) {
                    Surface(
                        color = Color(0xFFFFF6E5),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Rainbow needs phone permission before sign-in can start.",
                                color = Color(0xFF8A5A00),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Button(
                                onClick = onRequestPhonePermission,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Allow phone permission")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = uiState.login,
                    onValueChange = onLoginChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Rainbow login") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                )

                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = onPasswordChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                )

                uiState.errorMessage?.let { message ->
                    Surface(
                        color = Color(0xFFFFF1F1),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(message, color = Color(0xFFB3261E), style = MaterialTheme.typography.bodyMedium)
                            TextButton(
                                onClick = onDismissError,
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text("Dismiss")
                            }
                        }
                    }
                }

                Button(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading && uiState.hasPhonePermission,
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(vertical = 2.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Sign in")
                    }
                }
            }
        }
    }
}
