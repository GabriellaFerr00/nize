import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { cardBrand } from './cardBrands'

/** "Adicionar um novo cartão": a card outside the list, named by hand and saved without a brand. */
export const NEW_CARD_CHOICE = 'new'

/**
 * Which card a form is about (REQ-052, D-21, 2026-11-12): a brand of the list (`choice` = its key, the name is the
 * optional nickname or the brand's name) or a new card outside the list (`choice` = NEW_CARD_CHOICE, name required).
 * Shared by the Cards screen and the "+ Novo cartão" of an entry.
 */
export function useCardIdentity(item?: { name: string; brand?: string | null }) {
  const { t } = useTranslation()
  const [choice, setChoice] = useState(item ? (cardBrand(item.brand) ? item.brand! : NEW_CARD_CHOICE) : '')
  const [name, setName] = useState(item?.name ?? '')
  const [errors, setErrors] = useState<{ choice?: string; name?: string }>({})
  const brand = cardBrand(choice)

  function pick(next: string) {
    // the name follows the brand unless she typed a nickname of her own
    const previous = cardBrand(choice)
    const untouched = !name.trim() || name.trim() === previous?.name
    const picked = cardBrand(next)
    if (untouched) setName(picked?.name ?? '')
    setChoice(next)
    setErrors({})
  }

  /** The `{name, brand}` to send, or null after showing what is missing. */
  function validate(): { name: string; brand?: string } | null {
    const finalName = name.trim() || brand?.name || ''
    const next = {
      choice: !choice ? t('catalog.brand.required') : undefined,
      name:
        choice && !finalName
          ? t('catalog.errors.nameRequired')
          : finalName.length > 80
            ? t('catalog.errors.nameTooLong')
            : undefined,
    }
    setErrors(next)
    if (next.choice || next.name) return null
    return { name: finalName, ...(brand && { brand: brand.key }) }
  }

  return { choice, name, setName, pick, errors, validate }
}

export type CardIdentity = ReturnType<typeof useCardIdentity>
