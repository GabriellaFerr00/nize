import { CatalogManager } from '../components/CatalogManager'

export function CategoriesPage() {
  return <CatalogManager kind="categories" fields={{ description: true }} canDelete />
}
