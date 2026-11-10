# design.md
> Nize — Version 0.3.1 (MVP web) — 2026-11-09

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
| D-2 | Dinheiro em centavos (`long`) nas regras, serviços, API e front; sem `double`/`float`. No banco, `DECIMAL(12,2)` em reais (colunas `amount`), convertido por `MoneyColumnConverter` (JPA) na leitura e na gravação; `BigDecimal` só dentro desse conversor (2026-11-09, pedido da usuária) | NFR-001 |
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
| D-20 | Identidade visual "Verde Nize" (substitui a verde-azulada): paleta, tokens e regras da skill `design-nize` (`.claude/skills/design-nize`). Cores só como tokens semânticos em `src/styles/index.css` (`page`, `sidebar`, `card`, `soft`, `line`, `line-strong`, `ink`, `ink-soft`, `brand`, `on-brand`, `brand-text`, `positive`, `negative`, `on-negative`, `warning`, `focus`, `backdrop`, `chart-*`), com valores para claro e escuro; as cores padrão do Tailwind ficam desligadas. O Verde Nize `#B8F45A` só é preenchimento no modo claro (texto verde usa `brand-text`). Tema: só Claro e Escuro; o seletor grava `nize.theme` (`light`/`dark`) no `localStorage` e o `<html>` sempre recebe `data-theme` (`lib/theme.ts`); sem escolha gravada, o tema inicial vem de `prefers-color-scheme` (decisão da usuária 2026-11-05: sem opção "Sistema"). Logotipo: o padrão de `public/logoPadrao/` (`nize-logo-n-bloco-verde-*`: bloco verde arredondado com o "N" + "ize"), mostrado por `components/Logo.tsx`; no modo escuro usa a variante `nize-logo-n-bloco-verde-fundo-escuro-*` (só a palavra "ize" em off-white `#F2F5EC`, gerada a partir do original) por meio da variante `dark:` do Tailwind, que acompanha o mesmo tema dos tokens. Ícones quadrados em `public/logotipo/` (favicon, `apple-touch-icon`, `site.webmanifest`). Fonte do sistema (a CSP não permite fonte externa). Menu lateral continua a partir de 768px (REQ-043), não 1024px como na skill | REQ-045, decisão da usuária 2026-11-05 |
| D-21 | Cores, marcas e logotipos (REQ-052, 2026-11-09): **cor** de categoria/pessoa = uma das 8 chaves rápidas (`green`, `teal`, `blue`, `purple`, `pink`, `red`, `orange`, `brown`, com tokens `--nz-tag-*` claro/escuro ≥ 4:1) ou `#rrggbb` minúsculo escolhido na paleta de matizes e tons (12 matizes × 6 tons, gerada no front) ou no seletor do aparelho; NULL = neutra. A cor é só uma bolinha ao lado do nome, sempre com contorno `line-strong` (≥ 3:1 nos dois temas), nunca cor de texto; o valor vai por CSSOM (`style` do React), compatível com a CSP. **Marca** do cartão = chave de `CardBrand` (lista igual no back e em `frontend/src/lib/cardBrands.ts`, conferida por teste): nome, cor e, quando o Simple Icons (CC0, versão 16.36.0) tem o logotipo, o SVG em `frontend/public/card-brands/` (servido pelo próprio site, `img-src 'self'`); sem logotipo livre, selo com iniciais em texto branco ou escuro (≥ 4:1, `contraste.py`). **Logotipo próprio** (imagem enviada, PNG/JPEG/WebP pelos primeiros bytes, ≤ 200 KB, tabela `card_logo`, mostrado como `data:` URL) tem prioridade sobre a marca; sem nenhum, a inicial do nome. Atribuição e aviso de marcas em `frontend/public/card-brands/README.md` | REQ-052 |
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
| valor | `amount` (`DECIMAL(12,2)`, reais; até 0.2.10 era `amount_cents BIGINT`) |
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
| cor | VARCHAR(16) | NULL | REQ-052; coluna `color` (V9; o CHECK da paleta saiu na V10): chave rápida ou `#rrggbb` (validada na API, D-21) |
| limite_mensal | DECIMAL(12,2) | NULL, CHECK > 0 | REQ-051; coluna `limit_amount` (V8), `monthlyLimitCents` na API; NULL = sem limite; copiado com a categoria para o ano novo |

