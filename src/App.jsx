import React, { useState } from 'react'
import { createRoot } from 'react-dom/client';

import './App.css'

const initExtensionPoint = () => {
  const domNode = document.getElementById('root')
  const root = createRoot(domNode)

  root.render(<App />)
}

function App() {

  const [selectedSapSystem, setSelectedSapSystem] = useState("")
  const [sapSystems, setSapSystems] = useState([])

  const [selectedTemplate, setSelectedTemplate] = useState("")
  const [templates, setTemplates] = useState([])

  const [companyCodes, setCompanyCodes] = useState([])

  const [file, setFile] = useState(null);

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

  const fetchTemplates = () => {
    const url = window.location.pathname + `rest/templates`

    fetch(url, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json; charset=utf-8'
      },
    })
      .then((response) => {
        if (!response.ok) {
          throw new Error("Failed to fetch templates");
        }

        return response.json()
      })
      .then((data) => {
        setTemplates(data)
      })
  }

  const fetchCompanyCodes = () => {
    const url = window.location.pathname + `rest/companyCodes?sapSystem=${selectedSapSystem}`

    console.log("URL: " + url);

    fetch(url, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json; charset=utf-8'
      },
    })
      .then((response) => {
        if (!response.ok) {
          throw new Error(`Failed to fetch company codes for SAP system ${encodeURIComponent(selectedSapSystem)}`);
        }

        return response.json()
      })
      .then((data) => {
        setCompanyCodes(data)
      })
  }

  const submit = async () => {
    const url = window.location.pathname + "/rest/submit";

    console.log("URL: " + url);

    const formData = new FormData();
    if (file) {
      formData.append('file', file, file.pathname)
    }

    fetch(url, {
      method: 'POST',
      body: formData,
    })
      .then((response) => {
        if (!response.ok) {
          throw new Error("Failed to submit");
        }

        return response.json()
      })
      .then((data) => {

      })
  }

  const download = () => {
    const url = window.location.pathname + `/rest/downloadTemplate?template=${encodeURIComponent(selectedTemplate)}`;

    fetch(url)
      .then(response => {
        if (!response.ok) {
          throw new Error(`Failed to fetch file: ${response.status} ${response.statusText}`);
        }
        return response.json()
      })
      .then(templateUrl => {
        // Create a temporary link element
        const link = document.createElement('a')
        link.href = templateUrl
        link.download = selectedTemplate

        // Append the link to the body (required for some browsers)
        document.body.appendChild(link)

        // Trigger the download
        link.click()

        // Remove the link element
        document.body.removeChild(link)
      })
      .catch(error => {
        console.error('Error downloading file ${selectedTemplate}', error);
      })
  }

  const changeSelectedTemplate = (event) => {
    setSelectedTemplate(event.target.value);
  }

  const changeSelectedSapSystem = (event) => {
    setSelectedSapSystem(event.target.value);
  }

  const changeFile = (e) => {
    if (e.target.files) {
      setFile(e.target.files[0]);
    }
  }

  return (
    <>
      <div className='card'>
        <div>
          <button onClick={fetchTemplates}>
            Update Templates
          </button>
        </div>
        {templates.length > 0 &&
          <>
            <label>Template: </label>
            <select onChange={changeSelectedTemplate}>
              {templates.map(option =>
                <option value={option}>{option}</option>
              )}
            </select>
          </>
        }
        {!!selectedTemplate &&
          <button onClick={download}>Download</button>
        }
      </div>

      <div className='card'>
        <div>
          <button onClick={fetchSapSystems}>
            Update SAP Systems
          </button>
        </div>
        {sapSystems.length > 0 &&
          <>
            <div className='card'>
              <label>Select SAP System: </label>
              <select onChange={changeSelectedSapSystem}>
                {sapSystems.map(option =>
                  <option value={option}>{option}</option>
                )}
              </select>
            </div>
            <span>Selected SAP System: {selectedSapSystem}</span>
            {!!selectedSapSystem &&
              <button onClick={fetchCompanyCodes}>Get Company Codes</button>
            }
          </>
        }
      </div>

      <div className='card'>
        <label>Select file to upload</label>
        <input type="file" onChange={changeFile} />
      </div>
      <div className='card'>
        <button type="submit" onClick={submit}>Submit</button>
      </div>
    </>
  )
}

export default initExtensionPoint
