package com.binc.gastapp.ui.legal

// Los mismos textos legales en ingles (para telefonos en otro idioma que no sea espanol).
// Es una traduccion de LegalDocuments.kt: si cambia uno, hay que cambiar el otro. Lo dice
// la introduccion: en caso de diferencia, vale la version en espanol.

private fun section(title: String, vararg blocks: LegalBlock) = LegalSection(title, blocks.toList())
private fun p(text: String) = LegalBlock.Paragraph(text)
private fun bullets(vararg items: String) = LegalBlock.Bullets(items.toList())

val PrivacyNoticeEn = LegalDocument(
    id = LegalDocumentId.Privacy,
    title = "Privacy Notice",
    summary = "What data we keep, what for, and how you can control it.",
    intro = "Gastapp helps you keep track of your expenses, cards, subscriptions and savings. To do that it needs to " +
        "store some of your data. Here we explain which data, what it's used for and what you can do with it. This is a " +
        "translation of the Spanish version, which prevails in case of any difference.",
    sections = listOf(
        section(
            "Data controller",
            p(
                "Gastapp is responsible for the processing of your personal data. For any question about this notice " +
                    "or about your data, write to us at $LegalContactEmail.",
            ),
        ),
        section(
            "Data we collect",
            p("Only what you enter in the app:"),
            bullets(
                "Account: name, email, date of birth and password.",
                "Income: your income, how often you get paid, your paydays and the percentage you want to save.",
                "Transactions: expenses and payments with their title, description, amount, date, category, payment method and installment plans.",
                "Credit cards: name, bank, last four digits (optional), statement and payment days, limit and color.",
                "Subscriptions: name, amount, billing frequency, charge dates and payment method.",
                "Watch: the name of the watch you link and the permission it's given to record expenses.",
            ),
            p(
                "We never ask for your full card numbers, security codes, PINs or bank passwords, and the app doesn't " +
                    "connect to your bank accounts.",
            ),
        ),
        section(
            "What we use it for",
            bullets(
                "Creating and maintaining your account and signing you in.",
                "Storing and syncing your information between your phone, the server and your watch.",
                "Calculating your summaries, savings, statement and payment dates, and scheduling your reminders.",
                "Emailing you the codes to verify your account or recover your password.",
                "Protecting the service, for example by limiting code attempts.",
            ),
            p(
                "We don't sell or rent your data, we don't show ads and the app doesn't include analytics or tracking tools.",
            ),
        ),
        section(
            "Where it's stored and who it's shared with",
            p(
                "Your information is stored first on your phone and, when there's a connection, it's synced with the " +
                    "Gastapp server. To work we use these providers, which may be outside Mexico:",
            ),
            bullets(
                "Render, where the Gastapp server runs.",
                "Neon, where the database is.",
                "Resend, which sends the emails with codes.",
                "Google Play services, which connect your phone with your watch over Bluetooth.",
                "GitHub, where app updates are downloaded from.",
            ),
            p(
                "These providers only receive what they need to provide their service. Like any server, they may log " +
                    "technical connection data, such as the IP address and time. Beyond them, we would only share your " +
                    "data if a competent authority requires it under the law.",
            ),
        ),
        section(
            "Phone permissions",
            bullets(
                "Notifications and alarms: for your reminders and card alerts. They're generated on your phone.",
                "Start with the phone: to schedule your reminders again after a restart.",
                "Install apps: only to install a Gastapp update when you confirm it.",
                "Internet: to sync your information.",
            ),
        ),
        section(
            "Backups",
            p(
                "If you export a backup, the file is saved wherever you choose and is in your care. The file isn't " +
                    "encrypted: keep it somewhere safe and don't share it.",
            ),
        ),
        section(
            "How long we keep it",
            p(
                "As long as you have your account. What you delete is removed from the app right away and permanently " +
                    "deleted from the server after 30 days. The codes we email you expire after 15 minutes. When you sign " +
                    "out, the data stored on that phone is deleted.",
            ),
        ),
        section(
            "Your rights",
            p(
                "You can access your data, correct it, ask us to delete it or object to its use (ARCO rights under " +
                    "Mexican law), and also withdraw your consent or ask us to delete your account. Write to us at " +
                    "$LegalContactEmail from your account's email, tell us what you want to do and we'll reply within " +
                    "20 business days at most.",
            ),
            p("You can also correct or delete much of your data directly in the app."),
        ),
        section(
            "Security",
            p(
                "Information travels encrypted between the app and the server. Your password is stored only as an " +
                    "irreversible fingerprint (salted hash), so no one can read it, not even us. No system is " +
                    "infallible: take care of your password and let us know if you notice anything odd in your account.",
            ),
        ),
        section(
            "Minors",
            p(
                "Gastapp is intended for adults. If you're a minor, use it with the permission and guidance of your " +
                    "parent or guardian.",
            ),
        ),
        section(
            "Changes to this notice",
            p(
                "If we change this notice, you'll see it here with its new date and we'll let you know in the app when " +
                    "the change is significant.",
            ),
        ),
    ),
)

