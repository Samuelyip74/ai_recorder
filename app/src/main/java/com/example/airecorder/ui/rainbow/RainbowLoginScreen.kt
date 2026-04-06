package com.example.airecorder.ui.rainbow

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.airecorder.R

@Composable
fun RainbowLoginScreen(
    uiState: RainbowAuthUiState,
    onLoginChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSignIn: () -> Unit,
    onRequestPhonePermission: () -> Unit,
    onDismissError: () -> Unit,
) {
    val accent = Color(0xFFA85CF3)
    val accentSoft = Color(0xFFE9D6FF)
    val borderColor = Color(0xFFD9D6E3)
    val muted = Color(0xFF7F7A8E)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFF2F80FF),
                        0.28f to Color(0xFF1F67E7),
                        0.58f to Color(0xFFEAF1FF),
                        1.0f to Color(0x00FFFFFF),
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 34.dp, vertical = 44.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.rb_notes_logo),
                    contentDescription = "RB-Notes app icon",
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(112.dp),
                )

                if (!uiState.hasPhonePermission) {
                    Surface(
                        color = Color(0xFFFFF6E5),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                "RB-Notes needs phone permission before sign-in can start.",
                                color = Color(0xFF8A5A00),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Button(
                                onClick = onRequestPhonePermission,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent,
                                    contentColor = Color.White,
                                ),
                            ) {
                                Text("Allow phone permission")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = uiState.login,
                    onValueChange = onLoginChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(999.dp),
                    placeholder = { Text("Email Address", color = muted) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    colors = inputColors(borderColor, muted),
                )

                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = onPasswordChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(999.dp),
                    placeholder = { Text("Password", color = muted) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    colors = inputColors(borderColor, muted),
                )

                uiState.errorMessage?.let { message ->
                    Surface(
                        color = Color(0xFFFFF1F1),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                message,
                                color = Color(0xFFB3261E),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(
                                onClick = onDismissError,
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text("Dismiss", color = accent)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = onSignIn,
                        enabled = !uiState.isLoading && uiState.hasPhonePermission,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = Color.White,
                            disabledContainerColor = accentSoft,
                            disabledContentColor = Color(0xFF8D77A9),
                        ),
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Login", fontWeight = FontWeight.SemiBold)
                            androidx.compose.material3.Icon(
                                Icons.AutoMirrored.Outlined.Login,
                                contentDescription = null,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun inputColors(borderColor: Color, muted: Color) = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    disabledContainerColor = Color.White,
    focusedBorderColor = borderColor,
    unfocusedBorderColor = borderColor,
    disabledBorderColor = borderColor,
    focusedTextColor = Color(0xFF151220),
    unfocusedTextColor = Color(0xFF151220),
    cursorColor = Color(0xFFA85CF3),
    focusedPlaceholderColor = muted,
    unfocusedPlaceholderColor = muted,
    focusedLeadingIconColor = Color(0xFFB2AFC0),
    unfocusedLeadingIconColor = Color(0xFFB2AFC0),
    focusedTrailingIconColor = Color(0xFFA85CF3),
    unfocusedTrailingIconColor = Color(0xFFA85CF3),
)