### cartao
| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGINT | PK | REQ-013 |
| usuario_id | BIGINT | NOT NULL, FK | |
| ano | SMALLINT | NOT NULL | |
| nome | VARCHAR(80) | NOT NULL | UNIQUE (usuario_id, ano, nome) |
| dia_vencimento | TINYINT | NOT NULL, CHECK 1..31 | define a quinzena do crédito |
| ativo | BOOLEAN | NOT NULL, default true | |
| marca | VARCHAR(32) | NULL | REQ-052; coluna `brand` (V10), chave de `CardBrand` (validada na API); copiada para o ano novo |

### pessoa
| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGINT | PK | REQ-014 |
| usuario_id | BIGINT | NOT NULL, FK | |
| ano | SMALLINT | NOT NULL | |
| nome | VARCHAR(80) | NOT NULL | UNIQUE (usuario_id, ano, nome) |
| ativa | BOOLEAN | NOT NULL, default true | |
| cor | VARCHAR(16) | NULL | REQ-052; coluna `color` (V9; o CHECK da paleta saiu na V10): chave rápida ou `#rrggbb` (validada na API, D-21) |

### logotipo_cartao
Tabela `card_logo` (V9), uma linha por cartão que tem imagem (REQ-052, D-21).
| Field | Type | Constraints | Notes |
|---|---|---|---|
| cartao_id | BIGINT | PK, FK cartao | `card_id`; excluído junto com o cartão |
| usuario_id | BIGINT | NOT NULL, FK | `user_id`; toda consulta filtra por ele |
| tipo | VARCHAR(16) | NOT NULL, CHECK em `image/png`, `image/jpeg`, `image/webp` | `content_type`, do conteúdo, não do cabeçalho |
| imagem | MEDIUMBLOB | NOT NULL | `data`, até 200 KB |

### lancamento
| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGINT | PK | REQ-015..023 |
| usuario_id | BIGINT | NOT NULL, FK | |
| mes | CHAR(7) | NOT NULL | `AAAA-MM` |
| nome | VARCHAR(120) | NOT NULL | |
| categoria_id | BIGINT | NOT NULL, FK categoria | |
| valor | DECIMAL(12,2) | NOT NULL, CHECK > 0 | |
| tipo | VARCHAR(10) | NOT NULL, CHECK in ('fixo','cartao') | |
| forma_pagamento | VARCHAR(10) | NOT NULL, CHECK in ('pix','credito') | independente do tipo: fixo pode ser pix ou crédito (ex.: assinatura no cartão); cartao é sempre crédito |
| cartao_id | BIGINT | NULL, FK cartao | só crédito; obrigatório no crédito desde 2026-11-09 (validado no serviço, `CARD_REQUIRED`); NULL só em linhas antigas |
| pessoa_id | BIGINT | NULL, FK pessoa | NOT NULL = gasto de terceiro (REQ-017) |
| pago | BOOLEAN | NOT NULL, default false | só controle (REQ-033) |
| data | DATE | NOT NULL | obrigatória (PDF); deve estar em `mes` |
| parcela_atual | SMALLINT | NULL | |
| parcelas_total | SMALLINT | NULL | CHECK parcela_atual ≤ parcelas_total |
| grupo_parcela_id | CHAR(36) | NULL | UUID comum às parcelas de uma compra |

Restrições: UNIQUE (`grupo_parcela_id`, `parcela_atual`) onde não nulos; `parcela_atual` e `parcelas_total` ambos nulos ou ambos preenchidos.

