const startBtn = document.getElementById('start');
const stopBtn = document.getElementById('stop');
const statusEl = document.getElementById('recording');
const resultEl = document.getElementById('result');
const errorEl = document.getElementById('error');

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

startBtn.onclick = async () => {
  clearError();
  resultEl.textContent = '';

  const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
  mediaRecorder = new MediaRecorder(stream);
  mediaRecorder.ondataavailable = e => chunks.push(e.data);
  mediaRecorder.onstart = () => { statusEl.textContent = 'recording'; startBtn.disabled = true; stopBtn.disabled = false; };
  mediaRecorder.onstop = async () => {
    statusEl.textContent = 'processing...';
    const blob = new Blob(chunks, { type: 'audio/webm' });
    chunks = [];
    const fd = new FormData();
    fd.append('file', blob, 'recording.webm');
    try {
      const resp = await fetch('/api/v1/transcribe', { method: 'POST', body: fd });
      const json = await resp.json();
      if (resp.ok) {
        resultEl.textContent = json.text || '';
      } else {
        resultEl.textContent = 'Error: ' + (json.error || JSON.stringify(json));
      }
    } catch (err) {
      resultEl.textContent = 'Network error: ' + err.message;
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
