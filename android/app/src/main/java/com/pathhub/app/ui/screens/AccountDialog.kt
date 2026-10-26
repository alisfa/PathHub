package com.pathhub.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pathhub.app.data.AuthService
import com.pathhub.app.data.SupabaseConfig
import com.pathhub.app.ui.theme.Emerald500
import com.pathhub.app.ui.theme.Rose500
import com.pathhub.app.ui.theme.Slate400
import com.pathhub.app.ui.theme.Slate700
import com.pathhub.app.ui.theme.Slate800
import com.pathhub.app.ui.theme.Slate900
import kotlinx.coroutines.launch

@Composable
fun AccountDialog(
    authService: AuthService,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by authService.user.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Account / Upgrade, 1: Server Config
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    var customUrl by remember { mutableStateOf(SupabaseConfig.getUrl(context)) }
    var customKey by remember { mutableStateOf(SupabaseConfig.getKey(context)) }

    AlertDialog(
        containerColor = Slate800,
        titleContentColor = Color.White,
        onDismissRequest = onDismiss,
        title = {
            Text("Sync & Account", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Slate900,
                    contentColor = Emerald500
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Account", color = if (selectedTab == 0) Emerald500 else Slate400) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Server", color = if (selectedTab == 1) Emerald500 else Slate400) }
                    )
                }

                if (selectedTab == 0) {
                    // Account info
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate700)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = if (user?.isAnonymous == false) "Signed In" else "Anonymous Guest",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = user?.email ?: "ID: ${user?.id?.take(12)}...",
                            color = Slate400,
                            fontSize = 12.sp
                        )
                    }

                    if (user?.isAnonymous == true) {
                        Text(
                            text = "Upgrade with email to share your playlist and sites across iOS and Android.",
                            color = Slate400,
                            fontSize = 13.sp
                        )

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Slate700,
                                unfocusedContainerColor = Slate700,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = Emerald500,
                                focusedLabelColor = Emerald500
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Slate700,
                                unfocusedContainerColor = Slate700,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = Emerald500,
                                focusedLabelColor = Emerald500
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                                enabled = email.isNotBlank() && password.isNotBlank() && !isSubmitting,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    isSubmitting = true
                                    scope.launch {
                                        val res = authService.signUpWithEmail(email.trim(), password.trim())
                                        isSubmitting = false
                                        if (res.isSuccess) {
                                            Toast.makeText(context, "Account created!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Sign up error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            ) {
                                Text("Sign Up", color = Slate900, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                                enabled = email.isNotBlank() && password.isNotBlank() && !isSubmitting,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    isSubmitting = true
                                    scope.launch {
                                        val res = authService.signInWithEmail(email.trim(), password.trim())
                                        isSubmitting = false
                                        if (res.isSuccess) {
                                            Toast.makeText(context, "Signed in!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Sign in error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            ) {
                                Text("Sign In", color = Color.White)
                            }
                        }
                    } else {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = Rose500),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                authService.signOut()
                                Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Sign Out", color = Color.White)
                        }
                    }
                } else {
                    // Server Config
                    Text(
                        text = "Configured Supabase Endpoint:",
                        color = Slate400,
                        fontSize = 13.sp
                    )

                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        label = { Text("Supabase URL") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Slate700,
                            unfocusedContainerColor = Slate700,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Emerald500,
                            focusedLabelColor = Emerald500
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = customKey,
                        onValueChange = { customKey = it },
                        label = { Text("Publishable Key") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Slate700,
                            unfocusedContainerColor = Slate700,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Emerald500,
                            focusedLabelColor = Emerald500
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            SupabaseConfig.saveConfig(context, customUrl, customKey)
                            Toast.makeText(context, "Server config updated", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Save Config", color = Slate900, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Emerald500)
            }
        }
    )
}
