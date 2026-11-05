# GymProgress Android — v2 em desenvolvimento

Aplicativo Kotlin com Jetpack Compose, Room e sincronização com a API GymProgress.
O registro do treino funciona primeiro no celular. A sincronização envia alterações;
a reimportação substitui os dados locais e não é uma mesclagem entre bancos.

## Repositórios do sistema

| Repositório | Responsabilidade |
|---|---|
| [gymprogress-android](https://github.com/xRyuukossei/gymprogress-android) | Este aplicativo Android |
| [gymprogress-api](https://github.com/xRyuukossei/gymprogress-api) | API e servidor C# |
| [my-fit-journey](https://github.com/xRyuukossei/my-fit-journey) | Interface web |

Branches e versões são independentes por repositório. Os commits deste projeto são
publicados em `gymprogress-android`; não modificam os outros dois repositórios.

A última versão da linha v1 é **1.0.6**, com **versionCode 14**. A tag correspondente é
`v1.0.6`. O desenvolvimento da v2 parte desse fechamento em `codex/v2-development`.
Esta branch começa em **2.0.0-dev**, versionCode **15**, sem mudanças funcionais adicionais.
Use a variante debug para experimentar a v2 com dados separados da instalação de produção.

## Organização

O pacote raiz é `br.com.ryuukossei.gymprogress`; os diretórios Kotlin omitem esse prefixo.

- `data/local`: entidades, consultas Room e snapshots das pendências.
- `data/remote`: contrato da API, validação de URLs e validação do lote recebido.
- `data/repository`: regras de gravação, importação e coordenação das transferências.
- `data/settings`: preferências e token cifrado com Android Keystore.
- `data/sync`: envio, confirmação das pendências e WorkManager.
- `data/draft`: valores ainda não confirmados e exercício aberto na sessão.
- `timer`: alarme e notificação de descanso, independentes da tela.
- `ui/common/OperationRunner.kt`: operações de tela, proteção contra duplo toque e mensagens de falha.
- `ui/theme/Theme.kt`: cores e tipografia. O XML guarda apenas o fundo inicial da janela.
- `tools/*.html`: prévias históricas de design; não são telas executadas pelo aplicativo.

## Configuração

Abra o projeto no Android Studio com o SDK indicado em `app/build.gradle.kts` e o JDK
configurado para este projeto. A debug usa o pacote `.debug` e dados próprios;
a release usa o pacote de produção.

O servidor deve usar HTTPS e o endereço não pode conter credenciais, parâmetros ou
fragmentos. O app não segue redirecionamentos: configure o endereço final da API.
Na primeira abertura, informe o token de aparelho, criado no servidor com:

```text
dotnet GymProgress.Api.dll device-token create "Celular"
```

O token é cifrado com AES-GCM; a chave fica no Android Keystore e o arquivo cifrado
é excluído do backup. As regras também excluem o cronômetro, cujos alarmes não são
restaurados junto com as preferências. O banco e os rascunhos continuam incluídos no
backup configurado pelo Android.

`local.properties`, `keystore.properties`, chaves, APKs e caches não devem entrar no Git.
Preserve o keystore de produção: sem a mesma chave não é possível atualizar a instalação
existente. Não habilite R8 nesta versão sem investigar o problema de inicialização já
registrado em `app/build.gradle.kts`.

## Regras que precisam ser preservadas

- PublicIds são UUIDs canônicos; os mesmos IDs são utilizados no celular e no servidor.
- Confirmar uma série individual inicia descanso na barra inferior; confirmar um lote não.
- Desfazer uma confirmação conserva os valores para correção e envia a exclusão do ID antigo.
- Sessões em andamento e suas séries só são enviadas após finalizar.
- Um snapshot consistente define o lote enviado. A confirmação não limpa alterações posteriores.
- Respostas com avisos mantêm o lote pendente, pois o contrato não identifica a aceitação por registro.
- Importação, troca de conexão e envio usam o mesmo mutex. A rede não bloqueia o registro do treino.
- Antes de reimportar, as pendências são verificadas novamente dentro da transação.
- A importação valida o lote inteiro antes de apagar dados. Coleções obrigatórias ausentes
  não são interpretadas como listas vazias.
- A troca de servidor com pendências ou treino aberto é bloqueada. Reconectar ao mesmo servidor
  preserva os dados locais nesses casos.
- A substituição bem-sucedida limpa rascunhos e cronômetro antigos.
- O banco está na versão 1, com esquema em `app/schemas/`. Mudanças futuras exigem migration;
  não usar `fallbackToDestructiveMigration`.

## Validação

No Android Studio, execute os testes locais e a inspeção Android Lint. Pelo terminal com o
JDK configurado, os comandos equivalentes são:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

O segundo comando exige emulador ou aparelho. Os testes instrumentados desta revisão
usam banco em memória e não alteram os dados pessoais do aplicativo.

Os testes locais cobrem HTTPS, importações incompletas/inconsistentes e regras de formatação.
Os instrumentados cobrem atualização de séries, snapshots, confirmação de envio e rollback.
A execução automática pelo agente ficou bloqueada pelo ambiente de loopback do Gradle;
consulte o resultado e as pendências em [Auditoria v1](docs/AUDITORIA_V1.md).

Para o fechamento da versão e a abertura da v2, siga [Release e branches](docs/RELEASE.md).
