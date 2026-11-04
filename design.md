# design.md
> Nize — Version 0.2.5 (MVP web) — 2026-11-04

## Architecture Overview
Monorepo com dois módulos independentes: `backend` (API REST) e `frontend` (SPA). **Toda regra de cálculo vive só no back-end** (totais, saldo, posição nos fechamentos, parcelas, carga do mês, saldo próprio, porcentagens). O front apenas exibe o que a API devolve e nunca recalcula valores. Como back (Java) e front (TypeScript) não compartilham código, o "pacote de regras compartilhado" do PDF vira o pacote Java `br.com.nize.rules`, puro (sem Spring, sem JPA), testado antes do código.

**Stack**
- Back-end: Java 25, Spring Boot **4.0.9** (Web, Security, Data JPA, Validation), Maven. Toolchain local: Temurin 25 + Maven Wrapper (Maven 3.10.0).
- Banco: MySQL 8.4 no ambiente local, MySQL 8.x gerenciado (Aiven, plano gratuito, TLS) em produção; H2 em memória (modo MySQL) nos testes. Migrações com Flyway, com SQL compatível com os dois.
- Front-end: React + TypeScript + Vite, Tailwind CSS, TanStack Query, React Router, biblioteca de gráficos (Recharts) para o panorama anual.
- Testes: JUnit 5 + AssertJ (regras e serviços), Spring Boot Test/MockMvc (API e isolamento), Vitest + Testing Library (front).

**Deployment** (provisório, gratuito, só para testes; D-13): front-end no **Cloudflare Pages** (projeto `nize`); back-end no **Render** (serviço `nize-api`, plano gratuito) a partir de uma imagem Docker publicada no GHCR (`ghcr.io/gabriellaferr00/nize-backend`, pública); banco **Aiven for MySQL** (plano gratuito: 1 GB RAM, 1 GB disco, `max_connections` 76, sem IP fixo, pode ser desligado por inatividade). Pipeline no GitHub Actions; ver "CI/CD" e `docs/ci-cd.md`.

```
 GitHub Actions ──► GHCR (imagem verificada pelo trivy) ──► Render (nize-api, perfil prod)
       │  Flyway migrate (usuário de migração, TLS)               │ usuário de runtime (só DML), TLS
       ▼                                                          ▼
 Aiven MySQL ◄────────────────────────────────────────────────────┘
 Navegador ──► Cloudflare Pages (SPA + Function /api/* ──► Render)
```

## System Diagram
```
 Navegador (SPA React)
        |  HTTPS, JSON, JWT no header Authorization
        v
 +------------------ backend (Spring Boot) ------------------+
 |  api (controllers, DTOs)                                   |
 |    -> servico (casos de uso, transações, usuario_id)       |
 |         -> regras (PURO: parcelas, totais, quinzena,       |
 |                    resumos, carga do mês)                  |
 |         -> repositorio (JPA)  -> MySQL / H2                |
 +------------------------------------------------------------+
```

