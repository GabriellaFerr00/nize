import { expect, test } from '@playwright/test'

// Synthetic user only; never production data.
const email = `e2e-${Date.now()}@example.test`
const password = 'E2e-synthetic-pass-1'

test('a new user signs up, logs an expense in the current month and signs in again', async ({ page }) => {
  const errors: string[] = []
  page.on('console', (msg) => {
    if (msg.type() === 'error') errors.push(msg.text())
  })

  await page.goto('/sign-up')
  await page.getByLabel('Nome').fill('Pessoa E2E')
  await page.getByLabel('E-mail').fill(email)
  await page.getByLabel('Senha').fill(password)
  await page.getByRole('button', { name: 'Criar conta' }).click()
  await page.getByRole('button', { name: 'Entendi' }).click()

  const now = new Date()
  const month = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  await page.goto(`/month/${month}`)
  await page.getByRole('button', { name: 'Novo lançamento' }).click()
  // Scoped to the window: the month screen also has labelled sections (e.g. "Por categoria").
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('Nome', { exact: true }).fill('Mercado E2E')
  await dialog.getByLabel('Valor (R$)', { exact: true }).fill('123,45')
  await dialog.getByLabel('Categoria', { exact: true }).selectOption({ index: 1 })
  await dialog.getByLabel('Data', { exact: true }).fill(`${month}-10`)
  await dialog.getByRole('button', { name: 'Salvar e lançar outro' }).click()
  await expect(dialog.getByText('Lançamento salvo.')).toBeVisible()

  // The month screen (GET /api/months/{month}) lists the new expense.
  await dialog.getByRole('button', { name: 'Cancelar' }).click()
  await expect(page.getByText('Mercado E2E')).toBeVisible()

  await page.getByRole('button', { name: 'Sair' }).click()
  await page.getByLabel('E-mail').fill(email)
  await page.getByLabel('Senha').fill(password)
  await page.getByRole('button', { name: 'Entrar' }).click()
  await expect(page.getByRole('button', { name: 'Sair' })).toBeVisible()

  // A CSP violation shows up as a console error.
  expect(errors.filter((e) => /Content Security Policy|Refused to/i.test(e))).toEqual([])
})
