// Toast notification system
class ToastNotifier {
    constructor() {
        this.container = document.getElementById('toastContainer');
    }

    show(message, type = 'error', duration = 5000) {
        const toast = document.createElement('div');
        toast.className = `toast-custom toast-${type}`;

        const icon = type === 'success' ? 'bi-check-circle-fill' : 'bi-exclamation-circle-fill';

        toast.innerHTML = `
            <span class="toast-icon">
                <i class="bi ${icon}"></i>
            </span>
            <div class="toast-content">${message}</div>
            <button type="button" class="toast-close" aria-label="Close">×</button>
        `;

        this.container.appendChild(toast);

        toast.querySelector('.toast-close').addEventListener('click', () => {
            this.removeToast(toast);
        });

        if (duration > 0) {
            setTimeout(() => {
                this.removeToast(toast);
            }, duration);
        }

        return toast;
    }

    removeToast(toast) {
        toast.classList.add('removing');
        setTimeout(() => {
            toast.remove();
        }, 300);
    }

    success(message, duration = 4000) {
        return this.show(message, 'success', duration);
    }

    error(message, duration = 5000) {
        return this.show(message, 'error', duration);
    }
}

// Initialize notifier
const notifier = new ToastNotifier();

// Modal functions
function openCreateModal() {
    document.getElementById('createModal').classList.add('active');
}

function closeCreateModal() {
    document.getElementById('createModal').classList.remove('active');
    document.getElementById('createRoomForm').reset();
}

function openJoinModal() {
    document.getElementById('joinModal').classList.add('active');
}

function closeJoinModal() {
    document.getElementById('joinModal').classList.remove('active');
    document.getElementById('joinRoomForm').reset();
}

function logout() {
    // Remove JWT token if stored
    localStorage.removeItem('jwtToken');
    localStorage.removeItem('username');
    localStorage.removeItem('currentRoom');
    document.cookie = 'jwtToken=; path=/; max-age=0; SameSite=Lax';
    window.location.href = '/app/music/public/login';
}

// Close modal when clicking outside
window.onclick = function(event) {
    const createModal = document.getElementById('createModal');
    const joinModal = document.getElementById('joinModal');

    if (event.target === createModal) {
        closeCreateModal();
    }
    if (event.target === joinModal) {
        closeJoinModal();
    }
}

// Handle error and success messages on page load
window.addEventListener('DOMContentLoaded', function() {
    let hasErrors = false;

    // Handle field validation errors
    const fieldErrorsContainer = document.getElementById('fieldErrors');
    const errorItems = fieldErrorsContainer.querySelectorAll('.error-item');
    errorItems.forEach(item => {
        const errorMessage = item.textContent.trim();
        if (errorMessage) {
            notifier.error(errorMessage);
            hasErrors = true;
        }
    });

    // Handle creation error
    const creationErrorContainer = document.getElementById('creationError');
    const creationError = creationErrorContainer.textContent.trim();
    if (creationError) {
        notifier.error(creationError);
        openCreateModal();
        hasErrors = true;
    }

    // Handle join error
    const joinErrorContainer = document.getElementById('joinError');
    const joinError = joinErrorContainer.textContent.trim();
    if (joinError) {
        notifier.error(joinError);
        openJoinModal();
        hasErrors = true;
    }

    // Clear fields ONLY if there are actual errors
    if (hasErrors) {
        document.getElementById('roomName').value = '';
        document.getElementById('maxCount').value = '';
        document.getElementById('joinRoomName').value = '';
    }

});