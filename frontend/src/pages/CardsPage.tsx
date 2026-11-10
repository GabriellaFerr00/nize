import { CatalogManager } from '../components/CatalogManager'

export function CardsPage() {
  return <CatalogManager kind="cards" fields={{ dueDay: true, brand: true }} canDelete />
}
