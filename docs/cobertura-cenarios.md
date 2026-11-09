# Cobertura de cenários, isolamento e desempenho
> Nize — Phase 8 (TASK-046, TASK-047, TASK-048) — 2026-11-04

Fonte dos cenários: `Nize — Funcionalidades e escopo do MVP web.pdf` (seção "Regras de cálculo"). Onde a spec diverge
do PDF por decisão da usuária, vale `requirements.md` e o cenário aparece como **adaptado**.

Como rodar: `cd backend && ./mvnw test` (todos os testes abaixo, exceto o de carga). Os nomes dos testes de
`PdfScenariosTest` são os nomes dos cenários (`@DisplayName`), sem Cucumber (design.md, Open Questions).

## 1. Cenários do PDF → testes (TASK-046, NFR-003)

Cada cenário tem um teste ponta a ponta pela API com os números do PDF (`api.PdfScenariosTest`, coluna "Ponta a
ponta") e, quando é regra de cálculo, o teste da regra pura escrito antes do código (`br.com.nize.rules`, coluna
"Regra / outros"). Todos os métodos ficam em `backend/src/test/java/br/com/nize/`.

| # | Funcionalidade | Cenário | REQ | Ponta a ponta (`api.PdfScenariosTest#…`) | Regra / outros | Status |
|---|---|---|---|---|---|---|
| 1 | Totais do mês | Total de gastos soma fixos e cartão | REQ-027, REQ-028 | `totalExpensesAddsFixedAndCard` | `rules.MonthTotalsTest#totalExpensesAddsFixedAndCard`; `api.MonthApiTest#totalsFollowThePdfScenarios` | ok |
| 2 | Totais do mês | Saldo é entradas menos gastos | REQ-029 | `balanceIsIncomeMinusExpenses` | `rules.MonthTotalsTest#balanceIsIncomeMinusExpenses`; `api.MonthApiTest#totalsFollowThePdfScenarios` | ok |
| 3 | Resumo por categoria | Porcentagem sobre o total de gastos | REQ-035 | `percentageOverTheTotalExpenses` | `rules.CategorySummaryTest#percentageOverTheTotalExpenses` | ok |
| 4 | Resumo por categoria | Categorias fecham com o total | REQ-035 | `categoriesAddUpToTheTotal` | `rules.CategorySummaryTest#categoriesAddUpToTheTotalExpenses`; `api.MonthApiTest#summariesAlwaysAddUpToTheTotals` | ok |
| 5 | Parcelas | Compra dividida sem perder centavos | REQ-020 | `aPurchaseIsSplitWithoutLosingCents` | `rules.InstallmentsTest#oneHundredReaisInThreeInstallmentsLeavesTheDifferenceInTheLast`, `#theSumIsAlwaysTheTotalAndTheShapeIsAlwaysTheSame` | ok |
| 6 | Parcelas | Indicador de parcela | REQ-021 | `installmentIndicator` (API devolve `installmentLabel` "7/12") | `rules.InstallmentIndicatorTest#seventhOfTwelveIsSevenSlashTwelve` | ok; exibido na tela do mês (TASK-040, `EntryLists`) |
| 7 | Resumo por cartão | Lançamento sem cartão não some do resumo | REQ-037 | `anExpenseWithoutACardDoesNotVanishFromTheSummary` | `rules.PaymentSummaryTest#aCreditWithoutACardDoesNotVanishFromTheCardSummary` | ok |
| 8 | Posição por quinzena | Falta ou sobra até o dia 15 | REQ-030, REQ-048 | `whatIsLeftOrMissingUntilThe15th` | `rules.ClosingPositionsTest#theFirstIntervalIsTheSumOfItsExpenses` | ok — **adaptado (REQ-030, REQ-048)**: posição do 1º intervalo, do dia 1 ao 1º fechamento (padrão 15), é só a soma dos gastos (120000); as entradas não entram e não há sobra/falta |
| 9 | Posição por quinzena | Posição até o fim do mês | REQ-030, REQ-031, REQ-048 | `positionUntilTheEndOfTheMonth` | `rules.ClosingPositionsTest#theSecondIntervalDoesNotCarryTheFirstOne`, `#anIntervalWithoutExpensesIsZero`, `#entriesAfterTheLastClosingDayFallInTheLastInterval`, `#thirtyAndThirtyOneInFebruaryBecomeASinglePositionOnTheLastDay` | ok — **adaptado (REQ-031, REQ-048)**: o PDF fala em "dia 30" e em posição acumulada (−30000); aqui o 2º intervalo vai do dia 16 ao último dia do mês e soma só os seus gastos (180000); o acumulado do PDF é o saldo do mês |
| 10 | Posição por quinzena | Marcar como pago não altera totais | REQ-033 | `markingAsPaidDoesNotChangeTotals` | `api.MonthApiTest#markingAsPaidChangesNoTotalNorPosition` ("pago" não é entrada de `ClosingPositions`/`MonthTotals`) | ok |
| 11 | Pix e crédito na posição por quinzena | Gasto no Pix pesa na data do pagamento | REQ-032 | `aPixExpenseWeighsOnThePaymentDate` | `rules.ClosingPositionsTest#aPixExpenseWeighsOnThePaymentDate` | ok |
| 12 | Pix e crédito na posição por quinzena | Gasto no crédito pesa no vencimento do cartão | REQ-032 | `aCreditExpenseWeighsOnTheCardDueDay` | `rules.ClosingPositionsTest#aCreditExpenseWeighsOnTheCardDueDayNotOnThePurchaseDate`; `api.MonthApiTest#positionsUseThePixDateAndTheCardDueDay` | ok |
| 13 | Carga ao abrir um mês | Fixos vêm do mês anterior sem a marca de pago | REQ-022 | `fixedExpensesComeFromThePreviousMonthUnpaid` | `rules.MonthLoadTest#aFixedExpenseIsCopiedToTheNewMonth`; `service.MonthLoadServiceTest#fixedExpensesAreCopiedWithPaidClearedAndOneOffsAreNot` | ok |
| 14 | Carga ao abrir um mês | Parcelas continuam no mês seguinte | REQ-022 | `installmentsContinueInTheNextMonth` | `rules.MonthLoadTest#seventhOfTwelveCreatesTheEighth`; `service.MonthLoadServiceTest#theNextInstallmentIsCreatedAlsoForOnesEnteredByHand` | ok — **adaptado (REQ-022)**: só com a permissão de parcelas automáticas ligada (`#withTheAutomaticPermissionOffOnlyFixedExpensesAreCarried`) |
| 15 | Carga ao abrir um mês | A última parcela não se repete | REQ-022 | `theLastInstallmentIsNotRepeated` | `rules.MonthLoadTest#theLastInstallmentDoesNotCreateAnotherOne`; `service.MonthLoadServiceTest#theLastInstallmentDoesNotCreateAnotherOne` | ok |
| 16 | Parcelas automáticas | Valor total e número de parcelas | REQ-018, REQ-020 | `totalValueAndNumberOfInstallments` | `rules.InstallmentsTest#oneHundredReaisInThreeInstallmentsLeavesTheDifferenceInTheLast`; `api.ExpenseInstallmentsApiTest#oneHundredReaisInThreeInstallmentsBecomes3333_3333_3334OneInEachMonth` | ok |
| 17 | Parcelas automáticas | Valor da parcela e número de parcelas | REQ-018 | `installmentValueAndNumberOfInstallments` | `rules.InstallmentsTest#fourInstallmentsOfFiftyReaisAreFourTimesFiveThousandCents`; `api.ExpenseInstallmentsApiTest#fourInstallmentsOfFiftyReaisAreFourTimesFiveThousand` | ok |
| 18 | Parcelas automáticas | Somente o valor, sem parcelas | REQ-018 | `onlyTheValueWithoutInstallments` | `rules.InstallmentsTest#withoutInstallmentsItIsASingleExpenseOfTheWholeValue`; `api.ExpenseInstallmentsApiTest#withoutInstallmentsItIsASingleExpense` | ok |
| 19 | Parcelas automáticas | Permissão desligada não gera parcelas | REQ-009, REQ-018 | `permissionOffGeneratesNoInstallments` | `api.ExpenseInstallmentsApiTest#withTheAutomaticPermissionOffItCreatesASingleExpense` | ok |
| 20 | Parcelas automáticas | Parcelas geradas não se repetem na carga do mês | REQ-022 | `generatedInstallmentsAreNotRepeatedByTheMonthLoad` | `rules.MonthLoadTest#anInstallmentThatAlreadyExistsIsNotCreatedAgain`; `service.MonthLoadServiceTest#installmentsGeneratedUpFrontAreNotCreatedAgain` | ok |
| 21 | Excluir categoria | Categoria com lançamentos não é excluída direto | REQ-012 | `aCategoryWithExpensesIsNotDeletedRightAway` (409 `CATEGORY_IN_USE`, `count` 2) | `api.ExpenseCreateApiTest#aCategoryWithExpensesCannotBeDeletedAndTheCountIsReal`; front: `frontend/src/pages/catalog.test.tsx` › "asks what to do when the category has expenses, and keeps it" (a pergunta) | ok |
| 22 | Excluir categoria | Categoria sem lançamentos é excluída | REQ-012 | `aCategoryWithoutExpensesIsDeleted` | `api.CategoryDeleteTest#categoryWithoutExpensesIsDeletedRightAway`; front: `catalog.test.tsx` › "deletes an unused category after confirming" | ok |

