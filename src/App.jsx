import { useState } from 'react'
import { createRoot } from 'react-dom/client';

import './App.css'

const initExtensionPoint = () => {
  const domNode = document.getElementById('root')
  const root = createRoot(domNode)

  root.render(<App />);
}

function App() {
  const [selectedSapSystem, setSelectedSapSystem] = useState("")
  const [sapSystems, setSapSystems] = useState([])
  const [companyCodes, setCompanyCodes] = useState([])

  const fetchSapSystems = () => {
    const url = window.location.pathname + "rest/sapSystems"

    fetch(url, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json; charset=utf-8'
      },
    }).then((response) => response.json())
      .then((data) => {
        setSapSystems(data)
      })
  }

  const fetchCompanyCodes = () => {
    const url = window.location.pathname + "rest/companyCodes?sapSystem=REDWOOD.GA1"

    console.log("URL: " + url);

    fetch(url, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json; charset=utf-8'
      },
    }).then((response) => response.json())
      .then((data) => {
        setCompanyCodes(data)
      })
  }

  const submit = async () => {
    const url = window.location.pathname + "/rest/submit";

    console.log("URL: " + url);

    fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json; charset=utf-8'
      },
      body: JSON.stringify(newProduct)
    }).then((response) => response.json())
      .then((data) => {
        setCompanyCodes(data)
      })
  }

  const upload = (file) => {
    const url = window.location.pathname + "/rest/upload";

    fetch(url, {
      method: 'POST',
      body: file
    }).then(
      response => response.json()
    ).then(
      success => console.log(success)
    ).catch(
      error => console.log(error)
    );
  }

  const changeSelectedSapSystem = (event) => {
    setSelectedSapSystem(event.target.value);
  };

  return (
    <>
      <div className='card'>
        <button onClick={fetchSapSystems}>
          Fetch SAP Systems
        </button>
      </div>
      {sapSystems.length > 0 &&
        <>
          <div className='card'>
            <label>SAP System: </label>
            <select onChange={changeSelectedSapSystem}>
              {sapSystems.map(option =>
                <option value={option}>{option}</option>
              )}
            </select>
          </div>
          <span>Selected SAP System: {selectedSapSystem}</span>
        </>
      }
      <div className='card'>
        <label>Select file to upload</label>
        <input type="file" />
      </div>
      <div className='card'>
        <button type="submit">Submit</button>
      </div>
    </>
  )
}

export default initExtensionPoint
