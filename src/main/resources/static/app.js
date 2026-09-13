const startBtn = document.getElementById('start');
const stopBtn = document.getElementById('stop');
const statusEl = document.getElementById('recording');
const resultEl = document.getElementById('result');
const errorEl = document.getElementById('error');
const copyBtn = document.getElementById('copy');
const clearBtn = document.getElementById('clear');

let mediaRecorder;
let chunks = [];
let stream = null;

function showError(message) {
  errorEl.textContent = message;
  errorEl.hidden = false;
}

function clearError() {
  errorEl.textContent = '';
  errorEl.hidden = true;
}

function setResultText(text) {
  resultEl.textContent = text || '';
}

startBtn.onclick = async () => {
  clearError();
  setResultText('');

  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: true });
  } catch (err) {
    showError('Unable to access microphone: ' + err.message);
    return;
  }

  const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus') ? 'audio/webm;codecs=opus' : 'audio/webm';
  mediaRecorder = new MediaRecorder(stream, { mimeType });
  chunks = [];
  mediaRecorder.ondataavailable = e => {
    if (e.data && e.data.size > 0) chunks.push(e.data);
  };
  mediaRecorder.onstart = () => {
    statusEl.textContent = 'recording';
    startBtn.disabled = true;
    stopBtn.disabled = false;
    startBtn.classList.add('btn-primary');
  };
  mediaRecorder.onstop = async () => {
    statusEl.textContent = 'processing...';
    if (chunks.length === 0) {
      showError('No audio data captured. Please try again.');
      statusEl.textContent = 'idle';
      startBtn.disabled = false;
      stopBtn.disabled = true;
      if (stream) stream.getTracks().forEach(track => track.stop());
      return;
    }

    const blob = new Blob(chunks, { type: mediaRecorder.mimeType || 'audio/webm' });
    chunks = [];
    const fd = new FormData();
    fd.append('file', blob, 'recording.webm');
    try {
      const resp = await fetch('/api/v1/transcribe', { method: 'POST', body: fd });
      const json = await resp.json();
      if (resp.ok) {
        setResultText(json.text || '');
      } else {
        showError('Error: ' + (json.error || JSON.stringify(json)));
      }
    } catch (err) {
      showError('Network error: ' + err.message);
    } finally {
      statusEl.textContent = 'idle';
      startBtn.disabled = false;
      stopBtn.disabled = true;
      if (stream) stream.getTracks().forEach(track => track.stop());
    }
  };
  mediaRecorder.start();
};

stopBtn.onclick = () => {
  if (mediaRecorder && mediaRecorder.state === 'recording') {
    try { mediaRecorder.requestData(); } catch (err) {}
    mediaRecorder.stop();
  }
};

copyBtn?.addEventListener('click', async () => {
  try { await navigator.clipboard.writeText(resultEl.textContent || ''); } catch (e) { showError('Copy failed'); }
});

clearBtn?.addEventListener('click', () => { setResultText(''); clearError(); });
