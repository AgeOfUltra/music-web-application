// Function to show custom error toast
function showErrorToast(message) {
    const toastContainer = document.body;
    const toastHTML = `
        <div class="toast-notification error-toast" style="display: flex;">
            <div class="toast-content">
                <i class="bi bi-exclamation-circle"></i>
                <span>${message}</span>
            </div>
        </div>
    `;

    const toastElement = document.createElement('div');
    toastElement.innerHTML = toastHTML;
    const toast = toastElement.querySelector('.toast-notification');
    toastContainer.appendChild(toast);

    setTimeout(() => {
        toast.style.animation = 'slideOutRight 0.4s ease forwards';
        setTimeout(() => {
            toast.remove();
        }, 400);
    }, 3000);
}

// File upload handling
const fileInput = document.getElementById('fileInput');
const fileNameDisplay = document.getElementById('fileNameDisplay');
const uploadPlaceholder = document.querySelector('.upload-placeholder');
const uploadForm = document.getElementById('uploadForm');

// Handle file selection
fileInput.addEventListener('change', function(e) {
    const file = e.target.files[0];
    if (file) {
        displayFileName(file.name, file.size);
    }
});



// Handle drag and drop
uploadPlaceholder.addEventListener('dragover', (e) => {
    e.preventDefault();
    uploadPlaceholder.style.borderColor = '#764ba2';
    uploadPlaceholder.style.backgroundColor = '#f0f2ff';
});

uploadPlaceholder.addEventListener('dragleave', () => {
    uploadPlaceholder.style.borderColor = '#667eea';
    uploadPlaceholder.style.backgroundColor = '#f8f9ff';
});

uploadPlaceholder.addEventListener('drop', (e) => {
    e.preventDefault();
    uploadPlaceholder.style.borderColor = '#667eea';
    uploadPlaceholder.style.backgroundColor = '#f8f9ff';

    const files = e.dataTransfer.files;
    if (files.length > 0) {
        fileInput.files = files;
        displayFileName(files[0].name, files[0].size);
    }
});

function displayFileName(name, size) {
    const sizeMB = (size / (1024 * 1024)).toFixed(2);
    fileNameDisplay.innerHTML = `<i class="bi bi-check-circle"></i> ${name} (${sizeMB} MB)`;
    fileNameDisplay.classList.add('active');
}

// Handle toast notifications
document.addEventListener('DOMContentLoaded', function() {
    const successToast = document.getElementById('successToast');
    const errorToast = document.getElementById('errorToast');

    if (successToast) {
        showToast(successToast);
        setTimeout(() => {
            clearFormFields();
        }, 500);
    }

    if (errorToast) {
        showToast(errorToast);
    }

    function showToast(toastElement) {
        toastElement.style.display = 'flex';
        setTimeout(() => {
            toastElement.style.animation = 'slideOutRight 0.4s ease forwards';
            setTimeout(() => {
                toastElement.style.display = 'none';
            }, 400);
        }, 3000);
    }

    function clearFormFields() {
        document.getElementById('fileInput').value = '';
        document.getElementById('songName').value = '';
        document.getElementById('fileName').value = '';
        document.getElementById('movie').value = '';
        document.getElementById('singer').value = '';
        document.getElementById('language').value = '';
        document.getElementById('songType').value = '';
        document.getElementById('hero').value = '';
        document.getElementById('heroine').value = '';
        document.getElementById('fileNameDisplay').textContent = '';
        document.getElementById('fileNameDisplay').classList.remove('active');
    }
});

// Add slide out animation styles if not already present
const style = document.createElement('style');
style.textContent = `
    @keyframes slideOutRight {
        from {
            opacity: 1;
            transform: translateX(0);
        }
        to {
            opacity: 0;
            transform: translateX(400px);
        }
    }
`;
document.head.appendChild(style);