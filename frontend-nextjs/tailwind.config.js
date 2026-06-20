module.exports = {
  content: [
    './src/app/**/*.{js,ts,jsx,tsx}',
    './src/components/**/*.{js,ts,jsx,tsx}',
  ],
  theme: {
    extend: {
      colors: {
        ink: '#16261F',
        paper: '#F6F4EC',
        clinic: {
          50: '#EEF5F1',
          100: '#D9E9DF',
          300: '#9CC4AC',
          500: '#3F7D5C',
          600: '#306249',
          700: '#234936',
        },
        clay: '#C76A4D',
      },
      fontFamily: {
        display: ['"Source Serif 4"', 'Georgia', 'serif'],
        sans: ['"Inter"', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
};
