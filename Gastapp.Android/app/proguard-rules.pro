# Reglas de R8 del telefono (release minificado desde la Fase 6).
#
# Casi todo lo que se usa por reflexion ya trae sus reglas dentro de la libreria:
# - kotlinx.serialization: conserva los serializadores de las clases @Serializable (DTOs de
#   :core, rutas de Navigation, respaldos JSON y el borrador del registro).
# - Retrofit 2.11: conserva las interfaces del API, las firmas genericas y las suspend fun.
# - Room, Hilt y WorkManager: entidades, componentes generados y los Worker (WorkManager
#   guarda el nombre de la clase en su base; su regla lo conserva para que un trabajo
#   programado por una version anterior se pueda seguir ejecutando).
# Si algo truena solo en release, revisar primero estas tres.

# Pilas de error legibles: conserva archivo y numero de linea, y con el mapping.txt de cada
# release (app/build/outputs/mapping/release/) se traducen los nombres ofuscados con
# `retrace`. Guardar ese archivo junto al APK de cada version publicada.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