## Decisões de design
| # | Decisão | Motivo |
|---|---|---|
| D-1 | Regras só no back; front só exibe | PDF + confirmação da usuária |
| D-2 | Dinheiro em `BIGINT` centavos; sem `double`/`BigDecimal` | NFR-001 |
| D-3 | Mês como `CHAR(7)` `AAAA-MM` | PDF |
| D-4 | Totais, saldos e % calculados na leitura, nunca persistidos | NFR-002 |
| D-5 | Autenticação própria: e-mail+senha (BCrypt) e JWT de curta duração | "Autenticação gerenciada pelo backend" |
| D-6 | Isolamento: toda consulta recebe `usuario_id` do token, nunca da requisição; acesso a linha de outra usuária devolve 404 | REQ-004 |
| D-7 | Carga do mês idempotente via tabela `mes_carregado` e chave única de parcela | REQ-022 |
| D-8 | Parcela identificada por `(grupo_parcela_id, parcela_atual)` único | evitar duplicar parcelas geradas |
| D-10 | **Todo identificador técnico em inglês**: pacotes, classes, métodos, tabelas, colunas, rotas e JSON (ver "Nomenclatura em inglês"). Os termos em português nas seções abaixo são do domínio do PDF e mapeiam para os nomes em inglês | Decisão da usuária, 2026-11-01 |
| D-12 | Sessão no front: token JWT guardado em `localStorage` (`nize.auth`, com a hora de expiração); ao expirar ou receber 401 a sessão é limpa e a usuária volta a `/sign-in`. Risco aceito no MVP: XSS poderia ler o token | Decisão da usuária, 2026-11-01 |
| D-11 | O front tem i18n com inglês e português; a usuária escolhe o idioma da interface (REQ-046). Só texto exibido é traduzido; valores e regras continuam vindo da API | Decisão da usuária, 2026-11-01 |
| D-13 | Hospedagem de teste: Cloudflare Pages (front), Render via imagem Docker do GHCR (back), Aiven for MySQL (banco). O Render puxa a imagem já verificada pelo trivy e não gasta minutos de build; o deploy é disparado pelo deploy hook com `imgURL` (digest) | Decisão da usuária, 2026-11-01 (custo zero) |
| D-14 | Perfil `prod` com `spring.flyway.enabled=false`: as migrações rodam num passo do pipeline, antes do deploy, com o usuário de migração (DDL). A aplicação usa outro usuário só com `SELECT, INSERT, UPDATE, DELETE`, TLS obrigatório na URL (`DatabaseTlsGuard`) e pool pequeno (5). Perfis `h2` e `local` continuam migrando no boot. Migrações seguem expand/contract (compatíveis com a versão anterior do app) | NFR-009, decisão da usuária |
| D-15 | O front continua chamando `/api` na própria origem. No Cloudflare Pages, uma Pages Function (`frontend/functions/api/[[path]].ts`) repassa `/api/*` para o Render (`API_ORIGIN`), trocando `X-Forwarded-For` pelo IP real (`CF-Connecting-IP`). Sem CORS no back; CSP com `connect-src 'self'` | Decisão da usuária, 2026-11-01 ("seguir o que tem hoje") |
| D-16 | Limite de tentativas (REQ-047) em memória, por chave (`login:email`, `login:ip`, ...), janela fixa; limites configuráveis em `nize.rate-limit.*`. Vale para uma instância só (o plano gratuito tem uma); reinício zera os contadores. IP vem de `X-Forwarded-For` no perfil `prod` (`server.forward-headers-strategy: framework`); quem chama o Render direto pode forjar o IP, por isso o limite por e-mail é o controle principal | REQ-047 |
| D-17 | `EmailSender` no perfil `prod` é `DisabledEmailSender` (não envia e não loga e-mail nem token); `LoggingEmailSender` só fora de `prod` | NFR-008, REQ-003 |
| D-18 | Healthcheck por Spring Boot Actuator, expondo só `GET /actuator/health` (sem detalhes, sem autenticação) | Smoke test e `HEALTHCHECK` do Docker |
| D-19 | `GET /api/years/{year}/overview` faz a carga do mês (`MonthLoadService.open`, idempotente) de cada mês do ano até o mês vigente, em ordem (janeiro primeiro, para cada mês copiar do anterior); ano passado → os 12 meses; ano futuro → nenhum. Meses depois do vigente só mostram o que já existe. Cada mês devolvido bate com `GET /api/months/{m}` (mesmos totais e saldo) | Decisão da usuária, 2026-11-04 |
| D-9 | Posição por quinzena usa "data efetiva" (pela forma de pagamento, não pelo tipo): Pix = `data`; crédito com cartão = dia de vencimento do cartão dentro do mês do lançamento; crédito sem cartão = `data` (confirmado 2026-11-01) | REQ-032 |

## Nomenclatura em inglês
As seções de modelo de dados, regras e API abaixo foram escritas em português (termos do PDF). Na implementação valem os nomes em inglês desta tabela; onde a spec diz `lancamento`, leia `expense`, etc. Mudar um nome aqui exige atualizar esta tabela primeiro.

| Português (spec/PDF) | Inglês (código, banco, API) |
|---|---|
| usuario / usuario_id | `app_user` / `user_id` |
| configuracao / ano_vigente / parcelas_automaticas | `settings` / `current_year` / `auto_installments` |
| fechamentos_ativos / dia_fechamento / dia | `closings_enabled` / `closing_day` / `day_number` |
| ano (tabela) / ano (coluna) / mes | `user_year` / `year_number` / `month_ref` (`YYYY-MM`). `year` e `month` são palavras reservadas no H2, por isso não são usados como nome de tabela ou coluna; a data do lançamento é `expense_date` e a da entrada, `income_date` |
| token_recuperacao_senha | `password_reset_token` |
| categoria / cartao / pessoa | `category` / `card` / `person` |
| lancamento (tipo `fixo`, `cartao`) | `expense` (type `FIXED`, `CARD`) |
| entrada | `income` |
| forma_pagamento (`pix`, `credito`) | `payment_method` (`PIX`, `CREDIT`) |
| valor_centavos | `amount_cents` |
| dia_vencimento | `due_day` |
| pago / ativa / ativo | `paid` / `active` |
| parcela_atual / parcelas_total / grupo_parcela_id | `installment_number` / `installment_count` / `installment_group_id` |
| mes_carregado | `loaded_month` |
| pacotes `regras`, `dominio`, `repositorio`, `servico`, `api`, `seguranca` | `rules`, `domain`, `repository`, `service`, `api`, `security` |
| EnviadorDeEmail | `EmailSender` |
| rotas `/cadastro`, `/recuperar-senha`, `/redefinir-senha` | `/register`, `/forgot-password`, `/reset-password` |
| rotas `/anos`, `/categorias`, `/cartoes`, `/pessoas` | `/years`, `/categories`, `/cards`, `/people` |
| rotas `/meses`, `/lancamentos`, `/entradas`, `/panorama` | `/months`, `/expenses`, `/incomes`, `/overview` |
| `/me/onboarding-visto`, `/configuracao` | `/me/onboarding-seen`, `/settings` |
| rotas do front `/entrar`, `/criar-conta`, `/recuperar-senha` | `/sign-in`, `/sign-up`, `/forgot-password` |
| tela de redefinição (nova; recebe `?token=`) | `/reset-password` |
| rotas do front `/mes/:aaaa-mm`, `/panorama/:ano` | `/month/:yyyy-mm`, `/overview/:year` |
| rotas do front `/categorias`, `/cartoes`, `/pessoas`, `/configuracoes` | `/categories`, `/cards`, `/people`, `/settings` |