val TermsOfUseEn = LegalDocument(
    id = LegalDocumentId.Terms,
    title = "Terms and Conditions",
    summary = "The rules for using Gastapp and what you can expect from it.",
    intro = "These terms explain how Gastapp works and the rules for using it. By creating an account or signing in " +
        "you accept them, along with the Privacy Notice. This is a translation of the Spanish version, which prevails " +
        "in case of any difference.",
    sections = listOf(
        section(
            "What Gastapp is",
            p(
                "A personal tool to record and organize your expenses, cards, subscriptions and savings. Gastapp isn't " +
                    "a bank, doesn't move your money and doesn't give financial advice.",
            ),
            p(
                "Statement and payment dates, balances and the other calculations are estimates based on what you enter. " +
                    "For payments and due dates, what your bank or the service provider says is what counts.",
            ),
        ),
        section(
            "Your account",
            bullets(
                "Use truthful information and an email you have access to.",
                "Take care of your password: what happens with your account is your responsibility.",
                "If you think someone got into your account, change your password and let us know.",
            ),
        ),
        section(
            "Permitted use",
            p("You can use Gastapp for your personal finances. It's not allowed to:"),
            bullets(
                "Try to access accounts that aren't yours.",
                "Attack, overload or interfere with the server.",
                "Use the app for illegal activities.",
            ),
        ),
        section(
            "Availability",
            p(
                "Gastapp is provided as is. The server may take a moment to respond or be unavailable at times; the app " +
                    "keeps working offline and syncs later. We recommend exporting backups every now and then.",
            ),
        ),
        section(
            "Price",
            p("Gastapp is free today. If that changed, we'd let you know in the app beforehand."),
        ),
        section(
            "Liability",
            p(
                "To the extent permitted by law, Gastapp isn't liable for financial decisions made based on the app, for " +
                    "charges, interest or fees from your bank, or for loss of information caused by failures beyond our control.",
            ),
        ),
        section(
            "Ownership",
            p(
                "The app, its design and its brand belong to Gastapp. The information you enter is yours and you can " +
                    "take it with you in a backup whenever you want.",
            ),
        ),
        section(
            "Termination",
            p(
                "You can stop using Gastapp whenever you want and ask us to delete your account by writing to " +
                    "$LegalContactEmail. We may suspend accounts that breach these terms.",
            ),
        ),
        section(
            "Changes",
            p(
                "We may update these terms. The current version is always on this screen with its date, and if the " +
                    "change is significant we'll let you know in the app.",
            ),
        ),
        section(
            "Governing law",
            p("These terms are governed by the laws of the United Mexican States."),
        ),
        section(
            "Contact",
            p("For any question, write to us at $LegalContactEmail."),
        ),
    ),
)