Exemplo do PDF fora do Gherkin (texto sobre gastos de terceiros): saldo R$ 500,00, gastos de terceiros R$ 200,00 e
reembolsos R$ 190,00 → saldo próprio R$ 510,00 (REQ-034): `rules.OwnBalanceTest#ownBalanceIsTheBalanceWithoutThirdPartyExpensesAndReimbursements`
e `api.MonthApiTest#ownBalanceLeavesOutThirdPartyExpensesAndReimbursements`.

**Resultado**: 22 cenários Gherkin no PDF, 22 com teste ponta a ponta (todos novos em `PdfScenariosTest`, com os
números do PDF) e todos também cobertos por testes já existentes de regra, serviço ou API. Nenhum cenário sem teste;
nenhum defeito de código de produção encontrado.

**Pendências de outras fases** (o PDF não tem cenário Gherkin para elas, mas a tela exibe o resultado):
- Phase 6 (tela do mês): exibir o indicador "N/M" (cenário 6), totais, posições e resumos que a API já devolve.
- Phase 7 (panorama anual, REQ-039): o PDF não traz cenário Gherkin do panorama; os testes de `GET /api/years/{year}/overview` estão em `api.OverviewApiTest` (TASK-044).

## 2. Endpoints de dados × teste de isolamento (TASK-047, REQ-004, NFR-004)

