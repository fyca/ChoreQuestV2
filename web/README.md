# ChoreQuest Web App

ChoreQuest web application built with React, TypeScript, and Vite.

## Tech Stack

- **Framework:** React 18
- **Language:** TypeScript
- **Build Tool:** Vite
- **Styling:** Tailwind CSS
- **State Management:** Zustand + TanStack Query
- **Routing:** React Router
- **Authentication:** @react-oauth/google
- **QR Code:** html5-qrcode (scanning), qrcode.react (generation)
- **Animations:** Framer Motion, canvas-confetti

## Getting Started

### Prerequisites

- Node.js 18+ 
- npm or yarn

### Installation

```bash
# Install dependencies
npm install

# Create .env file
cp .env.example .env
# Edit .env and add your Google Client ID and Apps Script URL

# Start development server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

## Environment Variables

Create a `.env` file in the web directory with:

```
VITE_GOOGLE_CLIENT_ID=your_google_client_id_here
VITE_APPS_SCRIPT_URL=your_apps_script_url_here
```

## Project Structure

```
src/
├── components/         # React components
│   ├── auth/          # Authentication components
│   ├── parent/        # Parent dashboard components
│   ├── child/         # Child dashboard components
│   ├── chores/        # Chore management components
│   ├── rewards/       # Rewards components
│   ├── activitylog/   # Activity log components
│   ├── games/         # Games components
│   └── common/        # Shared components
├── hooks/             # Custom React hooks
├── services/          # API services
├── types/             # TypeScript type definitions
├── utils/             # Utility functions
├── App.tsx            # Main app component
└── main.tsx           # Entry point
```

## Features

- QR code authentication for family members
- Colorful, kid-friendly UI
- Celebration animations on chore completion
- Responsive design for mobile and desktop
- Offline mode with local storage
- Google Drive integration
- Parent and child dashboards
- Rewards marketplace
- Activity log
- Mini-games for children

## Development

```bash
# Run linting
npm run lint

# Type check
npx tsc --noEmit
```

## Deployment

The app can be deployed to:
- Vercel
- Netlify
- Firebase Hosting
- Any static hosting service

```bash
npm run build
# Deploy the dist/ folder
```
