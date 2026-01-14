/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        // Kid-friendly colorful palette
        'sky-blue': '#4A90E2',
        'sunshine-yellow': '#FFD93D',
        'grass-green': '#6BCF7C',
        'coral-orange': '#FF6B6B',
        'purple': '#9B59B6',
        'pink': '#FF69B4',
        
        // Chore category colors
        'chore-red': '#E74C3C',
        'chore-orange': '#E67E22',
        'chore-yellow': '#F39C12',
        'chore-green': '#27AE60',
        'chore-blue': '#3498DB',
        'chore-purple': '#8E44AD',
        'chore-pink': '#E91E63',
        
        // Status colors
        'status-pending': '#95A5A6',
        'status-progress': '#3498DB',
        'status-completed': '#27AE60',
        'status-verified': '#16A085',
        'status-overdue': '#E74C3C',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        display: ['Quicksand', 'sans-serif'],
      },
      animation: {
        'bounce-slow': 'bounce 2s infinite',
        'pulse-slow': 'pulse 3s infinite',
        'wiggle': 'wiggle 1s ease-in-out infinite',
      },
      keyframes: {
        wiggle: {
          '0%, 100%': { transform: 'rotate(-3deg)' },
          '50%': { transform: 'rotate(3deg)' },
        },
      },
    },
  },
  plugins: [],
}
