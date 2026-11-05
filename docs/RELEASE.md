# Fechamento da v1 e início da v2

## Versão final da v1

- Repositório: `xRyuukossei/gymprogress-android`.
- Versão: **1.0.6**; versionCode: **14**; tag: **v1.0.6**.
- Integração: `develop` → `main`, por fast-forward a partir da base comum `ea0379a`.
- Próxima linha de desenvolvimento: `codex/v2-development`, criada a partir da v1 final.
- O responsável confirmou a validação funcional no aparelho, incluindo reimportação,
  após as correções de compatibilidade descritas em `AUDITORIA_V1.md`.
- Os testes automatizados não foram executados com sucesso pelo agente: o ambiente do
  Gradle não conseguiu estabelecer conexão de loopback. Preservar essa distinção no histórico.

## Organização no GitHub

Este fechamento pertence somente ao repositório Android. `gymprogress-api` é o servidor
C# e `my-fit-journey` é a interface web. Cada repositório possui branches independentes;
fazer commit ou merge aqui não altera o código nem publica os outros projetos.

O commit é criado primeiro no Git local. O push publica o commit/branch no remoto
`origin`, configurado para `https://github.com/xRyuukossei/gymprogress-android.git`.
A `main` recebe a última v1 validada. A tag permite localizar esse código mesmo depois
de novos desenvolvimentos; não mover a tag para reaproveitá-la em outra versão.

## Geração de produção

Fazer merge na `main` não instala o aplicativo no aparelho nem publica APK automaticamente.
Gerar o APK assinado a partir de `v1.0.6`, preservando o applicationId e o keystore originais,
e instalar como atualização da instalação existente. Guardar o APK fora do Git com a
identificação da versão e do commit utilizados.

Credenciais, keystore, SDK local, APKs e caches não entram no commit. O banco continua na
versão 1; não houve migration nem limpeza de dados para preparar este fechamento.

## Desenvolvimento da v2

A branch `codex/v2-development` começa no fechamento da v1 e pode avançar para `2.0.0`,
com versionCode maior que 14. A `main` permanece em 1.0.6 até a v2 estar pronta.
Estatísticas, evolução e mudanças grandes ficam nessa branch. Novas alterações no esquema
Room devem ter migrations explícitas e testes de atualização a partir de bancos v1.

Para correções urgentes da v1, partir da `main`, testar e publicar uma nova versão/tag;
levar a correção também à v2. Não misturar mudanças incompletas da v2 na manutenção da v1.
