# design.md
> Nize — Version 0.1.9 (MVP web) — 2026-11-01

## Architecture Overview
Monorepo com dois módulos independentes: `backend` (API REST) e `frontend` (SPA). **Toda regra de cálculo vive só no back-end** (totais, saldo, posição por quinzena, parcelas, carga do mês, saldo próprio, porcentagens). O front apenas exibe o que a API devolve e nunca recalcula valores. Como back (Java) e front (TypeScript) não compartilham código, o "pacote de regras compartilhado" do PDF vira o pacote Java `br.com.nize.rules`, puro (sem Spring, sem JPA), testado antes do código.

**Stack**
- Back-end: Java 25, Spring Boot **4.0.9** (Web, Security, Data JPA, Validation), Maven. Toolchain local: Temurin 25 + Maven 3.9.16 em `~/tools`.
- Banco: MySQL no ambiente local; H2 em memória (modo MySQL) no perfil de teste/nuvem por enquanto. Migrações com Flyway, com SQL compatível com os dois.
- Front-end: React + TypeScript + Vite, Tailwind CSS, TanStack Query, React Router, biblioteca de gráficos (Recharts) para o panorama anual.
- Testes: JUnit 5 + AssertJ (regras e serviços), Spring Boot Test/MockMvc (API e isolamento), Vitest + Testing Library (front).

**Deployment** (provisório, gratuito, só para testes): back-end em Render (plano gratuito, Docker); front-end em Vercel/Netlify/Cloudflare Pages; banco H2 em memória (dados somem a cada reinício). Decisão final em aberto.

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
| D-9 | Posição por quinzena usa "data efetiva" (pela forma de pagamento, não pelo tipo): Pix = `data`; crédito com cartão = dia de vencimento do cartão dentro do mês do lançamento; crédito sem cartão = `data` (confirmado 2026-11-01) | REQ-032 |

## Nomenclatura em inglês
As seções de modelo de dados, regras e API abaixo foram escritas em português (termos do PDF). Na implementação valem os nomes em inglês desta tabela; onde a spec diz `lancamento`, leia `expense`, etc. Mudar um nome aqui exige atualizar esta tabela primeiro.

| Português (spec/PDF) | Inglês (código, banco, API) |
|---|---|
| usuario / usuario_id | `app_user` / `user_id` |
| configuracao / ano_vigente / parcelas_automaticas | `settings` / `current_year` / `auto_installments` |
| ano (tabela) / ano (coluna) / mes | `user_year` / `year_number` / `month_ref` (`YYYY-MM`). `year` e `month` são palavras reservadas no H2, por isso não são usados como nome de tabela ou coluna; a data do lançamento é `expense_date` |
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

Nomes de classes de regras: `Installments`, `InstallmentIndicator`, `MonthLoad`, `MonthTotals`, `FortnightPosition`, `OwnBalance`, `CategorySummary` etc. (mesma ordem das regras em português). Textos de erro e de interface são tratados pelo i18n (D-11); a API devolve códigos de erro estáveis em inglês.

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

**Valores derivados (não persistidos, NFR-002)**: total de gastos, total de fixos, saldo, posição dia 15/30, saldo próprio, resumos e porcentagens.

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
| posição(limite) | Σ entradas com `data.dia ≤ limite` − Σ gastos com `dataEfetiva.dia ≤ limite`; limite 30 = último dia do mês | REQ-030/031 |
| saldoPróprio | saldo + Σ gastos com pessoa_id − Σ entradas com pessoa_id | REQ-034 |
| dividirParcelas(total, n) | base = total ÷ n (inteira); n−1 parcelas = base; última = total − base×(n−1) | REQ-020 |
| parcelasPorValor(valor, n) | n parcelas iguais | REQ-018 |
| resumoPorCategoria | Σ por categoria; % = valor ÷ totalGastos (2 casas, HALF_UP só na exibição) | REQ-035 |
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
| GET | /api/configuracao | sim | REQ-006, REQ-009 | ano vigente e parcelas automáticas |
| PUT | /api/configuracao | sim | REQ-006, REQ-009 | altera configuração |
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
| GET | /api/anos/{ano}/panorama | sim | REQ-039 | 12 meses: gastos, entradas e saldo |

