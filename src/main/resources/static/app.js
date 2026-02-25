const dropZone = document.getElementById('drop-zone');
const fileInput = document.getElementById('file-input');
const fileDetails = document.getElementById('file-details');
const fileNameDisplay = document.getElementById('fileName');
const fileNameText = document.getElementById('file-name');
const btnRemove = document.getElementById('btn-remove');
const btnCompress = document.getElementById('btn-compress');
const uploadError = document.getElementById('upload-error');

const uploadSection = document.getElementById('upload-section');
const statusSection = document.getElementById('status-section');
const statusTitle = document.getElementById('status-title');
const statusMessage = document.getElementById('status-message');
const spinner = document.getElementById('spinner');
const statusIcon = document.getElementById('status-icon');
const progressContainer = document.getElementById('progress-container');
const progressFill = document.getElementById('progress-fill');
const processedCount = document.getElementById('processed-count');
const totalCount = document.getElementById('total-count');
const btnDownload = document.getElementById('btn-download');
const btnReset = document.getElementById('btn-reset');

let selectedFile = null;
let currentJobId = null;
let pollInterval = null;

// Drag and drop events
['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
    dropZone.addEventListener(eventName, preventDefaults, false);
});

function preventDefaults(e) {
    e.preventDefault();
    e.stopPropagation();
}

['dragenter', 'dragover'].forEach(eventName => {
    dropZone.addEventListener(eventName, highlight, false);
});

['dragleave', 'drop'].forEach(eventName => {
    dropZone.addEventListener(eventName, unhighlight, false);
});

function highlight() {
    dropZone.classList.add('dragover');
}

function unhighlight() {
    dropZone.classList.remove('dragover');
}

dropZone.addEventListener('drop', handleDrop, false);

function handleDrop(e) {
    const dt = e.dataTransfer;
    const files = dt.files;
    handleFiles(files);
}

dropZone.addEventListener('click', () => {
    fileInput.click();
});

fileInput.addEventListener('change', function () {
    handleFiles(this.files);
});

function handleFiles(files) {
    if (files.length === 0) return;

    uploadError.classList.add('hidden');
    const file = files[0];

    if (!file.name.toLowerCase().endsWith('.zip')) {
        showError("Please upload a .zip file");
        return;
    }

    selectedFile = file;
    fileNameText.textContent = file.name;
    dropZone.classList.add('hidden');
    fileDetails.classList.remove('hidden');
    checkSubmitStatus();
}

btnRemove.addEventListener('click', () => {
    selectedFile = null;
    fileInput.value = '';
    dropZone.classList.remove('hidden');
    fileDetails.classList.add('hidden');
    checkSubmitStatus();
});

function checkSubmitStatus() {
    if (selectedFile) {
        btnCompress.removeAttribute('disabled');
    } else {
        btnCompress.setAttribute('disabled', 'true');
    }
}

function showError(msg) {
    uploadError.textContent = msg;
    uploadError.classList.remove('hidden');
}

btnCompress.addEventListener('click', () => {
    if (!selectedFile) return;

    uploadSection.classList.add('hidden');
    statusSection.classList.remove('hidden');
    resetStatusUI();

    const formData = new FormData();
    formData.append('file', selectedFile);

    fetch('/api/dicom/upload-and-compress', {
        method: 'POST',
        body: formData
    })
        .then(response => {
            if (!response.ok) {
                return response.json().then(err => { throw new Error(err.error || 'Upload failed') });
            }
            return response.json();
        })
        .then(data => {
            currentJobId = data.jobId;
            statusTitle.textContent = "Compressing DICOM Files";
            statusMessage.textContent = "We are extracting and compressing your files. This may take a while.";
            startPolling();
        })
        .catch(error => {
            showStatusError(error.message);
        });
});

function startPolling() {
    if (pollInterval) clearInterval(pollInterval);

    pollInterval = setInterval(() => {
        fetch(`/api/dicom/status/${currentJobId}`)
            .then(res => res.json())
            .then(data => {
                updateProgress(data);

                if (data.status === 'COMPLETED') {
                    clearInterval(pollInterval);
                    showSuccess();
                } else if (data.status === 'FAILED') {
                    clearInterval(pollInterval);
                    showStatusError(data.errorMsg || 'Compression failed');
                }
            })
            .catch(err => {
                console.error("Polling error", err);
            });
    }, 2000); // Check every 2 seconds
}

function updateProgress(data) {
    if (data.totalFiles > 0) {
        progressContainer.classList.remove('hidden');
        processedCount.textContent = data.processedCount;
        totalCount.textContent = data.totalFiles;

        const percentage = Math.round((data.processedCount / data.totalFiles) * 100);
        progressFill.style.width = `${percentage}%`;
    }
}

function showSuccess() {
    spinner.classList.add('hidden');
    statusIcon.classList.remove('hidden');
    statusIcon.className = 'status-icon success';
    statusIcon.innerHTML = '&#10003;'; // Checkmark

    statusTitle.textContent = "Compression Complete!";
    statusMessage.textContent = "Your files have been successfully compressed and re-packaged.";

    btnDownload.href = `/api/dicom/download/${currentJobId}`;
    btnDownload.classList.remove('hidden');
}

function showStatusError(msg) {
    spinner.classList.add('hidden');
    progressContainer.classList.add('hidden');

    statusIcon.classList.remove('hidden');
    statusIcon.className = 'status-icon error';
    statusIcon.innerHTML = '&#10007;'; // X mark

    statusTitle.textContent = "Error";
    statusMessage.textContent = msg;
}

function resetStatusUI() {
    spinner.classList.remove('hidden');
    statusIcon.classList.add('hidden');
    progressContainer.classList.add('hidden');
    btnDownload.classList.add('hidden');
    progressFill.style.width = '0%';
    statusTitle.textContent = "Uploading...";
    statusMessage.textContent = "Please wait while your file is submitted.";
}

btnReset.addEventListener('click', () => {
    btnRemove.click();

    uploadSection.classList.remove('hidden');
    statusSection.classList.add('hidden');

    if (pollInterval) {
        clearInterval(pollInterval);
    }
});
