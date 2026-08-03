package com.igarciamen.viewerapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

@Composable
fun LoginScreen(auth: FirebaseAuth, onLoginExitoso: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Iniciar sesión", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Correo electrónico") },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        if (mensaje != null) {
            Text(mensaje!!, modifier = Modifier.padding(top = 8.dp))
        }

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    mensaje = "Rellena email y contraseña"
                    return@Button
                }
                cargando = true
                auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        cargando = false
                        onLoginExitoso()
                    }
                    .addOnFailureListener { error ->
                        cargando = false
                        mensaje = "Error al iniciar sesión: ${error.message}"
                    }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text(if (cargando) "Cargando..." else "Iniciar sesión")
        }

        TextButton(
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    mensaje = "Rellena email y contraseña"
                    return@TextButton
                }
                if (password.length < 6) {
                    mensaje = "La contraseña debe tener al menos 6 caracteres"
                    return@TextButton
                }
                cargando = true
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        cargando = false
                        onLoginExitoso()
                    }
                    .addOnFailureListener { error ->
                        cargando = false
                        mensaje = "Error al crear cuenta: ${error.message}"
                    }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("¿No tienes cuenta? Crear una")
        }
    }
}