Regras de consistência do lançamento (validadas no serviço; as CHECK do banco cobrem as simples):
- `tipo = cartao` ⇒ `forma_pagamento = credito`. `tipo = fixo` ⇒ `forma_pagamento` pix **ou** crédito.
- `cartao_id` só com `forma_pagamento = credito`, e obrigatório nele (novo ou editado; 400 `CARD_REQUIRED`). Linhas antigas de crédito sem `cartao_id` continuam válidas e contam nos totais, mas não entram no resumo por cartão (não há "Sem cartão").
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
| valor | DECIMAL(12,2) | NOT NULL, CHECK > 0 | |
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
| posições(dias) | limites = `min(d, último dia do mês)` de cada dia de fechamento, em ordem crescente e sem repetir (30 e 31 em fevereiro dão um só). Intervalos: `[1, L1]`, `[L1+1, L2]`, …, e o último vai até o último dia do mês (`[Ln-1+1, fim]`, mesmo que Ln seja antes do fim). Cada posição `{fromDay, toDay, totalExpensesCents}` = Σ gastos com `dataEfetiva.dia` no intervalo (entradas não entram; nunca negativo); não acumula os intervalos anteriores. Fechamentos desligados ou sem dias → nenhuma posição | REQ-030/031/048 |
| saldoPróprio | saldo + Σ gastos com pessoa_id − Σ entradas com pessoa_id | REQ-034 |
| dividirParcelas(total, n) | base = total ÷ n (inteira); n−1 parcelas = base; última = total − base×(n−1) | REQ-020 |
| parcelasPorValor(valor, n) | n parcelas iguais | REQ-018 |
| resumoPorCategoria | Σ por categoria; % = valor ÷ totalGastos (2 casas, HALF_UP só na exibição; aritmética decimal exata, nunca float; na API é um número com 2 casas, ex.: `23.86`) | REQ-035 |
| limiteDaCategoria | `CategoryLimit.of(gasto, limite)`: sem limite → `NONE`; gasto > limite → `OVER`; gasto ≥ 80% do limite (`gasto × 100 ≥ limite × 80`, inteiros) → `NEAR`; senão `UNDER`. Na API, cada item de `byCategory` traz `limitCents` e `limitStatus` (só com limite). A tela do mês mostra uma faixa com as categorias `NEAR`/`OVER` e "usado de limite" no resumo; nada bloqueia o lançamento | REQ-051 |
| resumoPorForma / PorCartão | Σ por forma de pagamento; Σ por cartão, só cartões (sem grupo "Sem cartão"; linha antiga sem cartão fica só no total de crédito) | REQ-036/037 |
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
| POST | /api/anos/{ano}/categorias | sim | REQ-011, REQ-051 | cria; `monthlyLimitCents` opcional (> 0, ≤ 999999999999) também no PUT (ausente/null = sem limite) |
| PUT | /api/categorias/{id} | sim | REQ-011 | renomeia, descreve, ativa/desativa |
| DELETE | /api/categorias/{id} | sim | REQ-012 | exclui; 409 com contagem se houver lançamentos |
| GET/POST | /api/anos/{ano}/cartoes | sim | REQ-013 | lista / cria |
| PUT | /api/cartoes/{id} | sim | REQ-013 | edita |
| PUT | /api/cartoes/{id}/logotipo | sim | REQ-052 | `PUT /api/cards/{id}/logo`, corpo = bytes da imagem; 204; não é PNG/JPEG/WebP → 400 `INVALID_IMAGE`; > 200 KB → 400 `IMAGE_TOO_LARGE`; cartão de outra usuária → 404 |
| GET | /api/cartoes/{id}/logotipo | sim | REQ-052 | bytes com o `Content-Type` guardado, `nosniff`, `Cache-Control: private, no-cache`; sem imagem → 404. `CardResponse.hasLogo` diz se existe |
| DELETE | /api/cartoes/{id}/logotipo | sim | REQ-052 | remove a imagem; 204 |
| DELETE | /api/cartoes/{id} | sim | REQ-013 | `DELETE /api/cards/{id}`: exclui; 409 `CARD_IN_USE` com `count` se houver lançamentos com o cartão (de qualquer mês do ano) |
| GET/POST | /api/anos/{ano}/pessoas | sim | REQ-014 | lista / cria |
| PUT | /api/pessoas/{id} | sim | REQ-014 | edita |
| GET | /api/meses/{aaaa-mm} | sim | REQ-022, REQ-027..041 | abre o mês (dispara carga na 1ª vez) e devolve listas, totais, posições (`positions: [{fromDay, toDay, totalExpensesCents}]`, uma por intervalo), resumos e saldo próprio |
| POST | /api/meses/{aaaa-mm}/lancamentos | sim | REQ-015..020 | cria gasto; gera parcelas se `gerarParcelas` e permissão ligada |
| PUT | /api/lancamentos/{id} | sim | REQ-023 | edita |
| PATCH | /api/lancamentos/{id}/pago | sim | REQ-015, REQ-033 | marca/desmarca pago |
| DELETE | /api/lancamentos/{id} | sim | REQ-023, REQ-050 | exclui (204). Parcela: `?scope=this` (padrão) só ela; `?scope=following` ela e as de número maior do mesmo `installment_group_id` (meses seguintes). Com `this`, se a parcela seguinte ainda não existe e o mês seguinte ainda não foi carregado (parcelas automáticas ligadas), ela é criada na hora no mês seguinte, para a carga não perder a sequência |
| POST | /api/meses/{aaaa-mm}/entradas | sim | REQ-024..026 | cria entrada (parcelada ou reembolso) |
| PUT/DELETE | /api/entradas/{id} | sim | REQ-023, REQ-050 | edita / exclui; `scope` como em lançamentos |
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