Nomes de classes de regras: `Installments`, `InstallmentIndicator`, `MonthLoad`, `MonthTotals`, `ClosingPositions`, `OwnBalance`, `CategorySummary`, `PaymentSummary` (forma e cartão), `ThirdPartySummary` (mesma ordem das regras em português). Textos de erro e de interface são tratados pelo i18n (D-11); a API devolve códigos de erro estáveis em inglês.

## Data Models
Convenções: todas as tabelas têm `usuario_id BIGINT NOT NULL` (FK `usuario.id`) e índice por ele. `ano` é `SMALLINT`. Datas são `DATE`.

### usuario
| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGINT | PK, auto | REQ-001 |
| nome | VARCHAR(120) | NOT NULL | |
| email | VARCHAR(255) | NOT NULL, UNIQUE | REQ-001 |
| senha_hash | VARCHAR(100) | NOT NULL | BCrypt, NFR-005 |
| onboarding_visto | BOOLEAN | NOT NULL, default false | REQ-005 |
| criado_em | TIMESTAMP | NOT NULL | |

### token_recuperacao_senha
| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGINT | PK | REQ-003 |
| usuario_id | BIGINT | NOT NULL, FK | |
| token_hash | VARCHAR(64) | NOT NULL, UNIQUE | só o hash é guardado |
| expira_em | TIMESTAMP | NOT NULL | |
| usado_em | TIMESTAMP | NULL | uso único |

### configuracao
| Field | Type | Constraints | Notes |
|---|---|---|---|
| usuario_id | BIGINT | PK, FK | uma linha por usuária |
| ano_vigente | SMALLINT | NOT NULL | REQ-006 |
| parcelas_automaticas | BOOLEAN | NOT NULL, default true | REQ-009 (padrão a confirmar) |
| fechamentos_ativos | BOOLEAN | NOT NULL, default true | REQ-048 |

### dia_fechamento
| Field | Type | Constraints | Notes |
|---|---|---|---|
| usuario_id | BIGINT | PK (com dia), FK | REQ-048 |
| dia | SMALLINT | PK (com usuario_id), CHECK 1–31 | dia além do fim do mês vale como último dia; conta nova recebe 15 e 31 (a migração também, para as contas existentes) |

### ano
| Field | Type | Constraints | Notes |
|---|---|---|---|
| usuario_id | BIGINT | PK parte 1, FK | REQ-007 |
| ano | SMALLINT | PK parte 2 | anos existentes da usuária |

### categoria
| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGINT | PK | REQ-010/011/012 |
| usuario_id | BIGINT | NOT NULL, FK | |
| ano | SMALLINT | NOT NULL | lista por ano, copiada do ano anterior |
| nome | VARCHAR(80) | NOT NULL | UNIQUE (usuario_id, ano, nome) |
| descricao | VARCHAR(255) | NULL | |
| ativa | BOOLEAN | NOT NULL, default true | |

### cartao
| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGINT | PK | REQ-013 |
| usuario_id | BIGINT | NOT NULL, FK | |
| ano | SMALLINT | NOT NULL | |
| nome | VARCHAR(80) | NOT NULL | UNIQUE (usuario_id, ano, nome) |
| dia_vencimento | TINYINT | NOT NULL, CHECK 1..31 | define a quinzena do crédito |
| ativo | BOOLEAN | NOT NULL, default true | |

### pessoa
| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGINT | PK | REQ-014 |
| usuario_id | BIGINT | NOT NULL, FK | |
| ano | SMALLINT | NOT NULL | |
| nome | VARCHAR(80) | NOT NULL | UNIQUE (usuario_id, ano, nome) |
| ativa | BOOLEAN | NOT NULL, default true | |

### lancamento
| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGINT | PK | REQ-015..023 |
| usuario_id | BIGINT | NOT NULL, FK | |
| mes | CHAR(7) | NOT NULL | `AAAA-MM` |
| nome | VARCHAR(120) | NOT NULL | |
| categoria_id | BIGINT | NOT NULL, FK categoria | |
| valor_centavos | BIGINT | NOT NULL, CHECK > 0 | |
| tipo | VARCHAR(10) | NOT NULL, CHECK in ('fixo','cartao') | |
| forma_pagamento | VARCHAR(10) | NOT NULL, CHECK in ('pix','credito') | independente do tipo: fixo pode ser pix ou crédito (ex.: assinatura no cartão); cartao é sempre crédito |
| cartao_id | BIGINT | NULL, FK cartao | só crédito; NULL = "Sem cartão" |
| pessoa_id | BIGINT | NULL, FK pessoa | NOT NULL = gasto de terceiro (REQ-017) |
| pago | BOOLEAN | NOT NULL, default false | só controle (REQ-033) |
| data | DATE | NOT NULL | obrigatória (PDF); deve estar em `mes` |
| parcela_atual | SMALLINT | NULL | |
| parcelas_total | SMALLINT | NULL | CHECK parcela_atual ≤ parcelas_total |
| grupo_parcela_id | CHAR(36) | NULL | UUID comum às parcelas de uma compra |

