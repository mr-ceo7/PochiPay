/**
 * PochiPay Gateway Console — Dual API Testing Suite
 * Handles Socket.io events and REST calls for Business STK Push & SMS Verification
 */

document.addEventListener('DOMContentLoaded', () => {
  // --- DOM Elements ---
  const connectionForm = document.getElementById('connectionForm');
  const serverUrlInput = document.getElementById('serverUrl');
  const connectBtn = document.getElementById('connectBtn');
  const connectionBadge = document.getElementById('connectionBadge');
  const socketIdBadge = document.getElementById('socketIdBadge');

  const mockSimulatorToggle = document.getElementById('mockSimulatorToggle');
  const mockIndicator = document.getElementById('mockIndicator');

  // STK Push Elements
  const stkPushForm = document.getElementById('stkPushForm');
  const stkPhoneInput = document.getElementById('stkPhone');
  const stkAmountInput = document.getElementById('stkAmount');
  const stkReferenceInput = document.getElementById('stkReference');
  const stkRequestIdInput = document.getElementById('stkRequestId');
  const stkSubmitBtn = document.getElementById('stkSubmitBtn');
  const stkBtnText = document.getElementById('stkBtnText');
  const stkBtnSpinner = document.getElementById('stkBtnSpinner');

  const stkStatusBadge = document.getElementById('stkStatusBadge');
  const stkCustomerName = document.getElementById('stkCustomerName');
  const stkDuration = document.getElementById('stkDuration');
  const stkResultReqId = document.getElementById('stkResultReqId');
  const stkErrorDetail = document.getElementById('stkErrorDetail');
  const copyStkCurlBtn = document.getElementById('copyStkCurlBtn');
  const stkTimeAgo = document.getElementById('stkTimeAgo');

  // Verify Elements
  const verifyForm = document.getElementById('verifyForm');
  const verifyReferenceInput = document.getElementById('verifyReference');
  const verifyAmountInput = document.getElementById('verifyAmount');
  const verifySubmitBtn = document.getElementById('verifySubmitBtn');
  const verifyBtnText = document.getElementById('verifyBtnText');
  const verifyBtnSpinner = document.getElementById('verifyBtnSpinner');

  const verifyStatusBadge = document.getElementById('verifyStatusBadge');
  const verifyMatchedRef = document.getElementById('verifyMatchedRef');
  const verifyMatchedAmt = document.getElementById('verifyMatchedAmt');
  const verifySenderPhone = document.getElementById('verifySenderPhone');
  const verifyTimestamp = document.getElementById('verifyTimestamp');
  const copyVerifyCurlBtn = document.getElementById('copyVerifyCurlBtn');
  const verifyTimeAgo = document.getElementById('verifyTimeAgo');

  // Console Elements
  const consoleContainer = document.getElementById('consoleContainer');
  const logCounter = document.getElementById('logCounter');
  const clearLogsBtn = document.getElementById('clearLogsBtn');
  const filterBtns = document.querySelectorAll('.filter-btn');

  // State
  let socket = null;
  let isConnected = false;
  let pendingStkRequests = new Map();
  let pendingVerifyRequests = new Map();
  let totalEvents = 0;
  let currentFilter = 'all';

  // --- Logger Utility ---
  function addLog(category, direction, title, payload = null) {
    totalEvents++;
    logCounter.textContent = `${totalEvents} EVENT${totalEvents === 1 ? '' : 'S'}`;

    const now = new Date();
    const timeStr = now.toTimeString().split(' ')[0] + '.' + String(now.getMilliseconds()).padStart(3, '0');

    const entry = document.createElement('div');
    entry.className = `log-entry log-${category} text-xs font-mono space-y-1`;
    entry.dataset.category = category;

    let dirBadgeClass = 'text-[#8b949e]';
    if (direction === 'OUT') dirBadgeClass = 'text-[#38bdf8]';
    if (direction === 'IN') dirBadgeClass = 'text-[#34d399]';
    if (direction === 'SYS') dirBadgeClass = 'text-[#fbbf24]';
    if (direction === 'ERR') dirBadgeClass = 'text-[#f87171]';

    let html = `
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <span class="text-[10px] text-[#8b949e]">${timeStr}</span>
          <span class="text-[10px] font-bold ${dirBadgeClass}">[${direction}]</span>
          <span class="text-white font-medium">${escapeHtml(title)}</span>
        </div>
        <span class="text-[10px] uppercase tracking-wider text-[#8b949e]">${category}</span>
      </div>
    `;

    if (payload) {
      const jsonString = JSON.stringify(payload, null, 2);
      html += `<pre class="bg-[#0a0b0d] p-2 rounded border border-[#1f242c] overflow-x-auto text-[11px] text-[#cbd5e1] mt-1">${syntaxHighlight(jsonString)}</pre>`;
    }

    entry.innerHTML = html;

    if (currentFilter !== 'all' && currentFilter !== category) {
      entry.style.display = 'none';
    }

    consoleContainer.appendChild(entry);
    consoleContainer.scrollTop = consoleContainer.scrollHeight;
  }

  function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  function syntaxHighlight(json) {
    return json.replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g, (match) => {
      let cls = 'json-number';
      if (/^"/.test(match)) {
        if (/:$/.test(match)) {
          cls = 'json-key';
        } else {
          cls = 'json-string';
        }
      } else if (/true|false/.test(match)) {
        cls = 'json-boolean';
      } else if (/null/.test(match)) {
        cls = 'json-null';
      }
      return `<span class="${cls}">${match}</span>`;
    });
  }

  // --- Socket.io Connection Management ---
  function connectGateway(url) {
    if (socket) {
      socket.disconnect();
      socket = null;
    }

    connectionBadge.textContent = 'CONNECTING...';
    connectionBadge.className = 'text-[10px] font-mono uppercase tracking-wider px-2 py-1 rounded bg-[#16191f] text-[#fbbf24] border border-[#d97706]/40';
    addLog('system', 'SYS', `Connecting to Socket.io Gateway: ${url}`);

    try {
      socket = io(url, {
        transports: ['websocket', 'polling'],
        reconnection: true,
        reconnectionAttempts: 5,
        timeout: 10000
      });

      socket.on('connect', () => {
        isConnected = true;
        connectBtn.textContent = 'DISCONNECT';
        connectBtn.className = 'text-xs font-mono font-semibold uppercase tracking-wider px-3.5 py-2 rounded bg-[#991b1b] hover:bg-[#7f1d1d] text-white transition border border-[#b91c1c]';
        
        connectionBadge.textContent = 'GATEWAY ONLINE';
        connectionBadge.className = 'text-[10px] font-mono uppercase tracking-wider px-2 py-1 rounded bg-[#064e3b] text-[#34d399] border border-[#059669]';

        socketIdBadge.textContent = `ID: ${socket.id.slice(0, 8)}...`;
        socketIdBadge.classList.remove('hidden');

        addLog('system', 'IN', `Socket.io Connected Successfully (ID: ${socket.id})`, {
          socketId: socket.id,
          transport: socket.io.engine?.transport?.name || 'websocket',
          server: url
        });
      });

      socket.on('disconnect', (reason) => {
        isConnected = false;
        connectBtn.textContent = 'CONNECT';
        connectBtn.className = 'text-xs font-mono font-semibold uppercase tracking-wider px-3.5 py-2 rounded bg-[#1f242c] hover:bg-[#2b323d] text-white transition border border-[#2b323d]';

        connectionBadge.textContent = 'DISCONNECTED';
        connectionBadge.className = 'text-[10px] font-mono uppercase tracking-wider px-2 py-1 rounded bg-[#16191f] text-[#8b949e] border border-[#1f242c]';
        socketIdBadge.classList.add('hidden');

        addLog('system', 'SYS', `Gateway Disconnected: ${reason}`);
      });

      socket.on('connect_error', (error) => {
        addLog('error', 'ERR', `Connection Error: ${error.message}`, { error: error.toString() });
      });

      // Listen for STK Push Results from Android Device
      socket.on('stk_push_result', (data) => {
        handleIncomingStkResult(data);
      });

      // Listen for Payment Verification Results from Android Device
      socket.on('verification_result', (data) => {
        handleIncomingVerifyResult(data);
      });

      // Listen for STK Push Request broadcast (for multi-client awareness or mock simulator)
      socket.on('request_stk_push', (data) => {
        addLog('stk', 'IN', 'Received broadcast: request_stk_push', data);
        if (mockSimulatorToggle.checked) {
          triggerMockStkExecution(data);
        }
      });

      // Listen for Verification Request broadcast
      socket.on('request_verification', (data) => {
        addLog('verify', 'IN', 'Received broadcast: request_verification', data);
        if (mockSimulatorToggle.checked) {
          triggerMockVerifyExecution(data);
        }
      });

    } catch (err) {
      addLog('error', 'ERR', `Failed to initialize Socket.io: ${err.message}`);
    }
  }

  function disconnectGateway() {
    if (socket) {
      socket.disconnect();
      socket = null;
    }
    isConnected = false;
    connectBtn.textContent = 'CONNECT';
    connectBtn.className = 'text-xs font-mono font-semibold uppercase tracking-wider px-3.5 py-2 rounded bg-[#1f242c] hover:bg-[#2b323d] text-white transition border border-[#2b323d]';
    connectionBadge.textContent = 'DISCONNECTED';
    connectionBadge.className = 'text-[10px] font-mono uppercase tracking-wider px-2 py-1 rounded bg-[#16191f] text-[#8b949e] border border-[#1f242c]';
    socketIdBadge.classList.add('hidden');
  }

  connectionForm.addEventListener('submit', (e) => {
    e.preventDefault();
    if (isConnected) {
      disconnectGateway();
    } else {
      const url = serverUrlInput.value.trim();
      if (url) connectGateway(url);
    }
  });

  // --- Mock Simulator Mode ---
  mockSimulatorToggle.addEventListener('change', () => {
    if (mockSimulatorToggle.checked) {
      mockIndicator.textContent = 'ACTIVE';
      mockIndicator.className = 'text-[10px] uppercase font-mono px-1.5 py-0.5 rounded bg-[#064e3b] text-[#34d399] border border-[#059669]';
      addLog('system', 'SYS', 'PochiPay Phone Mock Simulator: ENABLED (Will auto-respond to STK & Verification requests)');
    } else {
      mockIndicator.textContent = 'OFF';
      mockIndicator.className = 'text-[10px] uppercase font-mono px-1.5 py-0.5 rounded bg-[#16191f] text-[#8b949e] border border-[#1f242c]';
      addLog('system', 'SYS', 'PochiPay Phone Mock Simulator: DISABLED');
    }
  });

  function triggerMockStkExecution(data) {
    addLog('stk', 'SYS', `[SIMULATOR] Device received STK dispatch for ${data.phone}. Running 7-step UI automation...`);
    setTimeout(() => {
      const sampleNames = ['JOHN K. MWANGI', 'MARY WANJIKU', 'PETER OCHIENG', 'SARAH CHEMUTAI'];
      const randomName = sampleNames[Math.floor(Math.random() * sampleNames.length)];
      const mockResult = {
        requestId: data.requestId || 'mock_' + Date.now(),
        success: true,
        customerName: randomName,
        errorMessage: null,
        reference: data.reference || 'REF-SIM'
      };

      if (socket && isConnected) {
        socket.emit('stk_push_result', mockResult);
      } else {
        handleIncomingStkResult(mockResult);
      }
    }, 2800);
  }

  function triggerMockVerifyExecution(data) {
    addLog('verify', 'SYS', `[SIMULATOR] Device querying local Room DB for M-PESA SMS with Ref ${data.reference}...`);
    setTimeout(() => {
      const mockResult = {
        reference: data.reference,
        verified: true,
        amount: data.amount || 100.0,
        sender: '254712***789',
        timestamp: Date.now() - 360000
      };

      if (socket && isConnected) {
        socket.emit('verification_result', mockResult);
      } else {
        handleIncomingVerifyResult(mockResult);
      }
    }, 1500);
  }

  // --- Quick Amount Chips ---
  document.querySelectorAll('.amount-chip').forEach(chip => {
    chip.addEventListener('click', () => {
      stkAmountInput.value = parseFloat(chip.dataset.amount).toFixed(2);
    });
  });

  // --- Verify Presets ---
  document.querySelectorAll('.verify-preset').forEach(preset => {
    preset.addEventListener('click', () => {
      verifyReferenceInput.value = preset.dataset.ref;
      verifyAmountInput.value = parseFloat(preset.dataset.amt).toFixed(2);
    });
  });

  // --- API 1: STK Push Dispatch ---
  stkPushForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const protocol = document.querySelector('input[name="stkProtocol"]:checked').value;
    const phone = stkPhoneInput.value.trim();
    const amount = parseFloat(stkAmountInput.value);
    const reference = stkReferenceInput.value.trim();
    const requestId = stkRequestIdInput.value.trim() || 'req_' + Date.now();

    if (!phone || isNaN(amount) || amount <= 0) {
      alert('Please enter a valid phone number and positive amount.');
      return;
    }

    const payload = {
      requestId,
      phone,
      amount,
      reference
    };

    // UI State: In-Flight
    setStkLoading(true);
    stkStatusBadge.textContent = 'DISPATCHING...';
    stkStatusBadge.className = 'text-[10px] uppercase px-2 py-0.5 rounded bg-[#16191f] text-[#38bdf8] border border-[#0284c7]/50';
    stkCustomerName.textContent = 'Awaiting device response...';
    stkDuration.textContent = 'In flight...';
    stkResultReqId.textContent = requestId;
    stkErrorDetail.textContent = '—';

    const startTime = performance.now();
    pendingStkRequests.set(requestId, { startTime, payload });

    if (protocol === 'socket') {
      if (!isConnected && !mockSimulatorToggle.checked) {
        addLog('error', 'ERR', 'Cannot emit socket event: Gateway is not connected! Click CONNECT above or enable phone simulator.');
        setStkLoading(false);
        stkStatusBadge.textContent = 'DISCONNECTED';
        stkStatusBadge.className = 'text-[10px] uppercase px-2 py-0.5 rounded bg-[#991b1b]/30 text-[#f87171] border border-[#dc2626]';
        return;
      }

      addLog('stk', 'OUT', `Emitting event 'request_stk_push' [${requestId}]`, payload);
      
      if (socket && isConnected) {
        socket.emit('request_stk_push', payload);
      }

      if (mockSimulatorToggle.checked && !isConnected) {
        triggerMockStkExecution(payload);
      }

      // Safety timeout: 45s
      setTimeout(() => {
        if (pendingStkRequests.has(requestId)) {
          pendingStkRequests.delete(requestId);
          setStkLoading(false);
          stkStatusBadge.textContent = 'TIMEOUT (NO RESPONSE)';
          stkStatusBadge.className = 'text-[10px] uppercase px-2 py-0.5 rounded bg-[#991b1b]/30 text-[#f87171] border border-[#dc2626]';
          stkErrorDetail.textContent = 'Phone device did not return stk_push_result within 45 seconds.';
          addLog('error', 'ERR', `STK Push Request ${requestId} timed out after 45s`);
        }
      }, 45000);

    } else {
      // REST Protocol
      const serverUrl = serverUrlInput.value.trim().replace(/\/$/, '');
      const endpoint = `${serverUrl}/api/pending-stk`;
      addLog('stk', 'OUT', `REST POST ${endpoint}`, payload);

      try {
        const res = await fetch(endpoint, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });

        const elapsed = ((performance.now() - startTime) / 1000).toFixed(2);
        const data = await res.json().catch(() => ({ status: res.status }));

        setStkLoading(false);
        if (res.ok) {
          stkStatusBadge.textContent = 'REST QUEUED';
          stkStatusBadge.className = 'text-[10px] uppercase px-2 py-0.5 rounded bg-[#064e3b] text-[#34d399] border border-[#059669]';
          stkDuration.textContent = `${elapsed}s`;
          addLog('stk', 'IN', `REST Response (${res.status} OK)`, data);
        } else {
          stkStatusBadge.textContent = `HTTP ${res.status}`;
          stkStatusBadge.className = 'text-[10px] uppercase px-2 py-0.5 rounded bg-[#991b1b]/30 text-[#f87171] border border-[#dc2626]';
          stkErrorDetail.textContent = data.message || 'REST request failed';
          addLog('error', 'ERR', `REST Error (${res.status})`, data);
        }
      } catch (err) {
        setStkLoading(false);
        stkStatusBadge.textContent = 'NETWORK ERROR';
        stkStatusBadge.className = 'text-[10px] uppercase px-2 py-0.5 rounded bg-[#991b1b]/30 text-[#f87171] border border-[#dc2626]';
        stkErrorDetail.textContent = err.message;
        addLog('error', 'ERR', `REST Fetch Exception: ${err.message}`);
      }
    }
  });

  function handleIncomingStkResult(data) {
    addLog('stk', 'IN', `Received 'stk_push_result' for req ${data.requestId || data.id}`, data);

    const pending = pendingStkRequests.get(data.requestId);
    let durationText = '—';
    if (pending) {
      const elapsed = ((performance.now() - pending.startTime) / 1000).toFixed(2);
      durationText = `${elapsed}s`;
      pendingStkRequests.delete(data.requestId);
    }

    setStkLoading(false);
    stkResultReqId.textContent = data.requestId || '—';
    stkDuration.textContent = durationText;
    stkTimeAgo.textContent = new Date().toLocaleTimeString();

    if (data.success) {
      stkStatusBadge.textContent = 'PROMPT INITIATED';
      stkStatusBadge.className = 'text-[10px] uppercase px-2 py-0.5 rounded bg-[#064e3b] text-[#34d399] border border-[#059669]';
      stkCustomerName.textContent = data.customerName || 'EXTRACTED (UNNAMED)';
      stkCustomerName.className = 'text-[#34d399] font-bold';
      stkErrorDetail.textContent = 'None (Success)';
    } else {
      stkStatusBadge.textContent = 'AUTOMATION FAILED';
      stkStatusBadge.className = 'text-[10px] uppercase px-2 py-0.5 rounded bg-[#991b1b]/30 text-[#f87171] border border-[#dc2626]';
      stkCustomerName.textContent = '—';
      stkCustomerName.className = 'text-[#8b949e] font-semibold';
      stkErrorDetail.textContent = data.errorMessage || 'Unknown automation failure on device';
    }
  }

  function setStkLoading(loading) {
    if (loading) {
      stkSubmitBtn.disabled = true;
      stkBtnSpinner.classList.remove('hidden');
      stkBtnText.textContent = 'DISPATCHING TO PHONE...';
    } else {
      stkSubmitBtn.disabled = false;
      stkBtnSpinner.classList.add('hidden');
      stkBtnText.textContent = 'DISPATCH STK PROMPT';
    }
  }

  // --- API 2: Verification Query ---
  verifyForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const protocol = document.querySelector('input[name="verifyProtocol"]:checked').value;
    const reference = verifyReferenceInput.value.trim().toUpperCase();
    const amount = parseFloat(verifyAmountInput.value);

    if (!reference || isNaN(amount) || amount <= 0) {
      alert('Please enter a valid reference and positive amount.');
      return;
    }

    const payload = { reference, amount };

    setVerifyLoading(true);
    verifyStatusBadge.textContent = 'QUERYING...';
    verifyStatusBadge.className = 'text-[10px] uppercase px-2 py-0.5 rounded bg-[#16191f] text-[#38bdf8] border border-[#0284c7]/50';
    verifyMatchedRef.textContent = reference;
    verifyMatchedAmt.textContent = 'Querying Room DB...';
    verifySenderPhone.textContent = '—';
    verifyTimestamp.textContent = '—';

    const startTime = performance.now();
    pendingVerifyRequests.set(reference, { startTime, payload });

    if (protocol === 'socket') {
      if (!isConnected && !mockSimulatorToggle.checked) {
        addLog('error', 'ERR', 'Cannot emit socket event: Gateway is not connected! Click CONNECT above or enable phone simulator.');
        setVerifyLoading(false);
        verifyStatusBadge.textContent = 'DISCONNECTED';
        verifyStatusBadge.className = 'text-[10px] uppercase px-2 py-0.5 rounded bg-[#991b1b]/30 text-[#f87171] border border-[#dc2626]';
        return;
      }

      addLog('verify', 'OUT', `Emitting event 'request_verification' [${reference}]`, payload);

      if (socket && isConnected) {
        socket.emit('request_verification', payload);
      }

      if (mockSimulatorToggle.checked && !isConnected) {
        triggerMockVerifyExecution(payload);
      }

      // Timeout safety: 30s
      setTimeout(() => {
        if (pendingVerifyRequests.has(reference)) {
          pendingVerifyRequests.delete(reference);
          setVerifyLoading(false);
          verifyStatusBadge.textContent = 'TIMEOUT';
          verifyStatusBadge.className = 'text-[10px] uppercase px-2 py-0.5 rounded bg-[#991b1b]/30 text-[#f87171] border border-[#dc2626]';
          addLog('error', 'ERR', `Verification request for ${reference} timed out after 30s`);
        }
      }, 30000);

    } else {
      // REST Protocol
      const serverUrl = serverUrlInput.value.trim().replace(/\/$/, '');
      const endpoint = `${serverUrl}/api/verify`;
      addLog('verify', 'OUT', `REST POST ${endpoint}`, payload);

      try {
        const res = await fetch(endpoint, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });

        const data = await res.json().catch(() => ({ status: res.status }));
        setVerifyLoading(false);

        if (res.ok) {
          addLog('verify', 'IN', `REST Response (${res.status} OK)`, data);
          verifyStatusBadge.textContent = 'REST BROADCASTED';
          verifyStatusBadge.className = 'text-[10px] uppercase px-2 py-0.5 rounded bg-[#064e3b] text-[#34d399] border border-[#059669]';
          verifyMatchedRef.textContent = payload.reference;
          verifyMatchedAmt.textContent = `KES ${payload.amount.toFixed(2)}`;
          verifySenderPhone.textContent = 'Broadcasted via REST';
          verifyTimestamp.textContent = new Date().toLocaleTimeString();
        } else {
          verifyStatusBadge.textContent = `HTTP ${res.status}`;
          verifyStatusBadge.className = 'text-[10px] uppercase px-2 py-0.5 rounded bg-[#991b1b]/30 text-[#f87171] border border-[#dc2626]';
          addLog('error', 'ERR', `REST Verification Error (${res.status})`, data);
        }
      } catch (err) {
        setVerifyLoading(false);
        verifyStatusBadge.textContent = 'NETWORK ERROR';
        verifyStatusBadge.className = 'text-[10px] uppercase px-2 py-0.5 rounded bg-[#991b1b]/30 text-[#f87171] border border-[#dc2626]';
        addLog('error', 'ERR', `REST Verification Fetch Exception: ${err.message}`);
      }
    }
  });

  function handleIncomingVerifyResult(data) {
    addLog('verify', 'IN', `Received 'verification_result' for Ref ${data.reference}`, data);

    setVerifyLoading(false);
    verifyTimeAgo.textContent = new Date().toLocaleTimeString();

    if (data.verified) {
      verifyStatusBadge.textContent = 'VERIFIED';
      verifyStatusBadge.className = 'text-[10px] uppercase px-2 py-0.5 rounded bg-[#064e3b] text-[#34d399] border border-[#059669]';
      verifyMatchedRef.textContent = data.reference;
      verifyMatchedAmt.textContent = `KES ${(data.amount || 0).toFixed(2)}`;
      verifySenderPhone.textContent = data.sender || 'Sender captured';
      verifyTimestamp.textContent = data.timestamp ? new Date(data.timestamp).toLocaleString() : 'Just now';
    } else {
      verifyStatusBadge.textContent = 'NOT FOUND';
      verifyStatusBadge.className = 'text-[10px] uppercase px-2 py-0.5 rounded bg-[#991b1b]/30 text-[#f87171] border border-[#dc2626]';
      verifyMatchedRef.textContent = data.reference;
      verifyMatchedAmt.textContent = 'No matching SMS record in local DB';
      verifySenderPhone.textContent = '—';
      verifyTimestamp.textContent = '—';
    }
  }

  function setVerifyLoading(loading) {
    if (loading) {
      verifySubmitBtn.disabled = true;
      verifyBtnSpinner.classList.remove('hidden');
      verifyBtnText.textContent = 'QUERYING RECEIPT...';
    } else {
      verifySubmitBtn.disabled = false;
      verifyBtnSpinner.classList.add('hidden');
      verifyBtnText.textContent = 'QUERY RECEIPT VERIFICATION';
    }
  }

  // --- Copy cURL Snippets ---
  copyStkCurlBtn.addEventListener('click', () => {
    const serverUrl = serverUrlInput.value.trim().replace(/\/$/, '');
    const phone = stkPhoneInput.value.trim();
    const amount = stkAmountInput.value.trim();
    const reference = stkReferenceInput.value.trim();
    const curl = `curl -X POST "${serverUrl}/api/pending-stk" \\\n  -H "Content-Type: application/json" \\\n  -d '{"phone": "${phone}", "amount": ${amount}, "reference": "${reference}"}'`;
    navigator.clipboard.writeText(curl).then(() => {
      copyStkCurlBtn.textContent = 'COPIED TO CLIPBOARD!';
      setTimeout(() => { copyStkCurlBtn.textContent = 'COPY CURL COMMAND'; }, 2000);
    });
  });

  copyVerifyCurlBtn.addEventListener('click', () => {
    const serverUrl = serverUrlInput.value.trim().replace(/\/$/, '');
    const ref = verifyReferenceInput.value.trim();
    const amt = verifyAmountInput.value.trim();
    const curl = `curl -X POST "${serverUrl}/api/verify" \\\n  -H "Content-Type: application/json" \\\n  -d '{"reference": "${ref}", "amount": ${amt}}'`;
    navigator.clipboard.writeText(curl).then(() => {
      copyVerifyCurlBtn.textContent = 'COPIED TO CLIPBOARD!';
      setTimeout(() => { copyVerifyCurlBtn.textContent = 'COPY CURL COMMAND'; }, 2000);
    });
  });

  // --- Console Logs Controls ---
  clearLogsBtn.addEventListener('click', () => {
    consoleContainer.innerHTML = '<div class="text-[#8b949e] text-[11px] italic">[Console cleared.]</div>';
    totalEvents = 0;
    logCounter.textContent = '0 EVENTS';
  });

  filterBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      filterBtns.forEach(b => {
        b.className = 'filter-btn px-2 py-0.5 rounded text-[#8b949e] hover:text-white';
      });
      btn.className = 'filter-btn px-2 py-0.5 rounded bg-[#1f242c] text-white';

      currentFilter = btn.dataset.filter;
      document.querySelectorAll('.log-entry').forEach(entry => {
        if (currentFilter === 'all' || entry.dataset.category === currentFilter) {
          entry.style.display = 'block';
        } else {
          entry.style.display = 'none';
        }
      });
    });
  });

  // Auto-connect on page load if default URL is present
  if (serverUrlInput.value) {
    connectGateway(serverUrlInput.value.trim());
  }
});
