import { BrowserRouter as Router } from 'react-router-dom'
import './App.css'

function App() {
  return (
    <Router>
      <div className="min-h-screen bg-gradient-to-br from-sky-blue/10 via-purple/10 to-pink/10">
        <div className="container mx-auto px-4 py-8">
          <div className="text-center">
            <h1 className="text-6xl font-display font-bold text-sky-blue mb-4">
              ChoreQuest
            </h1>
            <p className="text-2xl text-gray-700 mb-8">
              Making chores fun for the whole family! 🎉
            </p>
            <div className="card max-w-md mx-auto">
              <p className="text-gray-600">
                The app is being built... Stay tuned!
              </p>
            </div>
          </div>
        </div>
      </div>
    </Router>
  )
}

export default App