Restrições: UNIQUE (`grupo_parcela_id`, `parcela_atual`) onde não nulos; `parcela_atual` e `parcelas_total` ambos nulos ou ambos preenchidos.

Regras de consistência do lançamento (validadas no serviço; as CHECK do banco cobrem as simples):
- `tipo = cartao` ⇒ `forma_pagamento = credito`. `tipo = fixo` ⇒ `forma_pagamento` pix **ou** crédito.
- `cartao_id` só com `forma_pagamento = credito`; crédito sem `cartao_id` é "Sem cartão".
- Parcelas (`parcela_atual`/`parcelas_total`/`grupo_parcela_id`) só em `tipo = cartao`.
- Um fixo no crédito continua na lista de Fixos e entra em `totalFixos`; também entra em "total de crédito" e no resumo por cartão.

### entrada
| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGINT | PK | REQ-024..026 |
| usuario_id | BIGINT | NOT NULL, FK | |
| mes | CHAR(7) | NOT NULL | |
| nome | VARCHAR(120) | NOT NULL | |
| data | DATE | NOT NULL | define dia 15/30 |
| valor_centavos | BIGINT | NOT NULL, CHECK > 0 | |
| pessoa_id | BIGINT | NULL, FK pessoa | NOT NULL = reembolso de terceiro |
| parcela_atual | SMALLINT | NULL | ex.: 1/3 |
| parcelas_total | SMALLINT | NULL | |
| grupo_parcela_id | CHAR(36) | NULL | UNIQUE com parcela_atual |

### mes_carregado
| Field | Type | Constraints | Notes |
|---|---|---|---|
| usuario_id | BIGINT | PK parte 1, FK | REQ-022 |
| mes | CHAR(7) | PK parte 2 | marca que a carga já rodou |
| carregado_em | TIMESTAMP | NOT NULL | |

**Relationships**: usuario 1—N de todas as tabelas; lancamento N—1 categoria, cartao (opcional), pessoa (opcional); entrada N—1 pessoa (opcional). Tabelas de fase 2 (`aporte_investimento`, `meta`, `aporte_meta`) **não** são criadas no MVP.

**Valores derivados (não persistidos, NFR-002)**: total de gastos, total de fixos, saldo, posições nos fechamentos, saldo próprio, resumos e porcentagens.

## Categorias de fábrica
Semeadas em `category` para cada ano novo (REQ-010). A usuária pode renomear, desativar, criar e excluir (REQ-011/012). Os nomes seguem a lista do produto; as descrições têm texto próprio do Nize (REQ-045). O código guarda esta lista em `DefaultCategories`; se mudar aqui, mude lá (e vice-versa).

| # | Nome | Descrição |
|---|---|---|
| 1 | Mercado | Despensa e geladeira cheias: feira, supermercado e itens de limpeza do lar. |
| 2 | Necessidades | Itens do dia a dia que não podem faltar: farmácia, higiene pessoal e pequenas coisas da casa. |
| 3 | Eletrônicos | Aparelhos e acessórios tecnológicos, além de consertos e manutenção deles. |
| 4 | Assinaturas | Serviços cobrados todo mês ou todo ano, como streaming, música e aplicativos. |
| 5 | Roupa | Roupas, calçados e acessórios para vestir, em loja física ou online. |
| 6 | Beleza | Cuidados com a aparência: salão, unhas, maquiagem e produtos para pele e cabelo. |
| 7 | Presentes | Mimos e lembranças para quem você gosta, em aniversários e datas especiais. |
| 8 | Saúde | Cuidados com o corpo e a mente: academia, suplementos, consultas e exames. |
| 9 | Despesas eventuais | Imprevistos e gastos que não se repetem todo mês: conserto do carro, veterinário, consulta fora do plano. |
| 10 | Desenvolvimento | Investimento em aprender e crescer: cursos, livros, agendas e materiais de estudo. |
| 11 | Uber/transporte | Deslocamentos: aplicativos de corrida, transporte público e combustível. |
| 12 | IFood/restaurante | Refeições fora de casa: restaurantes, lanchonetes e pedidos por aplicativo. |
| 13 | Lazer | Diversão e encontros: festas, cinema, teatro, passeios e comemorações. |
| 14 | Aluguel | Valor mensal da moradia: aluguel ou prestação do imóvel. |
| 15 | Contas | Contas da casa que chegam todo mês: internet, água, luz, condomínio e IPTU. |

