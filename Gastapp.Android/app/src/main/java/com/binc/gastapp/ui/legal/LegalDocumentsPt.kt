package com.binc.gastapp.ui.legal

// Los mismos textos legales en portugues (Brasil), para telefonos en portugues.
// Es una traduccion de LegalDocuments.kt: si cambia uno, hay que cambiar el otro (y el de
// ingles). Lo dice la introduccion: en caso de diferencia, vale la version en espanol.

private fun section(title: String, vararg blocks: LegalBlock) = LegalSection(title, blocks.toList())
private fun p(text: String) = LegalBlock.Paragraph(text)
private fun bullets(vararg items: String) = LegalBlock.Bullets(items.toList())

val PrivacyNoticePt = LegalDocument(
    id = LegalDocumentId.Privacy,
    title = "Aviso de privacidade",
    summary = "Quais dados guardamos, para quê e como você pode controlá-los.",
    intro = "O Gastapp ajuda você a acompanhar seus gastos, cartões, assinaturas e economias. Para isso, precisa " +
        "guardar alguns dados seus. Aqui explicamos quais são, para que são usados e o que você pode fazer com eles. " +
        "Esta é uma tradução da versão em espanhol, que prevalece em caso de qualquer diferença.",
    sections = listOf(
        section(
            "Responsável",
            p(
                "O Gastapp é responsável pelo tratamento dos seus dados pessoais. Para qualquer dúvida sobre este " +
                    "aviso ou sobre os seus dados, escreva para $LegalContactEmail.",
            ),
        ),
        section(
            "Dados que coletamos",
            p("Somente os que você informa no app:"),
            bullets(
                "Conta: nome, e-mail, data de nascimento e senha.",
                "Renda: sua renda, com que frequência você recebe, seus dias de pagamento e a porcentagem que você quer economizar.",
                "Lançamentos: gastos e pagamentos com título, descrição, valor, data, categoria, forma de pagamento e parcelamentos sem juros.",
                "Cartões de crédito: nome, banco, últimos quatro dígitos (opcionais), dias de fechamento e de vencimento, limite e cor.",
                "Assinaturas: nome, valor, periodicidade, datas de cobrança e forma de pagamento.",
                "Relógio: o nome do relógio que você vincula e a permissão que ele recebe para registrar gastos.",
            ),
            p(
                "Nunca pedimos o número completo dos seus cartões, o código de segurança, o PIN nem as senhas do seu " +
                    "banco, e o app não se conecta às suas contas bancárias.",
            ),
        ),
        section(
            "Para que os usamos",
            bullets(
                "Criar e manter sua conta e fazer o login.",
                "Guardar e sincronizar suas informações entre o celular, o servidor e o relógio.",
                "Calcular seus resumos, economias, datas de fechamento e de vencimento, e programar seus lembretes.",
                "Enviar por e-mail os códigos para verificar sua conta ou recuperar sua senha.",
                "Proteger o serviço, por exemplo limitando as tentativas de código.",
            ),
            p(
                "Não vendemos nem alugamos seus dados, não mostramos anúncios e o app não inclui ferramentas de análise " +
                    "de uso nem de rastreamento.",
            ),
        ),
        section(
            "Onde ficam guardados e com quem são compartilhados",
            p(
                "Suas informações são guardadas primeiro no seu celular e, quando há conexão, são sincronizadas com o " +
                    "servidor do Gastapp. Para funcionar, usamos estes provedores, que podem estar fora do México:",
            ),
            bullets(
                "Render, onde fica o servidor do Gastapp.",
                "Neon, onde fica o banco de dados.",
                "Resend, que envia os e-mails com códigos.",
                "Serviços do Google Play, que conectam seu celular ao relógio por Bluetooth.",
                "GitHub, de onde são baixadas as atualizações do app.",
            ),
            p(
                "Esses provedores recebem apenas o necessário para prestar o serviço. Como qualquer servidor, podem " +
                    "registrar dados técnicos da conexão, como o endereço IP e a hora. Além deles, só compartilharíamos " +
                    "seus dados se uma autoridade competente exigir, conforme a lei.",
            ),
        ),
        section(
            "Permissões do celular",
            bullets(
                "Notificações e alarmes: para seus lembretes e avisos de cartão. Eles são gerados no seu celular.",
                "Iniciar com o celular: para programar os lembretes novamente depois de reiniciar.",
                "Instalar apps: somente para instalar uma atualização do Gastapp quando você confirmar.",
                "Internet: para sincronizar suas informações.",
            ),
        ),
        section(
            "Backups",
            p(
                "Se você exportar um backup, o arquivo é salvo onde você escolher e fica sob o seu cuidado. O arquivo " +
                    "não é criptografado: guarde-o em um lugar seguro e não o compartilhe.",
            ),
        ),
        section(
            "Por quanto tempo os guardamos",
            p(
                "Enquanto você tiver a sua conta. O que você exclui sai do app na hora e é eliminado definitivamente " +
                    "do servidor após 30 dias. Os códigos que enviamos por e-mail expiram em 15 minutos. Ao sair da " +
                    "conta, os dados guardados naquele celular são apagados.",
            ),
        ),
        section(
            "Seus direitos",
            p(
                "Você pode acessar seus dados, corrigi-los, pedir que os excluamos ou se opor ao seu uso (direitos " +
                    "ARCO, da lei mexicana), e também retirar seu consentimento ou pedir que excluamos sua conta. " +
                    "Escreva para $LegalContactEmail a partir do e-mail da sua conta, diga o que você quer fazer e " +
                    "responderemos em até 20 dias úteis.",
            ),
            p("Muitos dos seus dados também podem ser corrigidos ou apagados diretamente no app."),
        ),
        section(
            "Segurança",
            p(
                "As informações trafegam criptografadas entre o app e o servidor. Sua senha é guardada apenas como uma " +
                    "impressão irreversível (hash com sal), então ninguém consegue lê-la, nem mesmo nós. Nenhum sistema " +
                    "é infalível: cuide da sua senha e avise-nos se notar algo estranho na sua conta.",
            ),
        ),
        section(
            "Menores de idade",
            p(
                "O Gastapp foi pensado para pessoas maiores de idade. Se você é menor, use-o com a permissão e o " +
                    "acompanhamento de sua mãe, pai ou responsável.",
            ),
        ),
        section(
            "Mudanças neste aviso",
            p(
                "Se mudarmos este aviso, você o verá aqui com a nova data e avisaremos dentro do app quando a mudança " +
                    "for importante.",
            ),
        ),
    ),
)

