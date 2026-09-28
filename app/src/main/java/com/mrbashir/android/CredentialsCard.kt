package com.mrbashir.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Prominent credentials onboarding form displayed when no credentials are saved.
 * Features frictionless labels, password show/hide eye toggle, and a visually primary
 * "Save & start" button.
 */
@Composable
fun CredentialsCard(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    validationError: String?,
    onSaveAndStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Portal credentials",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Save once. Mr. Bashir logs you in automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Username / Student ID") },
                placeholder = { Text("e.g. k21-1234") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Portal password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = onTogglePasswordVisibility) {
                        Icon(
                            painter = painterResource(
                                id = if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility
                            ),
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            validationError?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSaveAndStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save & start", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Convenient overload managing password visibility internally.
 * Matches PROJECT.md interface contract.
 */
@Composable
fun CredentialsCard(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onSaveAndStart: () -> Unit,
    modifier: Modifier = Modifier,
    validationError: String? = null
) {
    var passwordVisible by remember { mutableStateOf(false) }
    CredentialsCard(
        username = username,
        onUsernameChange = onUsernameChange,
        password = password,
        onPasswordChange = onPasswordChange,
        passwordVisible = passwordVisible,
        onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
        validationError = validationError,
        onSaveAndStart = onSaveAndStart,
        modifier = modifier
    )
}
