# fiap-fase4-execution-service

Execution Service da Fase 4 do Tech Challenge (FIAP SOAT): fila de diagnóstico e reparo por filial, em
DynamoDB. Quarkus.

> Em construção: por enquanto o `GET /execution/v1/info` e o primeiro passo da Saga (ver *Mensagens*).
> Arquitetura em
> [fiap-fase4-os-service/docs/arquitetura](https://github.com/CleytonOngaratto/fiap-fase4-os-service/tree/main/docs/arquitetura).

## Mensagens

| Consome | De onde | O que faz |
|---|---|---|
| `StartDiagnosis` | `execution-commands.fifo` (fila deste serviço) | registra o início do diagnóstico e responde com `DiagnosisStarted` |

| Publica | Para onde | Quando |
|---|---|---|
| `DiagnosisStarted` | `execution-events.fifo` (tópico da plataforma) | ao consumir um `StartDiagnosis` |

O envelope, os atributos e o significado de cada campo estão no
[catálogo de mensagens](https://github.com/CleytonOngaratto/fiap-fase4-os-service/blob/main/docs/arquitetura/catalogo-de-mensagens.md).
Comando de tipo desconhecido é registrado e descartado — deixá-lo na fila travaria todas as mensagens da mesma
ordem de serviço, porque a fila é FIFO. Qualquer erro no processamento **não** apaga a mensagem: ela volta, esgota
as tentativas e para na fila de descarte.

### Simplificação assumida neste bloco

Este serviço publica **direto no tópico, sem outbox**. O outbox dele depende da tabela no DynamoDB, que entra no
bloco do domínio deste serviço — até lá, "gravar o estado" e "publicar o evento" não são a mesma transação, porque
ainda não existe estado a gravar.

O que segura a reentrega enquanto isso: o identificador do evento é **determinístico**, derivado do comando que o
causou (`DiagnosisStarted:<messageId do comando>`). O mesmo comando reentregue produz exatamente o mesmo
identificador, que serve de chave de deduplicação na publicação — e, fora da janela de deduplicação, a
idempotência por estado no consumidor do OS absorve a repetição. O raciocínio completo está no
[ADR-006](https://github.com/CleytonOngaratto/fiap-fase4-os-service/blob/main/docs/arquitetura/adr/ADR-006-mensageria.md).

## Deploy

| Workflow | Gatilho | O que faz |
|---|---|---|
| [`maven-ci.yml`](.github/workflows/maven-ci.yml) | PR e `push` na `main` | build, testes, JaCoCo, análise no SonarCloud (`build` e `SonarCloud Code Analysis` são checks obrigatórios) e Trivy |
| [`terraform.yml`](.github/workflows/terraform.yml) | PR → `fmt-validate` (check obrigatório) + `plan` · `push` na `main` com mudança em `terraform/` → `apply` · `workflow_dispatch` → `plan`/`apply`/`destroy` | a infraestrutura do serviço, em [`terraform/`](terraform/): target group `ip` e a regra `/execution/v1/*` no ALB interno da plataforma |
| [`cd.yml`](.github/workflows/cd.yml) | `push` na `main` (fora `*.md`, `docs/` e `terraform/`) e `workflow_dispatch` | testes → imagem no ECR (tag = SHA do commit) → manifestos no EKS → smoke: alvos `healthy` no target group e, com a borda de pé, `GET /execution/v1/info` → **200** pelo API Gateway |

**O merge aplica.** Um merge que mude `terraform/` cria ou altera recursos na AWS — só com a sessão do laboratório
ligada e com o teardown na mesma sessão. Num merge com código e `terraform/` os dois workflows rodam juntos: o
`cd.yml` espera o target group no SSM (até 25 min) e falha dizendo quem publica.

## Contrato no SSM

| Lê | De quem |
|---|---|
| `/fase4/vpc/id`, `/fase4/alb/listener-arn` | plataforma ([fiap-fase4-infra-k8s](https://github.com/CleytonOngaratto/fiap-fase4-infra-k8s)), no `terraform/` |
| `/fase4/ecr/execution/repo-url`, `/fase4/eks/cluster-name`, `/fase4/alb/sg-id`, `/fase4/sns/execution-events/topic-arn` | plataforma, no `cd.yml` |
| `/fase4/newrelic/license-key` | bootstrap manual (sobrevive ao teardown) |

| Publica | Conteúdo |
|---|---|
| `/fase4/execution/alb/target-group-arn` | target group que o `TargetGroupBinding` liga aos pods |
| `/fase4/execution/sqs/commands-queue-url` | fila de comandos deste serviço; **o orquestrador do OS a lê** para enviar `StartDiagnosis` |

## Kubernetes

Tudo no namespace **`execution`** ([`k8s/application/`](k8s/application/)): ServiceAccount própria sem token
montado, ConfigMap, o Secret do New Relic gerado pelo `cd.yml` (só neste namespace), Deployment com HPA, Service
`ClusterIP` e o `TargetGroupBinding` que registra os pods no target group do ALB interno.

O namespace tem a label `elbv2.k8s.aws/pod-readiness-gate-inject: enabled`: o pod só fica pronto quando o alvo dele
está `healthy` no target group, e o `preStop` segura o encerramento enquanto o ALB drena o alvo.

A entrada é sempre pelo API Gateway; `/q/*` (health, métricas, OpenAPI) só dentro da VPC.
