package com.binc.gastapp.ui.legal

// Textos legales que se leen dentro de la app (pantalla Privacidad y legal). Antes el aviso
// era un enlace a privacypolicies.com, pero esa liga ya redirige a la portada del sitio.
// Lo que dicen estos textos sale de lo que hace el codigo de verdad (que se guarda, donde y
// por cuanto tiempo): si cambia algo de eso, hay que actualizarlos y mover LegalUpdated.

/** Correo para dudas y para ejercer los derechos ARCO. */
const val LegalContactEmail = "maatcesar@gmail.com"

/** Fecha de la version vigente de los dos documentos. */
const val LegalUpdated = "4 de octubre de 2026"

enum class LegalDocumentId { Privacy, Terms }

sealed interface LegalBlock {
    data class Paragraph(val text: String) : LegalBlock
    data class Bullets(val items: List<String>) : LegalBlock
}

data class LegalSection(val title: String, val blocks: List<LegalBlock>)

data class LegalDocument(
    val id: LegalDocumentId,
    val title: String,
    /** Una linea para la tarjeta de la pantalla principal. */
    val summary: String,
    val intro: String,
    val sections: List<LegalSection>,
)

fun legalDocument(id: LegalDocumentId): LegalDocument = when (id) {
    LegalDocumentId.Privacy -> PrivacyNotice
    LegalDocumentId.Terms -> TermsOfUse
}

private fun section(title: String, vararg blocks: LegalBlock) = LegalSection(title, blocks.toList())
private fun p(text: String) = LegalBlock.Paragraph(text)
private fun bullets(vararg items: String) = LegalBlock.Bullets(items.toList())

val PrivacyNotice = LegalDocument(
    id = LegalDocumentId.Privacy,
    title = "Aviso de privacidad",
    summary = "Qué datos guardamos, para qué y cómo puedes controlarlos.",
    intro = "Gastapp te ayuda a llevar tus gastos, tarjetas, suscripciones y ahorros. Para eso necesita " +
        "guardar algunos datos tuyos. Aquí te explicamos cuáles, para qué se usan y qué puedes hacer con ellos.",
    sections = listOf(
        section(
            "Responsable",
            p(
                "Gastapp es responsable del tratamiento de tus datos personales. Para cualquier duda sobre este " +
                    "aviso o sobre tus datos, escríbenos a $LegalContactEmail.",
            ),
        ),
        section(
            "Datos que recabamos",
            p("Solo los que tú capturas en la app:"),
            bullets(
                "Cuenta: nombre, correo electrónico, fecha de nacimiento y contraseña.",
                "Ingresos: tu ingreso, cada cuánto te pagan, tus días de pago y el porcentaje que quieres ahorrar.",
                "Movimientos: gastos y pagos con su título, descripción, monto, fecha, categoría, forma de pago y meses sin intereses.",
                "Tarjetas de crédito: nombre, banco, últimos cuatro dígitos (opcionales), días de corte y de pago, límite y color.",
                "Suscripciones: nombre, monto, periodicidad, fechas de cobro y forma de pago.",
                "Reloj: el nombre del reloj que vinculas y el permiso que se le da para registrar gastos.",
            ),
            p(
                "Nunca te pedimos el número completo de tus tarjetas, el código de seguridad, NIP ni contraseñas de tu " +
                    "banco, y la app no se conecta a tus cuentas bancarias.",
            ),
        ),
        section(
            "Para qué los usamos",
            bullets(
                "Crear y mantener tu cuenta e iniciar sesión.",
                "Guardar y sincronizar tu información entre tu teléfono, el servidor y tu reloj.",
                "Calcular tus resúmenes, ahorros, fechas de corte y de pago, y programar tus recordatorios.",
                "Enviarte por correo los códigos para verificar tu cuenta o recuperar tu contraseña.",
                "Proteger el servicio, por ejemplo limitando los intentos de código.",
            ),
            p(
                "No vendemos ni rentamos tus datos, no mostramos anuncios y la app no incluye herramientas de " +
                    "analítica ni de rastreo.",
            ),
        ),
        section(
            "Dónde se guardan y con quién se comparten",
            p(
                "Tu información se guarda primero en tu teléfono y, cuando hay conexión, se sincroniza con el servidor " +
                    "de Gastapp. Para funcionar usamos estos proveedores, que pueden estar fuera de México:",
            ),
            bullets(
                "Render, donde vive el servidor de Gastapp.",
                "Neon, donde está la base de datos.",
                "Resend, que envía los correos con códigos.",
                "Servicios de Google Play, que conectan tu teléfono con tu reloj por Bluetooth.",
                "GitHub, desde donde se descargan las actualizaciones de la app.",
            ),
            p(
                "Estos proveedores solo reciben lo necesario para dar su servicio. Como cualquier servidor, pueden " +
                    "registrar datos técnicos de la conexión, como la dirección IP y la hora. Fuera de ellos, solo " +
                    "compartiríamos tus datos si una autoridad competente lo exige conforme a la ley.",
            ),
        ),
        section(
            "Permisos del teléfono",
            bullets(
                "Notificaciones y alarmas: para tus recordatorios y avisos de tarjeta. Se generan en tu teléfono.",
                "Iniciar con el teléfono: para volver a programar los recordatorios después de reiniciar.",
                "Instalar apps: solo para instalar una actualización de Gastapp cuando tú lo confirmas.",
                "Internet: para sincronizar tu información.",
            ),
        ),
        section(
            "Respaldos",
            p(
                "Si exportas un respaldo, el archivo se guarda donde tú elijas y queda bajo tu cuidado. El archivo no " +
                    "va cifrado: guárdalo en un lugar seguro y no lo compartas.",
            ),
        ),
        section(
            "Cuánto tiempo los conservamos",
            p(
                "Mientras tengas tu cuenta. Lo que borras se quita de la app al momento y se elimina definitivamente " +
                    "del servidor después de 30 días. Los códigos que te enviamos por correo caducan a los 15 minutos. Al cerrar sesión " +
                    "se borran los datos guardados en ese teléfono.",
            ),
        ),
        section(
            "Tus derechos",
            p(
                "Puedes acceder a tus datos, corregirlos, pedir que los eliminemos u oponerte a su uso (derechos ARCO), " +
                    "y también retirar tu consentimiento o pedir que borremos tu cuenta. Escríbenos a " +
                    "$LegalContactEmail desde el correo de tu cuenta, di qué quieres hacer y te responderemos en un " +
                    "plazo máximo de 20 días hábiles.",
            ),
            p("Muchos de tus datos también puedes corregirlos o borrarlos directamente desde la app."),
        ),
        section(
            "Seguridad",
            p(
                "La información viaja cifrada entre la app y el servidor. Tu contraseña se guarda solo como una huella " +
                    "irreversible (hash con sal), así que nadie puede leerla, ni siquiera nosotros. Ningún sistema es " +
                    "infalible: cuida tu contraseña y avísanos si notas algo raro en tu cuenta.",
            ),
        ),
        section(
            "Menores de edad",
            p(
                "Gastapp está pensada para personas mayores de edad. Si eres menor, úsala con permiso y acompañamiento " +
                    "de tu madre, padre o tutor.",
            ),
        ),
        section(
            "Cambios a este aviso",
            p(
                "Si cambiamos este aviso, lo verás aquí con su nueva fecha y te avisaremos dentro de la app cuando el " +
                    "cambio sea importante.",
            ),
        ),
    ),
)