Todos os testes abaixo estendem `IsolationTestBase` (duas usuárias, A e B). "Isolado" significa: B recebe 404 em
linha de A (e vice-versa), listas e o mês só mostram linhas da própria usuária, e `userId` no corpo é ignorado.
`IsolationCoverageTest` garante que todo controller de dados tem um teste desses. Fora do escopo: `/api/auth/**`
(sem dados de usuária) e `/actuator/health`.

| Endpoint | Controller | Teste de isolamento (`api.…`) |
|---|---|---|
| GET /api/me | MeController | `MeAndSettingsTest#meReturnsTheCallerAndIgnoresAnyUserIdInTheRequest` |
| POST /api/me/onboarding-seen | MeController | `OnboardingTest#onlyTheCallersFlagChanges` |
| GET /api/settings | SettingsController | `MeAndSettingsTest#userBCannotReadOrChangeUserAsSettings`, `SettingsClosingsApiTest#eachUserSeesAndChangesOnlyTheirOwnClosings` |
| PUT /api/settings | SettingsController | os mesmos (B manda `userId` de A no corpo e só a linha de B muda) |
| GET /api/years | YearController | `YearApiTest#eachUserOnlySeesAndCopiesTheirOwnData` |
| POST /api/years/{year} | YearController | `YearApiTest#eachUserOnlySeesAndCopiesTheirOwnData` (copia só os dados da própria usuária) |
| GET /api/years/{year}/categories | CategoryController | `CategoryApiTest#createAddsACategoryToTheList` (B não vê a categoria de A) |
| POST /api/years/{year}/categories | CategoryController | `CategoryApiTest#theSameNameIsFineForAnotherUser`, `#createAddsACategoryToTheList` |
| PUT /api/categories/{id} | CategoryController | `CategoryApiTest#onlyTheOwnerCanUpdateACategoryAndOthersGet404`, `#anotherUsersChangeNeverTouchesMyCategory` |
| DELETE /api/categories/{id} | CategoryController | `CategoryDeleteTest#anotherUserGets404AndNothingIsDeleted` |
| GET /api/years/{year}/cards | CardController | `CardApiTest` (teste de criação: B lista 0 cartões) |
| POST /api/years/{year}/cards | CardController | `CardApiTest#theSameNameIsFineForAnotherUserAndAnotherYear` |
| PUT /api/cards/{id} | CardController | `CardApiTest#onlyTheOwnerCanUpdateACardAndOthersGet404`, `#anotherUsersChangeNeverTouchesMyCard` |
| GET /api/years/{year}/people | PersonController | `PersonApiTest` (teste de criação: B lista 0 pessoas) |
| POST /api/years/{year}/people | PersonController | `PersonApiTest#theSameNameIsFineForAnotherUserAndAnotherYear` |
| PUT /api/people/{id} | PersonController | `PersonApiTest#onlyTheOwnerCanUpdateAPersonAndOthersGet404`, `#anotherUsersChangeNeverTouchesMyPerson` |
| GET /api/months/{month} | MonthController | `MonthApiTest#eachUserSeesOnlyTheirOwnMonth`; carga: `service.MonthLoadServiceTest#eachUserLoadsOnlyTheirOwnMonth` |
| POST /api/months/{month}/expenses | ExpenseController | `ExpenseCreateApiTest#anotherUsersCategoryCardAndPersonAre404AndNothingIsCreated`, `#theExpenseBelongsToTheCallerNoMatterWhatTheBodySays`; parcelas: `ExpenseInstallmentsApiTest#generatedInstallmentsBelongToTheCallerOnly` |
| PUT /api/expenses/{id} | ExpenseController | `ExpenseEditApiTest#anotherUserGets404OnPutPatchAndDeleteAndNothingChanges`, `#editCannotPointToAnotherUsersCategory`, **`#editCannotPointToAnotherUsersCardOrPerson` (novo)** |
| PATCH /api/expenses/{id}/paid | ExpenseController | `ExpenseEditApiTest#anotherUserGets404OnPutPatchAndDeleteAndNothingChanges`, `#ownerAccessWorksAndEverythingRequiresAuthentication` (`assertOnlyOwnerCanAccess`) |
| DELETE /api/expenses/{id} | ExpenseController | `ExpenseEditApiTest#anotherUserGets404OnPutPatchAndDeleteAndNothingChanges` |
| POST /api/months/{month}/incomes | IncomeController | `IncomeApiTest#aPersonOfAnotherUserOrYearIsNotFound`, teste do corpo com `userId` de B (a entrada fica com A) |
| PUT /api/incomes/{id} | IncomeController | `IncomeApiTest#onlyTheOwnerCanEditAnIncome` (`assertOnlyOwnerCanAccess`), **`#editCannotPointToAnotherUsersPerson` (novo)** |
| DELETE /api/incomes/{id} | IncomeController | `IncomeApiTest#onlyTheOwnerCanDeleteAnIncome` |
| GET /api/years/{year}/overview | `OverviewApiTest#onlyTheOwnerCanReadTheOverviewOfHerYear`, `#anotherUsersMovementsNeverShowUp` | ok (TASK-044) |