## Regras de cálculo (pacote `br.com.nize.rules`)
| Regra | Definição | REQ |
|---|---|---|
| totalGastos | Σ valor de lancamento do mês (fixo + cartão) | REQ-027 |
| totalFixos | Σ valor de lancamento tipo fixo | REQ-028 |
| saldo | Σ entradas − totalGastos | REQ-029 |
| dataEfetiva | Pix: `data`; crédito com cartão: dia `min(dia_vencimento, último dia do mês)` do `mes` do lançamento; crédito sem cartão: `data` | REQ-032 |
| posições(dias) | para cada dia de fechamento d: limite = `min(d, último dia do mês)`; posição = Σ entradas com `data.dia ≤ limite` − Σ gastos com `dataEfetiva.dia ≤ limite`. Em ordem crescente de limite; dias que caem no mesmo limite (30 e 31 em fevereiro) dão uma posição só. Fechamentos desligados ou sem dias → nenhuma posição | REQ-030/031/048 |
| saldoPróprio | saldo + Σ gastos com pessoa_id − Σ entradas com pessoa_id | REQ-034 |
| dividirParcelas(total, n) | base = total ÷ n (inteira); n−1 parcelas = base; última = total − base×(n−1) | REQ-020 |
| parcelasPorValor(valor, n) | n parcelas iguais | REQ-018 |
| resumoPorCategoria | Σ por categoria; % = valor ÷ totalGastos (2 casas, HALF_UP só na exibição; aritmética decimal exata, nunca float; na API é um número com 2 casas, ex.: `23.86`) | REQ-035 |
| resumoPorForma / PorCartão | Σ por forma de pagamento; Σ por cartão com grupo "Sem cartão" | REQ-036/037 |
| resumoPorPessoa | Σ gastos com pessoa_id, por pessoa e por cartão | REQ-038 |
| cargaDoMês(mesAnterior, mês) | copia os fixos (pago=false; gastos avulsos não são copiados); para cada parcela N/M do mês anterior com N < M cria a parcela N+1, **se a permissão de parcelas automáticas está ligada**, e se `(grupo, N+1)` ainda não existe (vale também para parcelas lançadas à mão, que recebem grupo próprio ao serem criadas); idem entradas parceladas; só se o mês não está em `loaded_month`. Referências (categoria, cartão, pessoa) trocam de ano pelo nome; item cuja categoria não existe no ano novo é ignorado | REQ-022 |

Porcentagem é o único valor não inteiro e só existe na resposta da API/exibição; nunca é usada para somar.

## API / Interface Design
REST JSON sob `/api`. Todos os endpoints exceto `/api/auth/**` exigem `Authorization: Bearer <JWT>`. `usuario_id` sempre vem do token. Linha de outra usuária → 404.

| Method | Path | Auth | REQ | Description |
|---|---|---|---|---|
| POST | /api/auth/cadastro | não | REQ-001 | cria conta |
| POST | /api/auth/login | não | REQ-002 | devolve JWT |
| POST | /api/auth/recuperar-senha | não | REQ-003 | gera token de recuperação |
| POST | /api/auth/redefinir-senha | não | REQ-003 | usa token e define nova senha |
| GET | /api/me | sim | REQ-005 | dados da usuária e `onboarding_visto` |
| POST | /api/me/onboarding-visto | sim | REQ-005 | marca onboarding como visto |
| GET | /api/configuracao | sim | REQ-006, REQ-009, REQ-048 | ano vigente, parcelas automáticas, `closingsEnabled` e `closingDays` (crescente) |
| PUT | /api/configuracao | sim | REQ-006, REQ-009, REQ-048 | altera configuração; `closingDays` de 1 a 31, sem repetir (senão 400 `VALIDATION_ERROR`) |
| GET | /api/anos | sim | REQ-008 | anos existentes |
| POST | /api/anos/{ano} | sim | REQ-007 | cria ano copiando estrutura do anterior |
| GET | /api/anos/{ano}/categorias | sim | REQ-010 | lista |
| POST | /api/anos/{ano}/categorias | sim | REQ-011 | cria |
| PUT | /api/categorias/{id} | sim | REQ-011 | renomeia, descreve, ativa/desativa |
| DELETE | /api/categorias/{id} | sim | REQ-012 | exclui; 409 com contagem se houver lançamentos |
| GET/POST | /api/anos/{ano}/cartoes | sim | REQ-013 | lista / cria |
| PUT | /api/cartoes/{id} | sim | REQ-013 | edita |
| GET/POST | /api/anos/{ano}/pessoas | sim | REQ-014 | lista / cria |
| PUT | /api/pessoas/{id} | sim | REQ-014 | edita |
| GET | /api/meses/{aaaa-mm} | sim | REQ-022, REQ-027..041 | abre o mês (dispara carga na 1ª vez) e devolve listas, totais, posições, resumos e saldo próprio |
| POST | /api/meses/{aaaa-mm}/lancamentos | sim | REQ-015..020 | cria gasto; gera parcelas se `gerarParcelas` e permissão ligada |
| PUT | /api/lancamentos/{id} | sim | REQ-023 | edita |
| PATCH | /api/lancamentos/{id}/pago | sim | REQ-015, REQ-033 | marca/desmarca pago |
| DELETE | /api/lancamentos/{id} | sim | REQ-023 | exclui |
| POST | /api/meses/{aaaa-mm}/entradas | sim | REQ-024..026 | cria entrada (parcelada ou reembolso) |
| PUT/DELETE | /api/entradas/{id} | sim | REQ-023 | edita / exclui |
| GET | /api/anos/{ano}/panorama | sim | REQ-039 | `GET /api/years/{year}/overview` → `{year, months: [{month, totalExpensesCents, totalIncomeCents, balanceCents}]}` (12 meses, janeiro primeiro; mesma regra `MonthTotals` do mês); faz a carga dos meses até o vigente (D-19); ano que a usuária não tem → 404 |
| GET | /actuator/health | não | D-18 | `{"status":"UP"}`; usado pelo Docker e pelo smoke test |