val TermsOfUse = LegalDocument(
    id = LegalDocumentId.Terms,
    title = "Términos y condiciones",
    summary = "Las reglas para usar Gastapp y lo que puedes esperar de ella.",
    intro = "Estos términos explican cómo funciona Gastapp y las reglas para usarla. Al crear una cuenta o iniciar " +
        "sesión los aceptas, junto con el Aviso de privacidad.",
    sections = listOf(
        section(
            "Qué es Gastapp",
            p(
                "Una herramienta personal para registrar y organizar tus gastos, tarjetas, suscripciones y ahorros. " +
                    "Gastapp no es un banco, no mueve tu dinero y no da asesoría financiera.",
            ),
            p(
                "Las fechas de corte y de pago, los saldos y los demás cálculos son estimaciones hechas con lo que tú " +
                    "capturas. Para pagos y fechas límite, lo que vale es lo que diga tu banco o el proveedor del servicio.",
            ),
        ),
        section(
            "Tu cuenta",
            bullets(
                "Usa datos verdaderos y un correo al que tengas acceso.",
                "Cuida tu contraseña: lo que pase con tu cuenta es tu responsabilidad.",
                "Si crees que alguien entró a tu cuenta, cambia tu contraseña y avísanos.",
            ),
        ),
        section(
            "Uso permitido",
            p("Puedes usar Gastapp para tus finanzas personales. No está permitido:"),
            bullets(
                "Intentar entrar a cuentas que no son tuyas.",
                "Atacar, saturar o interferir con el servidor.",
                "Usar la app para actividades ilegales.",
            ),
        ),
        section(
            "Disponibilidad",
            p(
                "Gastapp se ofrece tal como está. El servidor puede tardar un poco en responder o no estar disponible " +
                    "algunos ratos; la app sigue funcionando sin conexión y sincroniza después. Te recomendamos exportar " +
                    "respaldos de vez en cuando.",
            ),
        ),
        section(
            "Precio",
            p("Hoy Gastapp es gratuita. Si eso cambiara, lo avisaríamos antes dentro de la app."),
        ),
        section(
            "Responsabilidad",
            p(
                "En la medida que la ley lo permita, Gastapp no responde por decisiones financieras tomadas con base en " +
                    "la app, por cargos, intereses o recargos de tu banco, ni por pérdidas de información causadas por " +
                    "fallas fuera de nuestro control.",
            ),
        ),
        section(
            "Propiedad",
            p(
                "La app, su diseño y su marca pertenecen a Gastapp. La información que capturas es tuya y puedes " +
                    "llevártela con un respaldo cuando quieras.",
            ),
        ),
        section(
            "Terminación",
            p(
                "Puedes dejar de usar Gastapp cuando quieras y pedir que borremos tu cuenta escribiendo a " +
                    "$LegalContactEmail. Podemos suspender cuentas que incumplan estos términos.",
            ),
        ),
        section(
            "Cambios",
            p(
                "Podemos actualizar estos términos. La versión vigente siempre está en esta pantalla con su fecha, y " +
                    "si el cambio es importante te avisaremos dentro de la app.",
            ),
        ),
        section(
            "Ley aplicable",
            p("Estos términos se rigen por las leyes de los Estados Unidos Mexicanos."),
        ),
        section(
            "Contacto",
            p("Para cualquier duda, escríbenos a $LegalContactEmail."),
        ),
    ),
)
