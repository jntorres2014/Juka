package com.example.juka.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.juka.data.AuthManager
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

// ✅ MODELO DE PERFIL EXTENDIDO
data class UserProfileData(
    val uid: String = "",
    val displayName: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val birthDate: String? = null,
    val phoneNumber: String? = null,
    val bio: String? = null,
    val location: String? = null,
    val occupation: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLogin: Long = System.currentTimeMillis()
)

// ✅ PANTALLA DE PERFIL MEJORADA - TOMA DATOS DE GOOGLE AUTOMÁTICAMENTE
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleProfileScreen(
    user: FirebaseUser,
    authManager: AuthManager
) {
    var isEditing by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var profileData by remember { mutableStateOf<UserProfileData?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val db = FirebaseFirestore.getInstance()

    // Estados para campos editables
    var birthDate by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var occupation by remember { mutableStateOf("") }

    // ✅ CARGAR DATOS AL INICIAR
    LaunchedEffect(user) {
        isLoading = true
        try {
            // Intentar cargar datos adicionales de Firestore
            val doc = db.collection("perfil")
                .document(user.uid)
                .get()
                .await()

            if (doc.exists()) {
                val profile = doc.toObject(UserProfileData::class.java)
                profileData = profile
                // Cargar campos editables
                birthDate = profile?.birthDate ?: ""
                phoneNumber = profile?.phoneNumber ?: ""
                bio = profile?.bio ?: ""
                location = profile?.location ?: ""
                occupation = profile?.occupation ?: ""
            } else {
                // Si no existe, crear perfil inicial con datos de Google
                val newProfile = UserProfileData(
                    uid = user.uid,
                    displayName = user.displayName,
                    email = user.email,
                    photoUrl = user.photoUrl?.toString()
                )

                db.collection("perfil")
                    .document(user.uid)
                    .set(newProfile)
                    .await()

                profileData = newProfile
            }
        } catch (e: Exception) {
            // Si hay error, usar solo datos de Google
            profileData = UserProfileData(
                uid = user.uid,
                displayName = user.displayName,
                email = user.email,
                photoUrl = user.photoUrl?.toString()
            )
        }
        isLoading = false
    }

    // ✅ FUNCIÓN PARA GUARDAR CAMBIOS
    fun saveProfile() {
        coroutineScope.launch {
            isLoading = true
            try {
                val updatedProfile = UserProfileData(
                    uid = user.uid,
                    displayName = user.displayName,
                    email = user.email,
                    photoUrl = user.photoUrl?.toString(),
                    birthDate = birthDate,
                    phoneNumber = phoneNumber,
                    bio = bio,
                    location = location,
                    occupation = occupation,
                    createdAt = profileData?.createdAt ?: System.currentTimeMillis(),
                    lastLogin = System.currentTimeMillis()
                )

                db.collection("perfil")
                    .document(user.uid)
                    .set(updatedProfile)
                    .await()

                profileData = updatedProfile
                isEditing = false
            } catch (e: Exception) {
                // Manejar error
            }
            isLoading = false
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ✅ HEADER CON FOTO Y DATOS PRINCIPALES DE GOOGLE
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 📸 FOTO DE PERFIL DE GOOGLE
                        if (user.photoUrl != null) {
                            AsyncImage(
                                model = user.photoUrl.toString(),
                                contentDescription = "Foto de perfil",
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Surface(
                                modifier = Modifier.size(100.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(25.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 👤 NOMBRE DE GOOGLE
                        Text(
                            text = user.displayName ?: "Usuario sin nombre",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        // 📧 EMAIL DE GOOGLE
                        Text(
                            text = user.email ?: "Sin email",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )

                        // 🎯 PROVEEDOR DE AUTENTICACIÓN
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Verificado con Google",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ✅ BOTÓN DE EDITAR
                if (!isEditing) {
                    Button(
                        onClick = { isEditing = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Completar Perfil")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ✅ INFORMACIÓN ADICIONAL
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Información Personal",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        if (isEditing) {
                            // 🖊️ MODO EDICIÓN
                            OutlinedTextField(
                                value = birthDate,
                                onValueChange = { birthDate = it },
                                label = { Text("Fecha de nacimiento") },
                                placeholder = { Text("dd/mm/aaaa") },
                                leadingIcon = {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { phoneNumber = it },
                                label = { Text("Teléfono") },
                                leadingIcon = {
                                    Icon(Icons.Default.Phone, contentDescription = null)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = location,
                                onValueChange = { location = it },
                                label = { Text("Ubicación") },
                                leadingIcon = {
                                    Icon(Icons.Default.LocationOn, contentDescription = null)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = occupation,
                                onValueChange = { occupation = it },
                                label = { Text("Ocupación") },
                                leadingIcon = {
                                    Icon(Icons.Default.Work, contentDescription = null)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = bio,
                                onValueChange = { bio = it },
                                label = { Text("Biografía") },
                                leadingIcon = {
                                    Icon(Icons.Default.Info, contentDescription = null)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // BOTONES DE GUARDAR/CANCELAR
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        isEditing = false
                                        // Restaurar valores originales
                                        birthDate = profileData?.birthDate ?: ""
                                        phoneNumber = profileData?.phoneNumber ?: ""
                                        bio = profileData?.bio ?: ""
                                        location = profileData?.location ?: ""
                                        occupation = profileData?.occupation ?: ""
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancelar")
                                }

                                Button(
                                    onClick = { saveProfile() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Guardar")
                                }
                            }

                        } else {
                            // 👁️ MODO VISTA
                            InfoRow(
                                icon = Icons.Default.CalendarToday,
                                label = "Fecha de nacimiento",
                                value = profileData?.birthDate ?: "No especificada"
                            )

                            InfoRow(
                                icon = Icons.Default.Phone,
                                label = "Teléfono",
                                value = profileData?.phoneNumber ?: "No especificado"
                            )

                            InfoRow(
                                icon = Icons.Default.LocationOn,
                                label = "Ubicación",
                                value = profileData?.location ?: "No especificada"
                            )

                            InfoRow(
                                icon = Icons.Default.Work,
                                label = "Ocupación",
                                value = profileData?.occupation ?: "No especificada"
                            )

                            InfoRow(
                                icon = Icons.Default.Info,
                                label = "Biografía",
                                value = profileData?.bio ?: "Sin biografía"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ✅ INFORMACIÓN DE LA CUENTA
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Información de la cuenta",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        InfoRow(
                            icon = Icons.Default.Key,
                            label = "ID de usuario",
                            value = user.uid.take(20) + "..."
                        )

                        InfoRow(
                            icon = Icons.Default.DateRange,
                            label = "Miembro desde",
                            value = profileData?.createdAt?.let {
                                SimpleDateFormat("dd MMMM yyyy", Locale("es")).format(Date(it))
                            } ?: "Desconocido"
                        )

                        InfoRow(
                            icon = Icons.Default.AccessTime,
                            label = "Último acceso",
                            value = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es"))
                                .format(Date(user.metadata?.lastSignInTimestamp ?: 0))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ✅ BOTÓN DE CERRAR SESIÓN
                Button(
                    onClick = { authManager.signOut() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cerrar Sesión")
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// ✅ COMPONENTE PARA MOSTRAR UNA FILA DE INFORMACIÓN
@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = value,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}