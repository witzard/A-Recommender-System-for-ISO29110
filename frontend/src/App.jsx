import { useState } from 'react';
import axios from 'axios';
import Button from '@mui/material/Button';

function App() {
  const [data, setData] = useState('');
  const [loading, setLoading] = useState(false);

  const handleFetchData = async () => {
    setLoading(true);
    try {
      // ยิงคำขอ GET ไปยัง Spring Boot
      const response = await axios.get('http://localhost:8080/api/tasks');
      // เก็บข้อมูลที่ได้มาในรูปแบบข้อความ JSON
      setData(JSON.stringify(response.data, null, 2));
    } catch (error) {
      console.error('เกิดข้อผิดพลาด:', error);
      setData('เชื่อมต่อไม่สำเร็จ: ' + error.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ padding: '2rem', textAlign: 'center' }}>
      <h1>Recommendation System</h1>
      <Button 
        variant="contained" 
        color="primary" 
        onClick={handleFetchData}
        disabled={loading}
      >
        {loading ? 'กำลังโหลดข้อมูล...' : 'ดึงข้อมูลจาก Backend'}
      </Button>

      {data && (
        <pre style={{ 
          marginTop: '1.5rem', 
          textAlign: 'left', 
          background: '#f4f4f4', 
          padding: '1rem',
          borderRadius: '8px',
          overflowX: 'auto'
        }}>
          {data}
        </pre>
      )}
    </div>
  );
}

export default App;