val TermsOfUsePt = LegalDocument(
    id = LegalDocumentId.Terms,
    title = "Termos e condições",
    summary = "As regras para usar o Gastapp e o que você pode esperar dele.",
    intro = "Estes termos explicam como o Gastapp funciona e as regras para usá-lo. Ao criar uma conta ou entrar, você " +
        "os aceita, junto com o Aviso de privacidade. Esta é uma tradução da versão em espanhol, que prevalece em " +
        "caso de qualquer diferença.",
    sections = listOf(
        section(
            "O que é o Gastapp",
            p(
                "Uma ferramenta pessoal para registrar e organizar seus gastos, cartões, assinaturas e economias. O " +
                    "Gastapp não é um banco, não movimenta o seu dinheiro e não oferece consultoria financeira.",
            ),
            p(
                "As datas de fechamento e de vencimento, os saldos e os demais cálculos são estimativas feitas com o que " +
                    "você informa. Para pagamentos e prazos, vale o que disser o seu banco ou o prestador do serviço.",
            ),
        ),
        section(
            "Sua conta",
            bullets(
                "Use dados verdadeiros e um e-mail ao qual você tenha acesso.",
                "Cuide da sua senha: o que acontecer com a sua conta é de sua responsabilidade.",
                "Se achar que alguém entrou na sua conta, altere sua senha e avise-nos.",
            ),
        ),
        section(
            "Uso permitido",
            p("Você pode usar o Gastapp para as suas finanças pessoais. Não é permitido:"),
            bullets(
                "Tentar acessar contas que não são suas.",
                "Atacar, sobrecarregar ou interferir no servidor.",
                "Usar o app para atividades ilegais.",
            ),
        ),
        section(
            "Disponibilidade",
            p(
                "O Gastapp é oferecido como está. O servidor pode demorar um pouco para responder ou ficar indisponível " +
                    "em alguns momentos; o app continua funcionando sem conexão e sincroniza depois. Recomendamos " +
                    "exportar backups de vez em quando.",
            ),
        ),
        section(
            "Preço",
            p("Hoje o Gastapp é gratuito. Se isso mudar, avisaremos antes dentro do app."),
        ),
        section(
            "Responsabilidade",
            p(
                "Na medida permitida pela lei, o Gastapp não se responsabiliza por decisões financeiras tomadas com base " +
                    "no app, por cobranças, juros ou encargos do seu banco, nem por perdas de informação causadas por " +
                    "falhas fora do nosso controle.",
            ),
        ),
        section(
            "Propriedade",
            p(
                "O app, seu design e sua marca pertencem ao Gastapp. As informações que você informa são suas e você " +
                    "pode levá-las com um backup quando quiser.",
            ),
        ),
        section(
            "Encerramento",
            p(
                "Você pode deixar de usar o Gastapp quando quiser e pedir que excluamos sua conta escrevendo para " +
                    "$LegalContactEmail. Podemos suspender contas que descumprirem estes termos.",
            ),
        ),
        section(
            "Mudanças",
            p(
                "Podemos atualizar estes termos. A versão vigente está sempre nesta tela com a sua data, e se a mudança " +
                    "for importante avisaremos dentro do app.",
            ),
        ),
        section(
            "Lei aplicável",
            p("Estes termos são regidos pelas leis dos Estados Unidos Mexicanos."),
        ),
        section(
            "Contato",
            p("Para qualquer dúvida, escreva para $LegalContactEmail."),
        ),
    ),
)