Erros sempre no formato `{code, fields}` (`ApiExceptionHandler`), inclusive os do framework: parâmetro de tipo errado → 400 `VALIDATION_ERROR`; rota inexistente → 404 `NOT_FOUND`; método errado → 405 `METHOD_NOT_ALLOWED`; tipo de conteúdo errado → 415 `UNSUPPORTED_MEDIA_TYPE`; erro inesperado → 500 `INTERNAL_ERROR` (causa só no log do servidor, nunca na resposta).

Limite de tentativas (REQ-047): os quatro `POST /api/auth/**` devolvem 429 `{code: "TOO_MANY_ATTEMPTS", fields: {}}` com `Retry-After` (segundos) quando a chave está bloqueada.

## Frontend
| Rota | Tela | REQ |
|---|---|---|
| /entrar, /criar-conta, /recuperar-senha | Entrar e criar conta | REQ-001..003 |
| /mes/:aaaa-mm | Mês (principal) + janela "Novo lançamento" | REQ-041, REQ-042 |
| /panorama/:ano | Panorama anual | REQ-039 |
| /categorias, /cartoes, /pessoas | Cadastros | REQ-044 |
| /configuracoes | Ano vigente, parcelas automáticas, fechamentos (ligar/desligar, dias) | REQ-006, REQ-009, REQ-048 |

Navegação: menu lateral (≥ 768px) e barra inferior (< 768px) — REQ-043. Identidade visual própria — REQ-045.

## File Structure
```
nize/
├── CLAUDE.md  CONTEXT.md  requirements.md  design.md  tasks.md
├── .env.example  .gitleaks.toml  (nomes das variáveis, sem valores; regras extras do gitleaks)
├── .semgrep/nize.yml            (regras próprias: XSS, hash fraco, Random para token, userId em DTO)
├── .zap/rules.tsv               (o que o ZAP baseline trata como falha/aviso)
├── .github/
│   ├── dependabot.yml           (Maven, npm, GitHub Actions, Docker)
│   └── workflows/  backend.yml, frontend.yml, integration-tests.yml, security.yml (um por tema)
├── docs/ci-cd.md                (fluxo, segredos, rollback, rotação, achados)
├── docs/cobertura-cenarios.md   (Phase 8: cenários do PDF → testes, endpoints × isolamento, carga NFR-007)
├── scripts/ci/                  (scripts do pipeline: mysql-setup.sh, check-schema.sql,
│                                 check-runtime-privileges.sh, api-smoke.sh, dast-checks.sh,
│                                 frontend-smoke.sh, check-logs.sh, check_pii.py, check-forbidden-files.sh,
│                                 render-deploy.sh, wait-for-workflows.sh, api-flow.sh)
├── pom.xml                      (agregador Maven; opcional)
├── backend/              (estrutura no padrão do Spring Initializr)
│   ├── pom.xml  mvnw  mvnw.cmd  .mvn/wrapper/  .gitignore  .gitattributes  HELP.md
│   ├── Dockerfile  .dockerignore  (multi-stage; D-13)
│   └── src/
│       ├── main/java/br/com/nize/
│       │   ├── NizeApplication.java
│       │   ├── rules/           (puro: sem Spring/JPA)
│       │   ├── domain/          (entidades JPA)
│       │   ├── repository/
│       │   ├── service/
│       │   ├── api/             (controllers e DTOs)
│       │   └── security/        (JWT, user_id do token)
│       │                    (panorama: service/OverviewService, api/OverviewController e OverviewResponse)
│       ├── main/resources/  application.yml, application-local.yml, application-h2.yml, application-prod.yml
│       ├── main/resources/db/migration/   (Flyway)
│       └── test/java/br/com/nize/  (rules/, service/, api/; api/PdfScenariosTest = cenários do PDF com
│                                    @DisplayName; api/MonthResponseTimeTest = carga, tag `performance`,
│                                    fora da suíte normal: `-Dtest.excludedGroups=none`;
│                                    panorama: api/OverviewApiTest)
└── frontend/
    ├── package.json  vite.config.ts  playwright.config.ts
    ├── public/  _redirects (fallback da SPA)  _headers (CSP, HSTS...)
    ├── functions/api/[[path]].ts  (Pages Function: proxy /api → Render; D-15)
    ├── e2e/  (Playwright, dados sintéticos)
    └── src/ (pages/, components/, api/, lib/, styles/, i18n/ com en.json e pt.json, test/)
        tela do mês (Phase 6): api/monthApi.ts, api/incomesApi.ts; lib/useMonth.ts, lib/format.ts;
        components/MonthHeader.tsx, MonthPicker.tsx, EntryLists.tsx, SummaryPanel.tsx, IncomeFormDialog.tsx, Money.tsx;
        testes pages/month.test.tsx, lib/month.test.ts
        panorama (Phase 7): pages/OverviewPage.tsx, components/OverviewChart.tsx (Recharts, carregado sob demanda),
        api/overviewApi.ts, pages/overview.test.tsx
```
groupId `br.com.nize`, artifactId `nize-backend`, pacote raiz `br.com.nize`.