Navegação: menu lateral (≥ 768px) e barra inferior (< 768px) — REQ-043. Mês padrão (`/`, menu "Mês" e "Panorama"): o mês do calendário dentro do ano vigente da usuária (nov/2026 com ano vigente 2027 → `2027-11`; `lib/month.defaultMonth`) — REQ-006, decisão da usuária 2026-11-04. Identidade visual própria "Verde Nize", modo claro/escuro com seletor de tema ao lado do idioma (D-20) — REQ-045. Link "Pular para o conteúdo" no topo; ações com área de toque ≥ 44px.

Layout da tela do mês (REQ-041, D-20; aprovado pela usuária em 2026-11-05): uma faixa de topo com o saldo do mês primeiro e maior, os outros totais numa linha logo abaixo (entradas, gastos, fixos) e, com os fechamentos ligados, uma lista simples com o total de cada intervalo ("Do dia A ao B" e "Gastos R$ X"), sem barra nem marca de hoje (decisão da usuária 2026-11-05). Com os fechamentos desligados a lista não aparece. As listas (Fixos, Cartão, Entradas) ficam numa só superfície, em estilo de livro-caixa: o dia do lançamento em destaque à esquerda de cada linha. Os resumos ficam numa superfície verde suave, com uma barra fina por categoria do tamanho da porcentagem que a API devolveu. Só os tokens de cor existentes; fonte do sistema.

