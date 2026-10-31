# fiap-fase4-execution-service

Execution Service da Fase 4 do Tech Challenge (FIAP SOAT): **fila de diagnóstico e reparo por filial**, em
DynamoDB. Quarkus 3.26.3, Java 17, Clean Architecture com ArchUnit.

> Arquitetura em
> [fiap-fase4-os-service/docs/arquitetura](https://github.com/CleytonOngaratto/fiap-fase4-os-service/tree/main/docs/arquitetura).
> O modelo da tabela está na
> [ADR-009](https://github.com/CleytonOngaratto/fiap-fase4-os-service/blob/main/docs/arquitetura/adr/ADR-009-tabela-unica-do-execution.md).

## O papel na Saga

A Saga é **orquestrada**, e o orquestrador vive no OS Service. Este serviço é **participante**: recebe comandos
numa fila FIFO, guarda o próprio estado e devolve eventos. Ele nunca decide o processo — decide só o que é dele:
em que ponto da execução cada ordem de serviço está.

Três regras valem para tudo o que ele faz:

1. **Comando responde uma vez por `messageId`.** Quem decide se a mensagem já foi tratada é a *inbox*, não o
   estado do item: a reentrega não produz segundo efeito **nem segunda resposta**.
2. **`CancelDiagnosis` responde sempre** — inclusive quando não existe diagnóstico nenhum, e inclusive quando o
   item já terminou em falha. Calar nesses casos deixaria a saga em `COMPENSATING` para sempre, porque esse
   passo é o último de toda lista de compensação e espera confirmação.
3. **Falha de negócio é evento de resposta, nunca exceção que sobe.** `DiagnosisFailed` e `RepairFailed` são
   gravados na mesma transação do estado. O que sobe como exceção é falha técnica — e aí a mensagem fica na fila.

### Comandos que consome

Fila `execution-commands.fifo` (deste repositório; o orquestrador a lê no SSM para escrever nela).

| Comando | O que faz | Responde |
|---|---|---|
| `StartDiagnosis` | põe a ordem na fila de diagnóstico da filial (`DIAGNOSIS_QUEUED`) | nada agora — quem responde é o mecânico, ao iniciar |
| `CancelDiagnosis` | encerra o item, se houver o que encerrar | `DiagnosisCanceled`, **sempre** |
| `StartRepair` | põe a ordem na fila de reparo (`REPAIR_QUEUED`), com os itens do orçamento | nada agora — quem responde é o mecânico |

Comando de tipo desconhecido é registrado e descartado; deixá-lo na fila travaria todas as mensagens da mesma
ordem de serviço, porque a fila é FIFO por OS. Conflito de estado também sai da fila, pelo mesmo motivo. O que
**não** sai é falha técnica: a mensagem volta, esgota as tentativas e para na fila de descarte.

### Eventos que publica

Tópico `execution-events.fifo` (da plataforma), por **outbox**: o evento nasce como item na mesma
`TransactWriteItems` que grava o estado, e um *relay* agendado publica depois.

| Evento | Quando | Payload |
|---|---|---|
| `DiagnosisStarted` | mecânico inicia o diagnóstico | `mechanic`, `startedAt` |
| `DiagnosisCompleted` | mecânico conclui, com descrição e itens validados | `description`, `items[{type, catalogId, quantity}]` |
| `DiagnosisFailed` | mecânico registra que não há reparo viável | `reason` |
| `DiagnosisCanceled` | resposta a `CancelDiagnosis` | — |
| `RepairStarted` | mecânico inicia o reparo | `startedAt` |
| `RepairCompleted` | mecânico conclui o reparo — é o **pivô** da saga | `completedAt` |
| `RepairFailed` | mecânico registra falha no reparo | `reason` |

**O preço não sai daqui.** `DiagnosisCompleted` leva o que foi diagnosticado; quem precifica é o catálogo do OS,
e o valor fica congelado no snapshot dele. O envelope e o significado de cada campo estão no
[catálogo de mensagens](https://github.com/CleytonOngaratto/fiap-fase4-os-service/blob/main/docs/arquitetura/catalogo-de-mensagens.md).

## API

Tudo sob `/execution/v1`. Toda rota de mecânico exige **JWT de ADMIN**; `/info` é pública, porque é o smoke da
borda. O nome do mecânico sai do **próprio token**, nunca do corpo da requisição.

| Método e rota | O que faz | Respostas |
|---|---|---|
| `GET /info` | nome e versão do serviço | 200 |
| `GET /queue?branchId=&status=&limit=` | fila da filial, **da mais antiga para a mais nova** | 200 · 400 filial ausente ou status desconhecido · 401 · 403 |
| `POST /work-orders/{id}/diagnosis/start` | inicia o diagnóstico | 202 · 404 · 409 |
| `POST /work-orders/{id}/diagnosis/complete` | conclui com `description` e `items` | 202 · **400** item fora do catálogo · **503** OS fora do ar · 404 · 409 |
| `POST /work-orders/{id}/diagnosis/fail` | registra diagnóstico inviável (`reason`) | 202 · 404 · 409 |
| `POST /work-orders/{id}/repair/start` | inicia o reparo | 202 · 404 · 409 |
| `POST /work-orders/{id}/repair/complete` | conclui o reparo | 202 · 404 · 409 |
| `POST /work-orders/{id}/repair/fail` | registra falha no reparo (`reason`) | 202 · 404 · 409 |

**202, não 200:** a resposta diz que o fato foi registrado; o efeito na saga chega depois, quando o relay publica
o evento. **409 vem do estado:** transição pedida por pessoa recusa quando o item não está no ponto que ela
pressupõe — ao contrário da transição dirigida pela saga, que simplesmente não acontece e segue.

### A validação dos itens do diagnóstico

Ao concluir um diagnóstico, o serviço valida os itens **antes de escrever qualquer coisa**, chamando
`POST /carworkshop/v1/internal/catalog/validate` no OS Service pelo ALB interno, com `X-Service-Token`. É a
única chamada síncrona que ele faz, e existe porque o retorno é **feedback a uma pessoa**: item inválido tem de
voltar como 400 na hora, não minutos depois.

Timeout curto, retentativa só para 5xx e falha de transporte, e **circuit breaker**: com o circuito aberto o 503
sai sem nova chamada. O porquê do endpoint em lote, em vez de repassar o JWT do mecânico, está na
[§6 do catálogo](https://github.com/CleytonOngaratto/fiap-fase4-os-service/blob/main/docs/arquitetura/catalogo-de-mensagens.md).

## Ambiente local

`docker compose up -d` sobe quatro contêineres e a aplicação: **DynamoDB Local** (com a tabela criada por um
init), **ElasticMQ** com a fila e a fila de descarte, e um **WireMock** no papel do catálogo do OS.

O ElasticMQ não emula SNS: em local o relay **registra o envelope em log** em vez de publicar
(`EXECUTION_EVENTS_PUBLISH_TO_LOG=true`). A chave pública do JWT vem de `./secrets/publicKey.pem`, que não está
no repositório.

## Testes

`./mvnw clean verify` — **137 testes, cobertura de linha 99,2%**, gate do JaCoCo em 0,80, ArchUnit 4/4.
O teste de integração precisa do **Docker ligado**.

A divisão não é estética. Classe exercitada **só** por `@QuarkusTest` conta **0%** no JaCoCo destes
repositórios, por um artefato de classloader medido desde a fase anterior. Então:

- **teste puro** (colaboradores mockados, campo injetado atribuído à mão, `Clock` fixo) para tudo o que precisa
  contar no gate — e é onde se afirma a forma do `TransactWriteItems`, das condições de escrita e das consultas
  de índice;
- **teste de integração** com DynamoDB Local e ElasticMQ para o que só comportamento real prova: a mesma
  mensagem duas vezes deixando **um** item de outbox, a transação cancelada que não grava nem o histórico, e a
  fila por filial voltando ordenada;
- **contrato com WireMock** para o cliente do catálogo: 5xx retentado por contagem de chamadas, timeout, corpo
  malformado e o circuito abrindo.

Pela mesma razão, o timeout e os limiares do circuito são **parâmetros de construtor**, não anotações: com
anotações de *fault tolerance* a prova exigiria subir a aplicação, e o cliente contaria zero.

## Deploy

| Workflow | Gatilho | O que faz |
|---|---|---|
| [`maven-ci.yml`](.github/workflows/maven-ci.yml) | PR e `push` na `main` | build, testes, JaCoCo, análise no SonarCloud (`build` e `SonarCloud Code Analysis` são checks obrigatórios) e Trivy |
| [`terraform.yml`](.github/workflows/terraform.yml) | PR → `fmt-validate` (check obrigatório) + `plan` · `push` na `main` com mudança em `terraform/` → `apply` · `workflow_dispatch` → `plan`/`apply`/`destroy` | a infraestrutura em [`terraform/`](terraform/): a tabela DynamoDB com os dois índices, a fila e a fila de descarte, o target group `ip` e a regra `/execution/v1/*` no ALB interno |
| [`cd.yml`](.github/workflows/cd.yml) | `push` na `main` (fora `*.md`, `docs/` e `terraform/`) e `workflow_dispatch` | testes → imagem no ECR (tag = SHA do commit) → manifestos no EKS → smoke: alvos `healthy` no target group e, com a borda de pé, `GET /execution/v1/info` → **200** pelo API Gateway |

**O merge aplica.** Um merge que mude `terraform/` cria ou altera recursos na AWS — só com a sessão do
laboratório ligada e com o teardown na mesma sessão. Num merge com código e `terraform/` os dois workflows rodam
juntos: o `cd.yml` espera no SSM o target group, a fila e a tabela (até 25 min) e falha dizendo quem publica.

## Contrato no SSM

| Lê | De quem |
|---|---|
| `/fase4/vpc/id`, `/fase4/alb/listener-arn` | plataforma ([fiap-fase4-infra-k8s](https://github.com/CleytonOngaratto/fiap-fase4-infra-k8s)), no `terraform/` |
| `/fase4/ecr/execution/repo-url`, `/fase4/eks/cluster-name`, `/fase4/alb/sg-id`, `/fase4/alb/dns`, `/fase4/sns/execution-events/topic-arn` | plataforma, no `cd.yml` |
| `/fase4/os/internal/service-token` | [fiap-fase4-os-service](https://github.com/CleytonOngaratto/fiap-fase4-os-service) — a credencial de serviço do endpoint interno do catálogo |
| `/fase4/jwt/public-key`, `/fase4/newrelic/license-key` | bootstrap manual (sobrevive ao teardown) |

| Publica | Conteúdo |
|---|---|
| `/fase4/execution/alb/target-group-arn` | target group que o `TargetGroupBinding` liga aos pods |
| `/fase4/execution/sqs/commands-queue-url` | fila de comandos deste serviço; **o orquestrador do OS a lê** para escrever nela |
| `/fase4/execution/dynamodb/table-name` | a tabela única do serviço |

Nenhum ARN, endpoint ou DNS de outro repositório aparece em código ou manifesto: o contrato entre repositórios é
o Parameter Store.

## Kubernetes

Tudo no namespace **`execution`** ([`k8s/application/`](k8s/application/)): ServiceAccount própria sem token
montado, ConfigMap, três Secrets gerados pelo `cd.yml` (New Relic, a **chave pública** do JWT e a credencial de
serviço do catálogo), Deployment com HPA, Service `ClusterIP` e o `TargetGroupBinding` que registra os pods no
target group do ALB interno.

**Só a chave pública:** este serviço valida o JWT e nunca o assina. Ela é montada por volume, e o pod declara
`fsGroup` — sem isso o Secret nasce `root:root` e o processo não-root não lê a chave, com uma mensagem de erro
que culpa o caminho do arquivo em vez da permissão.

O namespace tem a label `elbv2.k8s.aws/pod-readiness-gate-inject: enabled`: o pod só fica pronto quando o alvo
dele está `healthy` no target group, e o `preStop` segura o encerramento enquanto o ALB drena o alvo.

A entrada é sempre pelo API Gateway; `/q/*` (health, métricas, OpenAPI) só dentro da VPC.
