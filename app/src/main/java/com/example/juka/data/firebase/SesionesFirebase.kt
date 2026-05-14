// Archivo intencionalmente vacío.
//
// La clase SesionesFirebase fue eliminada como parte del refactor que removió
// el concepto de "ParteSessionChat" (chat + parte + estado en un único objeto
// persistido en Firestore). Hoy:
//   - Los datos del parte se manejan en EnhancedChatViewModel como
//     ParteEnProgreso (StateFlow) y se auto-guardan como borrador local
//     vía LocalStorageHelper.saveBorrador (uno por id, multi-borrador).
//   - El parte finalizado se persiste en Firestore vía
//     PartesFirebase.guardarParteCompletado.
//   - El historial de chat queda en Room (LocalStorageHelper) si se desea.
//
// Este archivo se mantiene como placeholder para que Git registre la eliminación
// y para evitar imports rotos durante el rebuild. Puede borrarse en el próximo
// commit.
package com.example.juka.data.firebase