**Resultado**: 25 de 25 endpoints de dados cobertos (100%), incluindo o panorama (TASK-044). Faltavam dois casos de referência de outra
usuária na edição (cartão/pessoa no `PUT /api/expenses/{id}` e pessoa no `PUT /api/incomes/{id}`); os testes foram
acrescentados e passam (o código já devolvia 404). TASK-047 fica aberta só pelo endpoint do panorama.

## 3. Teste de carga do resumo do mês (TASK-048, NFR-007)

Meta: `GET /api/months/{month}` com 500 lançamentos no mês responde em < 500 ms no p95.

Teste: `api.MonthResponseTimeTest` (tag JUnit `performance`, fora da suíte normal). Rodar com
`cd backend && ./mvnw test -Dtest=MonthResponseTimeTest -Dtest.excludedGroups=none`.
Cadastra uma usuária, lança pela API 500 gastos em abril (fixos no Pix, fixos no crédito, cartão com 3 cartões,
terceiros com 2 pessoas, parcelas à mão) e 20 entradas (algumas reembolsos), abre o mês uma vez (carga), faz 30
requisições de aquecimento e mede 300 requisições seguidas pelo MockMvc. Sem transação de teste (cada requisição
abre e fecha a sua, como em produção); a usuária é apagada no fim.

| Execução | Data | Ambiente | Requisições medidas | p50 | p95 | máx. |
|---|---|---|---|---|---|---|
| 1 | 2026-11-04 | WSL2 local, Intel i5-12450H (12 threads), 8 GB RAM, Java 25.0.5, perfil `h2` (H2 em memória), MockMvc (sem rede) | 300 | 10,0 ms | 14,4 ms | 27,0 ms |
| 2 | 2026-11-04 | o mesmo | 300 | 9,7 ms | 14,2 ms | 25,6 ms |

**Resultado**: p95 ≈ 14 ms, bem abaixo da meta de 500 ms. Não há N+1: `MonthService.open` faz um número fixo de
consultas por requisição (ano existe, mês já carregado, gastos, entradas, cartões, categorias, pessoas e
configuração com os dias de fechamento), independente do número de lançamentos. Limites da medição: H2 em memória e MockMvc não incluem rede, TLS nem o
MySQL do Aiven; em produção (plano gratuito do Render, cold start) o tempo será maior, mas o custo de cálculo por
requisição medido aqui é pequeno.
