# Revisão para fechamento da v1

Escopo: código Android, recursos, configurações de build, persistência, transferências e
organização do repositório local. Não é uma certificação de segurança nem uma auditoria do
servidor, do aparelho ou das dependências transitivas contra uma base atual de CVEs.

## Correções

| Área | Problema encontrado | Tratamento |
|---|---|---|
| Importação | Pendências podiam mudar durante o download e ser apagadas | Snapshot determinístico antes da rede e nova comparação dentro da transação |
| Sincronização | Leituras separadas podiam formar lote inconsistente | Snapshot Room transacional para montar o envio |
| Confirmação | Lotes com avisos eram marcados como enviados | Mantém lote pendente e mostra que requer revisão; o reenvio usa PublicIds idempotentes |
| Exclusões | Desfazer série importada removia apenas a cópia local | Registra tombstone e envia `deletedSets` |
| Sessões | Finalizar e cancelar podiam competir entre leitura e gravação | Finalização transacional, com validação de sessão aberta ao gravar séries |
| Integridade | Série podia apontar para planejamento de outro treino | Valida vínculo, número da série, duplicatas e limites antes de gravar o lote |
| API | URL aceitava HTTP, credenciais e parâmetros | HTTPS obrigatório; URL canônica; sem redirecionamentos automáticos |
| API | Corpo recebido sem limite e exportações com listas ausentes aceitas | Limite de 32 MiB e coleções obrigatórias na exportação |
| Importação | IDs, referências, números e datas não eram validados de forma conjunta | Validador único, antes da substituição do banco |
| Conexão | Troca de servidor podia preservar dados pendentes e enviá-los ao novo destino | Bloqueia troca de servidor com dados pendentes/treino aberto; permite recuperar a conexão original |
| Credenciais | Falha do Keystore podia ocorrer só depois de substituir dados | Prepara a cifra antes da transação de importação |
| Interface | Falhas de banco podiam escapar das coroutines e duplos toques criar operações repetidas | OperationRunner, com mensagens e cancelamento preservado |
| Setup | Token permanecia no estado do editor após conexão | Limpa o texto após sucesso |
| Intents | ID recebido externamente era interpolado na rota sem validação | Valida UUID antes de abrir sessão |
| Restauração | Rascunhos e cronômetro de dados substituídos permaneciam ativos | Limpa após importação bem-sucedida; exclui cronômetro do backup |
| Notificações | Revogação de permissão entre checagem e envio podia lançar exceção | Trata a corrida sem invalidar o registro da série |
| Manutenção | APIs de pausa/extensão, consultas, recursos e testes de exemplo sem uso | Remove implementações abandonadas; mantém apenas leitura/retomada de pausa legada |

O estado de pausa legado é mantido apenas para não perder um descanso salvo por uma versão
intermediária. Não existe mais tela separada nem comando novo para pausar.

## Validações e limites

- `git diff --check`: verificado durante a revisão.
- Consultas DAO: 62 consultas preparadas contra o esquema Room real; casos de contagem em SQLite temporário, sem dados reais.
- XML: 27 arquivos analisados; delimitadores de 42 arquivos Kotlin verificados. Isso não substitui compilação. Fonte Poppins e sua licença preservadas.
- Busca de padrões de segredos nos arquivos versionáveis, sem imprimir valores sensíveis.
- Testes reais adicionados no lugar dos exemplos: 13 locais e 5 instrumentados.
- Tentativa de testes e lint offline pelo Gradle: bloqueada. Primeiro houve acesso negado ao
  cache fora do workspace; com execução autorizada, o Gradle falhou ao estabelecer conexão
  de loopback. Isso não equivale a aprovação de compilação, testes ou lint.
- Não houve instalação, chamada à API de produção, commit, merge ou push nesta revisão.
- Não houve alteração do esquema Room nem das credenciais/chaves existentes.

## Verificações ainda necessárias no Android Studio

1. Rodar os testes locais, Android Lint e testes instrumentados.
2. Início → iniciar → sair → Início; repetir após finalizar, retomada e notificação do descanso.
3. Registrar individualmente e em lote; desfazer, corrigir, finalizar e reabrir o histórico.
4. Confirmar alerta de descanso com tela bloqueada, permissão negada e app reaberto.
5. Em ambiente de teste, simular falha de rede, resposta com avisos, reenvio e `deletedSets`.
6. Em ambiente de teste, modificar dados durante download de reimportação: deve recusar
   substituir. Verificar exportação vazia completa e exportação incompleta.
7. Compilar release com a chave original e instalar como atualização da instalação existente,
   verificando preservação de dados e funcionamento dos dois temas.

## Pontos para a v2

- Confirmação por registro no protocolo de sincronização, para tratar rejeições parciais sem
  manter todo o lote pendente.
- Estratégia explícita de conflitos e evolução/estatísticas separadas da tela de histórico.
- Migrations e índices de unicidade para novas regras de persistência.
- Testes de carga, de navegação e de integração com um servidor de homologação.
- Revisão das dependências transitivas e investigação de R8 antes de reabilitar otimizações.

Fechamento definido: versão **1.0.6**, versionCode **14**, tag **v1.0.6**.
A validação funcional no aparelho, incluindo a reimportação, foi confirmada pelo responsável
em 05/11/2026. A execução automatizada pelo agente continua registrada como bloqueada;
não se presume resultado de JUnit, testes instrumentados ou lint sem seus relatórios.

## Compatibilidade da reimportação após o teste no aparelho

A revisão inicial incluiu restrições sem correspondência no contrato local da API
(`DTOs/Sync/SyncDtos.cs` e `Controllers/WorkoutSessionsController.cs` do repositório da API). A importação agora preserva números de série
repetidos com IDs diferentes, vínculos históricos entre planejamentos e sessões e ordens
negativas aceitas pelo servidor. Datas históricas são verificadas quanto ao formato, sem
impedir a leitura de sessões com duração inconsistente; a apresentação já limita durações
negativas a zero.

Continuam obrigatórios: coleções completas, UUIDs únicos por registro, referências
existentes e limites numéricos do contrato. Os novos registros feitos no Android continuam
sujeitos às regras de vínculo e de número da série do repositório. Mensagens de importação
agora apresentam motivos controlados ou nomes de campos ausentes, sem incluir o JSON ou
o token. A reimportação foi testada novamente no aparelho e aprovada pelo responsável.
