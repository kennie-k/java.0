import type { Config } from 'tailwindcss'
const config: Config = {
  content: ['./app/**/*.{ts,tsx}','./components/**/*.{ts,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        gold: {
          DEFAULT:'#C9A227','50':'#FCFAF2','100':'#F8F0DC','200':'#F0E3BE',
          '300':'#E6D08F','400':'#D9BC66','500':'#C9A227','600':'#AD8620',
          '700':'#8C6B1A','800':'#6B5114','900':'#4A380D',
        },
      },
      fontFamily: {
        display: ['var(--font-fraunces)','serif'],
        sans:    ['var(--font-manrope)','sans-serif'],
      },
      boxShadow: {
        gold: '0 8px 24px -8px rgba(201,162,39,0.45)',
      },
      animation: {
        'fade-in':'fadeIn .25s ease forwards',
        'slide-up':'slideUp .3s ease forwards',
        'shimmer':'shimmer 1.5s infinite',
      },
      keyframes: {
        fadeIn:  {from:{opacity:'0',transform:'translateY(6px)'},to:{opacity:'1',transform:'translateY(0)'}},
        slideUp: {from:{opacity:'0',transform:'translateY(16px)'},to:{opacity:'1',transform:'translateY(0)'}},
        shimmer: {'0%':{backgroundPosition:'-200% 0'},'100%':{backgroundPosition:'200% 0'}},
      },
    },
  },
  plugins:[],
}
export default config
