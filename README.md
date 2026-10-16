# GymProgress — app Android

App nativo (Kotlin + Jetpack Compose) do GymProgress. Funciona primeiro no celular
(banco local Room) e sincroniza com a API do GymProgress.

## Estrutura

O código fica em `app/src/main/kotlin/`, seguindo a convenção do Kotlin de omitir o
pacote raiz (`br.com.ryuukossei.gymprogress`) das pastas:

- `data/local` — banco local (Room): entidades, consultas e banco.
- `data/remote` — cliente da API (`/api/sync`).
- `data/settings` — configurações; o token fica criptografado com o Android Keystore.
- `data/repository` — importação e acesso aos dados.
- `ui/` — telas (Compose) e navegação.

## Configuração inicial

Na primeira abertura, o app pede o endereço da API e um token de aparelho, criado no
servidor com:

    dotnet GymProgress.Api.dll device-token create "Celular"

## Esquema do banco

O esquema do Room é exportado para `app/schemas/` a cada build e deve ser versionado.
Mudanças de esquema exigem migration explícita (o app não apaga dados locais).
