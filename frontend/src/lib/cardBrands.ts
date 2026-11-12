/**
 * Card brands used in Brazil, offered when a card is created or edited (REQ-052, D-21). The key is what the API
 * stores (`card.brand`); the same keys live in the back-end `CardBrand` (a test keeps both lists equal).
 * `icon`: the brand has a logo saved in /card-brands/<key>.svg (a one-color CC0 icon from Simple Icons, or with
 * `fullColor` the institution's own logo from Open Finance Brasil; see public/card-brands/README.md); otherwise the
 * badge shows `initials`. `text` is white or near-black so the tile reads at ≥ 4:1 (contraste.py).
 */
export type CardBrand = {
  key: string
  name: string
  color: string
  text: string
  icon: boolean
  /** The icon is the institution's own full-color logo (Open Finance Brasil), shown on a white tile. */
  fullColor?: boolean
  initials: string
}

export const CARD_BRANDS: CardBrand[] = [
  { key: 'nubank', name: 'Nubank', color: '#820ad1', text: '#ffffff', icon: true, initials: 'Nu' },
  { key: 'itau', name: 'Itaú', color: '#ec7000', text: '#111111', icon: true, fullColor: true, initials: 'It' },
  { key: 'bradesco', name: 'Bradesco', color: '#cc092f', text: '#ffffff', icon: true, fullColor: true, initials: 'Br' },
  { key: 'santander', name: 'Santander', color: '#ec0000', text: '#ffffff', icon: true, fullColor: true, initials: 'Sa' },
  { key: 'bb', name: 'Banco do Brasil', color: '#fcfc30', text: '#111111', icon: true, fullColor: true, initials: 'BB' },
  { key: 'caixa', name: 'Caixa', color: '#005ca9', text: '#ffffff', icon: true, fullColor: true, initials: 'Cx' },
  { key: 'inter', name: 'Inter', color: '#ff7a00', text: '#111111', icon: true, fullColor: true, initials: 'In' },
  { key: 'c6', name: 'C6 Bank', color: '#242424', text: '#ffffff', icon: true, fullColor: true, initials: 'C6' },
  { key: 'mercadopago', name: 'Mercado Pago', color: '#00b1ea', text: '#111111', icon: true, fullColor: true, initials: 'MP' },
  { key: 'picpay', name: 'PicPay', color: '#21c25e', text: '#111111', icon: true, fullColor: true, initials: 'PP' },
  { key: 'pagbank', name: 'PagBank', color: '#ffc801', text: '#111111', icon: true, initials: 'PB' },
  { key: 'neon', name: 'Neon', color: '#00a5e3', text: '#111111', icon: false, initials: 'Ne' },
  { key: 'btg', name: 'BTG Pactual', color: '#05132a', text: '#ffffff', icon: false, initials: 'BTG' },
  { key: 'xp', name: 'XP', color: '#111111', text: '#ffffff', icon: false, initials: 'XP' },
  { key: 'sicredi', name: 'Sicredi', color: '#3fa110', text: '#111111', icon: false, initials: 'Si' },
  { key: 'sicoob', name: 'Sicoob', color: '#003641', text: '#ffffff', icon: false, initials: 'Sc' },
  { key: 'porto', name: 'Porto Bank', color: '#0046c0', text: '#ffffff', icon: false, initials: 'Po' },
  { key: 'carrefour', name: 'Carrefour', color: '#004e9f', text: '#ffffff', icon: true, initials: 'Ca' },
  { key: 'amazon', name: 'Amazon', color: '#ff9900', text: '#111111', icon: false, initials: 'Amz' },
  { key: 'amex', name: 'American Express', color: '#2e77bc', text: '#ffffff', icon: true, initials: 'Am' },
  { key: 'diners', name: 'Diners Club', color: '#004c97', text: '#ffffff', icon: true, initials: 'Di' },
  { key: 'visa', name: 'Visa', color: '#1a1f71', text: '#ffffff', icon: true, initials: 'Vi' },
  { key: 'mastercard', name: 'Mastercard', color: '#eb001b', text: '#ffffff', icon: true, initials: 'Mc' },
  { key: 'elo', name: 'Elo', color: '#000000', text: '#ffffff', icon: false, initials: 'Elo' },
  { key: 'hipercard', name: 'Hipercard', color: '#b3131b', text: '#ffffff', icon: false, initials: 'Hi' },
]

export const cardBrand = (key: string | null | undefined): CardBrand | undefined =>
  key ? CARD_BRANDS.find((b) => b.key === key) : undefined

/** Brands whose name contains the search, ignoring case and accents. */
export function searchBrands(query: string): CardBrand[] {
  const plain = (text: string) => text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase()
  const q = plain(query.trim())
  return q ? CARD_BRANDS.filter((b) => plain(b.name).includes(q)) : CARD_BRANDS
}
