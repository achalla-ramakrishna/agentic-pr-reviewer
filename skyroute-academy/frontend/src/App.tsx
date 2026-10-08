import { Link, Route, Routes, useNavigate } from 'react-router-dom';
import { session } from './api/client';
import { BookPage } from './pages/BookPage';
import { LoginPage } from './pages/LoginPage';
import { MyBookingsPage } from './pages/MyBookingsPage';
import { SearchPage } from './pages/SearchPage';

export function App() {
  const navigate = useNavigate();
  const user = session.get();
  return (
    <>
      <header>
        <h1>✈ SkyRoute</h1>
        <nav>
          <Link to="/">Search</Link>
          <Link to="/bookings">My bookings</Link>
          {user ? (
            <button className="link" onClick={() => { session.clear(); navigate('/login'); }}>Sign out {user.username}</button>
          ) : (
            <Link to="/login">Sign in</Link>
          )}
        </nav>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<SearchPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/book" element={<BookPage />} />
          <Route path="/bookings" element={<MyBookingsPage />} />
        </Routes>
      </main>
    </>
  );
}