## Security Design
- Senha: BCrypt; nunca logada nem devolvida (NFR-005).
- JWT assinado, curta duração; segredo por variável de ambiente, nunca no repositório.
- `usuario_id` extraído do token no servidor; DTOs de entrada não aceitam `usuario_id`.
- Todas as consultas filtradas por `usuario_id` (NFR-004); suíte de isolamento obrigatória.
- Recuperação de senha: token aleatório, só o hash é guardado, uso único, expira.
- CORS restrito ao domínio do front. Mensagens de login não revelam se o e-mail existe.
- Validação com Bean Validation em todos os DTOs.
- Limite de tentativas em `/api/auth/**` (REQ-047, D-16): `security.AttemptLimiter` + `TooManyAttemptsException` → 429.
- Segredos, senhas e tokens fora de logs e respostas (NFR-008); `DisabledEmailSender` em `prod` (D-17).
- Banco de produção: usuário de runtime só DML, migração com outro usuário, TLS obrigatório (`security.DatabaseTlsGuard`, perfil `prod`) — NFR-009, D-14.
- Logout é só no cliente (o JWT vale até expirar, 1h): risco residual aceito no MVP.
- Testes de segurança: `ArchitectureTest` (dinheiro, `rules` puro, DTO de entrada sem `userId`, repositórios de dados com `userId`) e `IsolationCoverageTest` (todo controller de dados tem teste que estende `IsolationTestBase`).

## CI/CD
GitHub Actions (detalhes em `docs/ci-cd.md`; NFR-010). **Um workflow por tema**, sem workflows aninhados; cada um dispara em push e Pull Request para a `main` só quando o seu código muda (filtro `paths`), e manualmente (`workflow_dispatch`):
- **Back** (`backend.yml`; `backend/**`): `mvnw verify` (testes de regras, API em H2, segurança, isolamento, ArchUnit; JaCoCo ≥ 80% global e em `rules`) → imagem Docker + trivy → (só `main`) push GHCR → *gate* → Environment `production` (aprovação) → Flyway no Aiven com o usuário de migração → Render (API, por digest) → smoke.
- **Front** (`frontend.yml`; `frontend/**`): `npm ci`, lint, typecheck, Vitest com cobertura ≥ 80%, build → *gate* → Cloudflare Pages: PR/branch publica preview (Environment `staging`), `main` publica produção (`production`, aprovação) → smoke.
- **Testes integrados** (`integration-tests.yml`; um job por lado, cada um só se o seu código ou o workflow de deploy desse lado mudou, para o *gate* sempre ter o que esperar): **back** — MySQL 8.4 descartável → migrações com o usuário de migração (duas vezes) → regras do esquema e privilégios do usuário de runtime → app no perfil `prod` (sem Flyway, só DML, TLS) → smoke → fluxo completo da API (`api-flow.sh`) → log sem segredos; **front** — E2E Playwright do site construído (CSP de produção) contra a API no perfil `h2` → log sem segredos.
- **Segurança** (`security.yml`; back ou front, semanal e manual): gitleaks e trufflehog no histórico, PII/arquivos proibidos, semgrep, CodeQL (Java e JS/TS), trivy no jar, npm audit, headers do front, DAST + ZAP contra o back local (perfil `h2`). Críticos/altos falham o workflow; médios/baixos viram issue (fora de PR).
- **Gate**: workflows separados não se referenciam com `needs`; o job *gate* do back e do front espera (via API do GitHub) os workflows Segurança e Testes integrados **do mesmo commit** terminarem com sucesso antes de qualquer deploy (`scripts/ci/wait-for-workflows.sh`). A aprovação de `production` só é pedida depois do gate.
- Actions fixadas por SHA e scanners por digest; `permissions: contents: read` por padrão; sem `pull_request_target`; PR de fork não recebe segredos (sem integração nem deploy de preview). PR do Dependabot também roda sem os segredos normais: não publica preview; o MySQL dos testes integrados usa a cópia de `NIZE_DB_ROOT_PASSWORD_TEST` em *Dependabot secrets*.
- **Dependabot** (`dependabot.yml`): semanal, minor/patch agrupados; não propõe Spring Boot ≥ 4.1 nem Flyway ≥ 12 (troca de versão principal da stack é decisão da usuária).