Layout das demais telas (D-20; aprovado pela usuária em 2026-11-05), no mesmo estilo da tela do mês (título grande, uma superfície por bloco, linhas de livro-caixa, só os tokens de cor, fonte do sistema):
- **Base comum**: classes de botão (primário, secundário, perigo, ícone) e de campo em `lib/ui.ts`, usadas por todas as telas.
- **Menu**: tema por um botão que alterna Claro/Escuro no lugar da lista (rótulo fixo "Tema escuro" com `aria-pressed`, ligado no escuro); idioma com rótulo curto (PT/EN) no cabeçalho do celular. Continua gravando `nize.theme`.
- **Cadastros** (`CatalogManager`): uma superfície com linhas; ações por ícone (editar, desativar/reativar, excluir) com rótulo acessível; nos cartões o dia de vencimento em destaque à esquerda; inativos num grupo "Inativos" no fim.
- **Configurações**: cada opção numa linha (título e ajuda à esquerda, controle à direita); dias de fechamento só com os fechamentos ligados; "Novo ano" num bloco separado.
- **Panorama**: gráfico mais leve (barras finas, mês atual destacado no eixo); a tabela vira lista de livro-caixa (mês, entradas, gastos, saldo com seta), a linha abre o mês; meses depois do mês atual aparecem esmaecidos. Sem total do ano (a API não devolve; o front não soma).
- **Janelas** (`Dialog`): abaixo de 640px sobem de baixo, na largura toda; a partir de 640px, centralizadas; botões Cancelar/Salvar no fim.
- **Entrar/criar conta/recuperar senha** (`AuthLayout`): a partir de 768px, tela dividida com um painel verde suave (logotipo, slogan e 3 frases sobre o Nize) e o formulário; no celular, o formulário direto na página.

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
    ├── public/  _redirects (fallback da SPA)  _headers (CSP, HSTS...)  site.webmanifest
    │   └── logotipo/  ícones do Nize (SVG e PNG 192/256/512/1024; favicon e apple-touch-icon; D-20)
    │   └── logoPadrao/  logotipo com nome (PNG 1000/2000 e variante para fundo escuro; D-20)
    ├── functions/api/[[path]].ts  (Pages Function: proxy /api → Render; D-15)
    ├── e2e/  (Playwright, dados sintéticos)
    └── src/ (pages/, components/, api/, lib/, styles/, i18n/ com en.json e pt.json, test/)
        tela do mês (Phase 6): api/monthApi.ts, api/incomesApi.ts; lib/useMonth.ts, lib/format.ts;
        components/MonthHeader.tsx, MonthPicker.tsx, EntryLists.tsx, SummaryPanel.tsx, IncomeFormDialog.tsx, Money.tsx;
        testes pages/month.test.tsx, lib/month.test.ts
        panorama (Phase 7): pages/OverviewPage.tsx, components/OverviewChart.tsx (Recharts, carregado sob demanda),
        api/overviewApi.ts, pages/overview.test.tsx
        identidade (D-20): components/Logo.tsx, components/ThemeSwitcher.tsx, lib/theme.ts, lib/theme.test.ts, lib/ui.ts (classes comuns de botão e campo, 0.2.9)
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
| 0.3.1 | 2026-11-09 | D-21 ampliada: marcas de cartão brasileiras (`CardBrand`, V10 `card.brand`, SVGs CC0 em `public/card-brands/`) e cores `#rrggbb` por paleta de matizes e tons (V10 tira o CHECK da paleta). Pedido da usuária |
| 0.3.0 | 2026-11-09 | D-21: cores de categoria/pessoa (paleta fixa) e logotipo do cartão (V9 `color`, `card_logo`, `/api/cards/{id}/logo`; REQ-052). Limite mensal opcional por categoria (V8 `category.limit_amount`, regra `CategoryLimit`, `limitCents`/`limitStatus` no resumo; REQ-051). Cartão obrigatório no crédito (`CARD_REQUIRED`) e fim do "Sem cartão" nos resumos (REQ-015/016/037). Front: `lib/privacy.ts` + `ValuesToggle` (REQ-049, olho ao lado do tema), ícone sol/lua no `ThemeSwitcher` (REQ-045). Dinheiro em `DECIMAL(12,2)` no banco (V7: `expense.amount`, `income.amount`), `MoneyColumnConverter`; centavos no resto (D-2, NFR-001). `DELETE /api/cards/{id}` (REQ-013). Pedidos da usuária |
| 0.2.10 | 2026-11-09 | Posição = só a soma dos gastos do intervalo (sem entradas); JSON `{fromDay, toDay, totalExpensesCents}`; tela "Gastos R$ X" sem sobra/falta (REQ-030 0.1.9, decisão da usuária) |
| 0.2.9 | 2026-11-05 | Layout das demais telas (menu, cadastros, configurações, panorama, janelas, entrar) no estilo da tela do mês; tema por botão que alterna; `lib/ui.ts` (aprovado pela usuária) |
| 0.2.8 | 2026-11-05 | Layout da tela do mês: saldo em destaque com totais numa linha, lista simples com o total de cada fechamento (a régua proposta foi descartada pela usuária), listas em estilo livro-caixa, resumos com barra por categoria (aprovado pela usuária) |
| 0.2.7 | 2026-11-05 | D-20: identidade Verde Nize (tokens claro/escuro, logotipo de `logoPadrao` com variante para fundo escuro, seletor de tema Claro/Escuro); arquivos novos na File Structure |
| 0.2.6 | 2026-11-04 | Posições por intervalo entre fechamentos, sem acumular; último intervalo até o fim do mês; JSON `{fromDay, toDay, balanceCents}`; tela "Do dia A ao B" (REQ-030, decisão da usuária) |
| 0.2.5 | 2026-11-04 | Panorama faz a carga dos meses até o vigente (D-19); Open Questions da Phase 8 resolvidas (JUnit 5, NFR-007 confirmada); Maven 3.10.0; mês padrão no ano vigente (REQ-006); arquivos das Phases 6–8 na File Structure |
| 0.2.4 | 2026-11-04 | Fechamentos opcionais (REQ-048): `settings.closings_enabled`, tabela `closing_day`, regra `ClosingPositions` (substitui `FortnightPosition`), `/api/settings` com `closingsEnabled`/`closingDays`. Diverge do PDF (15/30 fixos); decisão da usuária |
| 0.2.3 | 2026-11-04 | PR do Dependabot sem preview do front e com secret próprio para o MySQL dos testes; Dependabot ignora Spring Boot ≥ 4.1 e Flyway ≥ 12; Testes integrados também rodam quando muda `backend.yml`/`frontend.yml` (senão o *gate* falha por falta de run) |
| 0.2.2 | 2026-11-02 | Um workflow por tema (back, front, segurança, testes integrados) sem aninhamento; deploys esperam Segurança e Testes integrados do mesmo commit (job *gate*); testes integrados em dois jobs (back: MySQL + fluxo da API; front: E2E com a API em `h2`) |
| 0.2.1 | 2026-11-02 | `ci-cd.yml` também roda em Pull Request para a `main` (o `workflow_dispatch` só existe depois que o workflow está na `main`) |
| 0.2.0 | 2026-11-01 | CI/CD e hospedagem (D-13 a D-18): perfil `prod` sem Flyway no boot, usuários de banco separados, TLS, proxy `/api` no Cloudflare Pages, limite de tentativas, Actuator health, `DisabledEmailSender`; File Structure com workflows, Dockerfile, scripts e docs |