## Frontend
| Rota | Tela | REQ |
|---|---|---|
| /entrar, /criar-conta, /recuperar-senha | Entrar e criar conta | REQ-001..003 |
| /mes/:aaaa-mm | Mês (principal) + janela "Novo lançamento" | REQ-041, REQ-042 |
| /panorama/:ano | Panorama anual | REQ-039 |
| /categorias, /cartoes, /pessoas | Cadastros | REQ-044 |
| /configuracoes | Ano vigente, parcelas automáticas | REQ-006, REQ-009 |

Navegação: menu lateral (≥ 768px) e barra inferior (< 768px) — REQ-043. Identidade visual própria — REQ-045.

## File Structure
```
nize/
├── CLAUDE.md  CONTEXT.md  requirements.md  design.md  tasks.md
├── pom.xml                      (agregador Maven; opcional)
├── backend/              (estrutura no padrão do Spring Initializr)
│   ├── pom.xml  mvnw  mvnw.cmd  .mvn/wrapper/  .gitignore  .gitattributes  HELP.md
│   └── src/
│       ├── main/java/br/com/nize/
│       │   ├── NizeApplication.java
│       │   ├── rules/           (puro: sem Spring/JPA)
│       │   ├── domain/          (entidades JPA)
│       │   ├── repository/
│       │   ├── service/
│       │   ├── api/             (controllers e DTOs)
│       │   └── security/        (JWT, user_id do token)
│       ├── main/resources/  application.yml, application-local.yml, application-h2.yml
│       ├── main/resources/db/migration/   (Flyway)
│       └── test/java/br/com/nize/  (rules/, service/, api/)
└── frontend/
    ├── package.json  vite.config.ts
    └── src/ (pages/, components/, api/, lib/, styles/, i18n/ com en.json e pt.json, test/)
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

## Open Questions
- [x] ~~Lista das 15 categorias de fábrica~~ — resolvido em 2026-11-01: ver seção "Categorias de fábrica". "Mainha" não é categoria; é uma pessoa (terceiro), cadastrada em `pessoa`.
- [x] ~~Versão do Spring Boot~~ — resolvido em 2026-11-01: 4.0.9 (última 4.0.x estável; a 4.1.2 existe e pode ser adotada depois).
- [x] ~~groupId / pacote Java raiz~~ — resolvido em 2026-11-01: `br.com.nize`.
- [x] ~~Recuperação de senha~~ — resolvido em 2026-11-01: token no log em dev, atrás da interface `EmailSender`.
- [x] ~~Padrão de `parcelas_automaticas`~~ — resolvido em 2026-11-01: ligado em conta nova.
- [x] ~~Crédito sem cartão~~ — resolvido em 2026-11-01: usa a `data` do lançamento.
- [x] ~~Fixo com cartão~~ — resolvido em 2026-11-01: **pode**. Há fixos pagos no crédito (ex.: assinaturas); fixo e forma de pagamento são independentes (ver "Regras de consistência do lançamento"). Diverge do PDF; decisão da usuária.
- [x] ~~Parcela e vencimento~~ — resolvido em 2026-11-01: cada parcela pesa no mês em que aparece, com o dia de vencimento do cartão naquele mês.
- [ ] **Hospedagem final** (provisório: Render + Vercel/Netlify/Cloudflare Pages + H2).
- [ ] **Cucumber ou JUnit** para os cenários Gherkin do PDF. Proposto: JUnit 5 com nomes iguais aos cenários, sem Cucumber.
- [ ] **Meta de desempenho NFR-007** é proposta da spec; confirmar.
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
