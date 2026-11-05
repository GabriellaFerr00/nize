import { useTranslation } from 'react-i18next'

type Size = 'sm' | 'md' | 'lg'

/**
 * Height of the logo; the width follows the image (about 2:1). Below 360px the small one shows only the green
 * square with the "N" (cropped from the same image), so the phone header fits at 320px.
 */
const height: Record<Size, string> = {
  sm: 'h-9 max-[359px]:aspect-square max-[359px]:object-cover max-[359px]:object-left',
  md: 'h-11',
  lg: 'h-16',
}

const LIGHT_BACKGROUND = '/logoPadrao/nize-logo-n-bloco-verde-1000.png'
// same drawing with only the word "ize" in off-white, so it reads on the dark background (D-20)
const DARK_BACKGROUND = '/logoPadrao/nize-logo-n-bloco-verde-fundo-escuro-1000.png'

/**
 * The standard Nize logo (rounded green block with the "N" plus "ize"), from public/logoPadrao. Only one of the two images
 * is displayed, following the same theme as the color tokens; the hidden one is out of the accessibility tree.
 */
export function Logo({ size = 'md' }: { size?: Size }) {
  const { t } = useTranslation()
  const common = `${height[size]} w-auto max-w-none select-none`
  return (
    <span className="flex shrink-0 items-center">
      <img src={LIGHT_BACKGROUND} alt={t('app.name')} width={1000} height={515} className={`${common} dark:hidden`} />
      <img src={DARK_BACKGROUND} alt={t('app.name')} width={1000} height={515} className={`${common} hidden dark:block`} />
    </span>
  )
}