## Open Questions
- [x] ~~Lista das 15 categorias de fábrica~~ — resolvido em 2026-11-01: ver seção "Categorias de fábrica". "Mainha" não é categoria; é uma pessoa (terceiro), cadastrada em `pessoa`.
- [x] ~~Versão do Spring Boot~~ — resolvido em 2026-11-01: 4.0.9 (última 4.0.x estável; a 4.1.2 existe e pode ser adotada depois).
- [x] ~~groupId / pacote Java raiz~~ — resolvido em 2026-11-01: `br.com.nize`.
- [x] ~~Recuperação de senha~~ — resolvido em 2026-11-01: token no log em dev, atrás da interface `EmailSender`.
- [x] ~~Padrão de `parcelas_automaticas`~~ — resolvido em 2026-11-01: ligado em conta nova.
- [x] ~~Crédito sem cartão~~ — resolvido em 2026-11-01: usa a `data` do lançamento.
- [x] ~~Fixo com cartão~~ — resolvido em 2026-11-01: **pode**. Há fixos pagos no crédito (ex.: assinaturas); fixo e forma de pagamento são independentes (ver "Regras de consistência do lançamento"). Diverge do PDF; decisão da usuária.
- [x] ~~Parcela e vencimento~~ — resolvido em 2026-11-01: cada parcela pesa no mês em que aparece, com o dia de vencimento do cartão naquele mês.
- [x] ~~Hospedagem final~~ — resolvido em 2026-11-01: Cloudflare Pages + Render (imagem do GHCR) + Aiven MySQL (D-13).
- [ ] **Provedor de e-mail em produção** para a recuperação de senha (REQ-003). Até lá, `DisabledEmailSender` (D-17).
- [x] ~~Cucumber ou JUnit~~ — resolvido em 2026-11-04: JUnit 5 com nomes iguais aos cenários, sem Cucumber.
- [x] ~~Meta de desempenho NFR-007~~ — resolvido em 2026-11-04: confirmada (p95 < 500 ms com 500 lançamentos no mês).
- [x] ~~Idioma do código~~ — resolvido em 2026-11-01: tudo em inglês, inclusive banco e API; front com opção de português (D-10, D-11, REQ-046).

## Changelog
| Version | Date | Change |
|---|---|---|
| 0.1.0 | 2026-11-01 | Initial design |
| 0.1.1 | 2026-11-01 | Nomenclatura em inglês (D-10), i18n no front (D-11), Spring Boot 4.0.9, groupId `br.com.nize`, estrutura de pacotes em inglês |
| 0.1.2 | 2026-11-01 | `year` reservado no H2: tabela `user_year`, colunas `year_number` |
| 0.1.3 | 2026-11-01 | Backend no padrão Initializr: Maven Wrapper (`mvnw`), `.gitattributes`, `HELP.md` |
| 0.1.4 | 2026-11-01 | Rotas do front em inglês; estrutura do front: `pages/`, `components/`, `api/`, `styles/`, `i18n/` |
| 0.1.5 | 2026-11-01 | D-12 (token em localStorage), rota `/reset-password`, pastas `lib/` e `test/` do front |
| 0.1.6 | 2026-11-01 | Descrições das 15 categorias reescritas com texto próprio (REQ-045); lista guardada em `DefaultCategories` |
| 0.1.7 | 2026-11-01 | Fixo pode ser no crédito (tipo e forma de pagamento independentes); regras de consistência do lançamento; Open Questions da Phase 3 resolvidas |
| 0.1.8 | 2026-11-01 | `month` reservado no H2: coluna `month_ref`; data do lançamento `expense_date` |
| 0.1.9 | 2026-11-01 | Carga do mês: continua parcelas à mão (grupo próprio) e respeita a permissão de parcelas automáticas |
| 0.2.5 | 2026-11-04 | Panorama faz a carga dos meses até o vigente (D-19); Open Questions da Phase 8 resolvidas (JUnit 5, NFR-007 confirmada); Maven 3.10.0 |
| 0.2.4 | 2026-11-04 | Fechamentos opcionais (REQ-048): `settings.closings_enabled`, tabela `closing_day`, regra `ClosingPositions` (substitui `FortnightPosition`), `/api/settings` com `closingsEnabled`/`closingDays`. Diverge do PDF (15/30 fixos); decisão da usuária |
| 0.2.3 | 2026-11-04 | PR do Dependabot sem preview do front e com secret próprio para o MySQL dos testes; Dependabot ignora Spring Boot ≥ 4.1 e Flyway ≥ 12; Testes integrados também rodam quando muda `backend.yml`/`frontend.yml` (senão o *gate* falha por falta de run) |
| 0.2.2 | 2026-11-02 | Um workflow por tema (back, front, segurança, testes integrados) sem aninhamento; deploys esperam Segurança e Testes integrados do mesmo commit (job *gate*); testes integrados em dois jobs (back: MySQL + fluxo da API; front: E2E com a API em `h2`) |
| 0.2.1 | 2026-11-02 | `ci-cd.yml` também roda em Pull Request para a `main` (o `workflow_dispatch` só existe depois que o workflow está na `main`) |
| 0.2.0 | 2026-11-01 | CI/CD e hospedagem (D-13 a D-18): perfil `prod` sem Flyway no boot, usuários de banco separados, TLS, proxy `/api` no Cloudflare Pages, limite de tentativas, Actuator health, `DisabledEmailSender`; File Structure com workflows, Dockerfile, scripts e docs |
